package com.example.escanerbarras

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.escanerbarras.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isScanning = false

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val TAG = "BarcodeScanner"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnScan.setOnClickListener {
            if (hasCameraPermission()) {
                startCamera()
            } else {
                requestCameraPermission()
            }
        }

        binding.btnStop.setOnClickListener {
            stopCamera()
        }
    }

    // ─── Permisos ─────────────────────────────────────────────────────────────

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara denegado.", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── CameraX ──────────────────────────────────────────────────────────────

    private fun startCamera() {
        isScanning = true
        binding.btnScan.isEnabled = false
        binding.btnStop.isEnabled = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                    runOnUiThread { showResult(barcode) }
                })
            }

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar cámara: ${e.message}")
        }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        isScanning = false
        binding.btnScan.isEnabled = true
        binding.btnStop.isEnabled = false
    }

    // ─── Resultado ────────────────────────────────────────────────────────────

    private fun showResult(barcode: Barcode) {
        val value = barcode.rawValue ?: "Sin valor"
        val format = formatName(barcode.format)

        binding.tvResult.text = value
        binding.tvFormat.text = "Formato: $format  |  Tipo: ${typeName(barcode.valueType)}"

        // Detener escaneo automáticamente tras detectar
        stopCamera()
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE     -> "QR Code"
        Barcode.FORMAT_EAN_13      -> "EAN-13"
        Barcode.FORMAT_EAN_8       -> "EAN-8"
        Barcode.FORMAT_CODE_128    -> "Code 128"
        Barcode.FORMAT_CODE_39     -> "Code 39"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417      -> "PDF417"
        Barcode.FORMAT_AZTEC       -> "Aztec"
        Barcode.FORMAT_UPC_A       -> "UPC-A"
        Barcode.FORMAT_UPC_E       -> "UPC-E"
        else                       -> "Desconocido"
    }

    private fun typeName(type: Int): String = when (type) {
        Barcode.TYPE_URL          -> "URL"
        Barcode.TYPE_TEXT         -> "Texto"
        Barcode.TYPE_EMAIL        -> "Email"
        Barcode.TYPE_PHONE        -> "Teléfono"
        Barcode.TYPE_SMS          -> "SMS"
        Barcode.TYPE_WIFI         -> "WiFi"
        Barcode.TYPE_GEO          -> "Ubicación"
        Barcode.TYPE_CONTACT_INFO -> "Contacto"
        else                      -> "Otro"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}