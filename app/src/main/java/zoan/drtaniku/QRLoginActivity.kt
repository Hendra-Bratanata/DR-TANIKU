package zoan.drtaniku

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import zoan.drtaniku.network.ApiService
import zoan.drtaniku.repository.DeviceRepository
import zoan.drtaniku.utils.SessionManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import androidx.camera.core.ExperimentalGetImage

/**
 * QRLoginActivity - Handles QR code scanning for device authentication
 *
 * This activity manages:
 * - Camera initialization and QR code scanning using CameraX and ML Kit
 * - Device validation via REST API to zoan.online
 * - Session creation and persistence after successful authentication
 * - User feedback through status messages and toasts
 *
 * Architecture: MVVM pattern with Repository for data management
 * Dependencies: CameraX for camera, ML Kit for QR detection, Retrofit for API calls
 * Flow: Camera → QR Scan → API Validation → Session Save → HomeActivity
 */
@OptIn(ExperimentalGetImage::class)
class QRLoginActivity : AppCompatActivity() {

    // Camera Components
    private lateinit var cameraExecutor: ExecutorService      // Background thread for camera operations
    private lateinit var previewView: androidx.camera.view.PreviewView  // Camera preview display
    private lateinit var statusText: TextView                 // Status message display

    // Camera State Management
    private var cameraProvider: ProcessCameraProvider? = null  // Camera provider from CameraX
    private var camera: Camera? = null                        // Camera instance
    private var isFlashOn = false                            // Flashlight state

    // API Communication Layer
    private lateinit var deviceRepository: DeviceRepository    // Repository for device API calls

    // Operation Control Flags (Prevents duplicate operations)
    private var isProcessing = false                         // Flag to prevent multiple QR processing
    private var lastToastShown = 0L                         // Timestamp for toast cooldown
    private val TOAST_COOLDOWN = 3000L                      // 3 seconds cooldown between toasts

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val API_KEY = "50bfbf93-76db-4cc9-9cc9-eaeb6d5a88b4"
        private const val BASE_URL = "http://zoan.online/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_login)

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize API
        initializeApi()

        initViews()

        // Check camera permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun initializeApi() {
        // Setup OkHttpClient
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Setup Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        deviceRepository = DeviceRepository(apiService)
    }

    private fun initViews() {
        previewView = findViewById(R.id.camera_preview)
        statusText = findViewById(R.id.status_text)

        // Setup flash button
        val flashButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.flash_button)
        flashButton.setOnClickListener {
            toggleFlash()
        }
    }

    private fun toggleFlash() {
        camera?.cameraControl?.enableTorch(!isFlashOn)
        isFlashOn = !isFlashOn

        val flashButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.flash_button)
        flashButton.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (isFlashOn) R.color.tech_light_blue else R.color.primary_dark_green
        )
    }

    private fun showToast(message: String, length: Int = Toast.LENGTH_LONG) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastShown > TOAST_COOLDOWN) {
            Toast.makeText(this, message, length).show()
            lastToastShown = currentTime
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Image analysis use case for QR code scanning
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                        runOnUiThread {
                            handleQRCodeResult(qrCode)
                        }
                    })
                }

            // Select back camera as default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

                statusText.text = "Arahkan QR Code ke kamera"

            } catch (exc: Exception) {
                statusText.text = "Gagal memulai kamera"
                showToast("Kamera gagal dimulai: ${exc.message}", Toast.LENGTH_SHORT)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Handles QR code detection result from camera
     *
     * Process Flow:
     * 1. Check if already processing (prevent duplicate scans)
     * 2. Update UI status to show processing
     * 3. Validate QR code content (not empty)
     * 4. Set processing flag to prevent multiple API calls
     * 5. Send to API validation
     *
     * Called by: QRCodeAnalyzer when QR code is detected
     * Input: QR code string (IMEI or device identifier)
     */
    private fun handleQRCodeResult(qrCode: String) {
        // Prevent multiple processing of same QR code
        if (isProcessing) {
            return
        }

        statusText.text = "Memproses QR code..."

        // Log QR code for debugging purposes
        android.util.Log.d("QR_SCAN", "QR Code detected: $qrCode")

        // Validate QR code content (must not be empty)
        if (qrCode.isBlank()) {
            statusText.text = "QR Code kosong, coba lagi"
            return
        }

        // Set processing flag to prevent duplicate API calls
        isProcessing = true

        // Validate device with API
        checkDeviceWithAPI(qrCode.trim())
    }

    /**
     * Validates QR code device ID with remote API
     *
     * Process:
     * 1. Update UI status to "Memvalidasi device..."
     * 2. Launch coroutine for API call
     * 3. Call deviceRepository.isDeviceRegistered()
     * 4. Handle success/failure results
     * 5. Navigate accordingly based on validation result
     *
     * API Endpoint: http://zoan.online/api/id?api_key=[API_KEY]
     * Success: Device registered → Save session → Go to HomeActivity
     * Failure: Device not registered → Show error → Reset scanning
     *
     * @param deviceId QR code content (typically IMEI number)
     */
    private fun checkDeviceWithAPI(deviceId: String) {
        statusText.text = "Memvalidasi device..."

        lifecycleScope.launch {
            try {
                val result = deviceRepository.isDeviceRegistered(deviceId)
                result.fold(
                    onSuccess = { isRegistered ->
                        if (isRegistered) {
                            // Device terdaftar
                            statusText.text = "✅ Sukses"
//                            showToast("✅ Alat Terdaftar! Login Berhasil", Toast.LENGTH_SHORT)

                            // Get device info
                            val deviceInfoResult = deviceRepository.getDeviceInfo(deviceId)
                            deviceInfoResult.fold(
                                onSuccess = { device ->
                                    // Save session
                                    SessionManager.saveLoginSession(this@QRLoginActivity, device)
                                    showLoginSuccess(device)
                                },
                                onFailure = { error ->
                                    // Even if device info fetch fails, save basic session
                                    val basicDevice = zoan.drtaniku.network.Device(
                                        IMEI = deviceId,
                                        Lokasi = "",
                                        Alamat = "",
                                        Status = "Registered"
                                    )
                                    SessionManager.saveLoginSession(this@QRLoginActivity, basicDevice)
                                    showLoginSuccess(basicDevice)
                                }
                            )
                        } else {
                            // Device tidak terdaftar
                            statusText.text = " ❌ Alat Belum Terdaftar"
//                            showToast("❌ Alat Belum Terdaftar", Toast.LENGTH_LONG)

                            // Reset setelah 3 detik
                            resetScanning()
                        }
                    },
                    onFailure = { error ->
                        statusText.text = "❌ Error koneksi"
                        showToast("❌ Gagal memvalidasi device: ${error.message}", Toast.LENGTH_LONG)

                        // Reset setelah 3 detik
                        resetScanning()
                    }
                )
            } catch (e: Exception) {
                statusText.text = "❌ Error sistem"
                showToast("❌ Error sistem: ${e.message}", Toast.LENGTH_LONG)

                // Reset setelah 3 detik
                resetScanning()
            }
        }
    }

    private fun resetScanning() {
        statusText.postDelayed({
            statusText.text = "Arahkan QR Code ke kamera"
            isProcessing = false // Reset processing flag
        }, 3000)
    }

    private fun showLoginSuccess(device: zoan.drtaniku.network.Device?) {
        statusText.text = "Sukses"

        android.util.Log.d("QRLoginActivity", "Login successful, navigating to home")

        // Pass device info to HomeActivity
        val intent = Intent(this, HomeActivity::class.java)
        device?.let {
            intent.putExtra("device_imei", it.IMEI)
            intent.putExtra("device_location", it.Lokasi)
            intent.putExtra("device_address", it.Alamat)
            intent.putExtra("device_status", it.Status)
        }

        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                statusText.text = "Izin kamera diperlukan"
                showToast("Izin kamera diperlukan untuk scan QR code", Toast.LENGTH_LONG)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Safe shutdown camera executor
        try {
            if (::cameraExecutor.isInitialized) {
                cameraExecutor.shutdown()
            }
        } catch (e: Exception) {
            // Ignore shutdown errors
        }

        // Unbind camera use cases
        cameraProvider?.unbindAll()
    }

    // Inner class for QR code analysis
    @OptIn(ExperimentalGetImage::class)
    private inner class QRCodeAnalyzer(
        private val onQRCodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(image: ImageProxy) {
            val mediaImage = image.image
            if (mediaImage != null) {
                val imageToProcess = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

                scanner.process(imageToProcess)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                onQRCodeDetected(value)
                                return@addOnSuccessListener
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        // Handle scanning errors
                    }
                    .addOnCompleteListener {
                        image.close()
                    }
            }
        }
    }
}