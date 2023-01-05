package com.c1ctech.barcodescannerexp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.IllegalStateException

import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

class MainActivity : AppCompatActivity() {

    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    lateinit var btnsflip : ImageButton
    lateinit var btnsflash : ImageButton



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionBar: ActionBar? = getSupportActionBar() //Get action bar reference
        if (actionBar != null) {
            actionBar.hide()
        }
        setContentView(R.layout.activity_main)

        this.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_FULLSCREEN
        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        
        btnsflip = findViewById(R.id.flipcamera)
        btnsflip.setOnClickListener {
            Log.e(TAG, "lenfacing:"+lensFacing)

             if(lensFacing.toString() == "0" ) {
                 lensFacing = CameraSelector.LENS_FACING_BACK
                 Log.e(TAG, "inif1:"+lensFacing)
                 setupCamera()
            } else if(lensFacing.toString() == "1"){
                 lensFacing = CameraSelector.LENS_FACING_FRONT
                 Log.e(TAG, "inif2:"+lensFacing)
                 setupCamera()
            }
        }
        setupCamera()



    }

    private fun setupCamera() {
        previewView = findViewById(R.id.preview_view)
        try {
            // Only bind use cases if we can query a camera with this orientation
            cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            bindCameraUseCases()
        } catch (exc: Exception) {
            // Do nothing
        }

        ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(CameraXViewModel::class.java)
            .processCameraProvider
            .observe(this) { provider: ProcessCameraProvider? ->
                cameraProvider = provider
                if (isCameraPermissionGranted()) {
                    bindCameraUseCases()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        PERMISSION_CAMERA_REQUEST
                    )
                }
            }
    }


    private fun bindCameraUseCases() {
        bindPreviewUseCase()
        bindAnalyseUseCase()
    }

    private fun bindPreviewUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder()
            .setTargetRotation(previewView!!.display.rotation)
            .build()
        previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)

        try {

            cameraProvider!!.bindToLifecycle(
                this,
                cameraSelector!!,
                previewUseCase
            )
        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }

    private fun bindAnalyseUseCase() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()

        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)

        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }

        analysisUseCase = ImageAnalysis.Builder()
            .setTargetRotation(previewView!!.display.rotation)
            .build()

        // Initialize our background executor
        val cameraExecutor = Executors.newSingleThreadExecutor()

        analysisUseCase?.setAnalyzer(
            cameraExecutor,
            ImageAnalysis.Analyzer { imageProxy ->
                processImageProxy(barcodeScanner, imageProxy)
            }
        )

        try {
            val camera = cameraProvider!!.bindToLifecycle(
                /* lifecycleOwner= */this,
                cameraSelector!!,
                analysisUseCase
            )
            var check = 0
            val cameraControl = camera.cameraControl
            btnsflash = findViewById(R.id.flashligh)
            btnsflash.setOnClickListener {
                if(check == 0){
                    cameraControl.enableTorch(true) // enable torch
                    check = 1
                }else{
                    cameraControl.enableTorch(false) // disbale torch
                    check = 0
                }

            }

        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(
        barcodeScanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val inputImage =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.forEach { barcode ->
                    val bounds = barcode.boundingBox
                    val corners = barcode.cornerPoints

                    val rawValue = barcode.rawValue

                    tvScannedData.text = rawValue

                    val valueType = barcode.valueType
                    //Toast.makeText(this,Barcode.TYPE_WIFI.toString(), Toast.LENGTH_LONG).show()

                    // See API reference for complete list of supported types
                    when (valueType) {
                        Barcode.TYPE_WIFI -> {
                            val ssid = barcode.wifi!!.ssid
                            val password = barcode.wifi!!.password
                            val type = barcode.wifi!!.encryptionType
                            tvScannedData.text = "ssid: " + ssid + "\npassword: " + password + "\ntype: " + type
                            Toast.makeText(this,"Sucess", Toast.LENGTH_LONG).show()
                        }
                        Barcode.TYPE_URL -> {
                            val title = barcode.url!!.title
                            val url = barcode.url!!.url

                            tvScannedData.text = "Title: " + title + "\nURL: " + url
                            Toast.makeText(this,"Sucess", Toast.LENGTH_LONG).show()

                        }
                    }
                   // Toast.makeText(this,rawValue, Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, it.message ?: it.toString())
            }
            .addOnCompleteListener {
                // When the image is from CameraX analysis use case, must call image.close() on received
                // images when finished using them. Otherwise, new images may not be received or the camera
                // may stall.

                imageProxy.close()

            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (isCameraPermissionGranted()) {
                bindCameraUseCases()
            } else {
                Log.e(TAG, "no camera permission")
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName

        private const val PERMISSION_CAMERA_REQUEST = 1
    }

}
