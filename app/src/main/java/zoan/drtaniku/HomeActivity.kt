package zoan.drtaniku

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions
import zoan.drtaniku.utils.SessionManager
import zoan.drtaniku.database.AnalysisDatabaseHelper
import zoan.drtaniku.model.SavedAnalysis
import zoan.drtaniku.adapter.RecentAnalysesAdapter
import java.text.SimpleDateFormat
import java.util.*

/**
 * HomeActivity - Main dashboard for sensor monitoring and device control
 *
 * This activity manages:
 * - Real-time sensor data display (Temperature, Humidity, pH, N, P, K)
 * - Environmental monitoring (GPS, Altitude, Light, Compass)
 * - USB device communication and Modbus protocol handling
 * - Navigation drawer with logout functionality
 * - Session validation and access control
 *
 * Architecture: MVP pattern with Repository and Service layers
 * Data Sources: USB Serial Device, Android Sensors, GPS
 * Refresh Rate: 5 seconds for sensor data
 * UI Pattern: Card-based responsive layout with real-time updates
 */
class HomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, EasyPermissions.PermissionCallbacks, SensorEventListener {

    // Demo Mode Flag
    private var isDemoMode = false                           // Flag to indicate demo mode status

    // Navigation Components
    private lateinit var drawerLayout: DrawerLayout          // Navigation drawer container
    private lateinit var navigationView: NavigationView       // Navigation menu view
    private lateinit var toolbar: Toolbar                     // App toolbar with hamburger menu

    // Background Operations
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob()) // Main thread coroutine scope

    // Sensor Data Display UI Elements
    private lateinit var textSuhuValue: TextView              // Temperature display
    private lateinit var textHumiValue: TextView              // Humidity display
    private lateinit var textPhValue: TextView               // pH level display
    private lateinit var textNValue: TextView                // Nitrogen display
    private lateinit var textPValue: TextView                // Phosphorus display
    private lateinit var textKValue: TextView                // Potassium display

    // Sensor Card Containers (for visual effects)
    private lateinit var cardSuhu: View                       // Temperature card
    private lateinit var cardHumi: View                       // Humidity card
    private lateinit var cardPh: View                        // pH card
    private lateinit var cardN: View                         // Nitrogen card
    private lateinit var cardP: View                         // Phosphorus card
    private lateinit var cardK: View                         // Potassium card

    // Environmental Sensor Display
    private lateinit var textGpsValue: TextView              // GPS coordinates display
    private lateinit var textAltitudeValue: TextView          // Altitude display
    private lateinit var textLuxValue: TextView              // Light level display
    private lateinit var textCompassValue: TextView           // Compass heading display
    private lateinit var compassArrow: ImageView              // Compass direction indicator

    // Environmental Card Containers
    private lateinit var cardGps: View                       // GPS card
    private lateinit var cardAltitude: View                  // Altitude card
    private lateinit var cardLux: View                       // Light sensor card
    private lateinit var cardCompass: View                    // Compass card

    // USB Communication Components
    private lateinit var usbManager: UsbManager              // Android USB system service
    private var usbSerialPort: UsbSerialPort? = null        // USB serial port connection
    private var serialIoManager: SerialInputOutputManager? = null  // Serial data manager
    private val serialListener = MainListener()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var refreshRunnable: Runnable
    private var isConnected = false
    private val readBuffer = ArrayList<Byte>()
    private val bufferLock = Any()
    private var isWaitingForResponse = false
    private var lastRequestTime: Long = 0
    private var lastTxResponseData: TxResponseData? = null

    // API Repository
    private lateinit var deviceRepository: zoan.drtaniku.repository.DeviceRepository

    // Sensor & Location
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    // Data tracking
    private var previousSuhu: Double = -1.0
    private var previousHumi: Double = -1.0
    private var previousPh: Double = -1.0
    private var previousN: Double = -1.0
    private var previousP: Double = -1.0
    private var previousK: Double = -1.0

    private var currentSensorData: SensorData? = null

    // Location Components
    private lateinit var geocodingManager: GeocodingManager
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentAltitude: Double? = null

    // Analysis Database
    private lateinit var analysisDatabaseHelper: AnalysisDatabaseHelper
    private lateinit var recentAnalysesAdapter: RecentAnalysesAdapter
    private lateinit var recyclerRecentAnalyses: RecyclerView
    private var currentLocationDetails: LocationDetails? = null
    private var lastApiRequestTime = 0L
    private val API_REQUEST_COOLDOWN = 30000L // 30 seconds cooldown

    // Toast cooldown to prevent duplicates
    private var lastToastShown = 0L
    private val TOAST_COOLDOWN = 3000L // 3 seconds cooldown

    // App state tracking
    private var isAppInForeground = false

    private var isSendButtonProcessing = false
    private val SEND_DEBOUNCE_DELAY_MS = 5000L // 5 detik untuk kirim data

    // UI components for progress indication
    private lateinit var progressOperation: ProgressBar
    private lateinit var textOperationStatus: TextView

    // Plant Analysis UI components
    private lateinit var editPlantName: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnAnalyzePlant: android.widget.Button
    private lateinit var cardAnalysisResult: androidx.cardview.widget.CardView
    private lateinit var textAnalysisResult: TextView
    private lateinit var btnSaveAnalysis: android.widget.Button
    private var isAnalyzeButtonProcessing = false
    private var currentAnalysisResult: String = ""
    private var analysisSensorData: SensorData? = null // Data sensor yang digunakan saat analisis
    private val ANALYZE_DEBOUNCE_DELAY_MS = 8000L // 8 detik untuk analisa tanaman

    // Webhook URL for plant analysis

    // Permissions
    private val usbPermissionIntent by lazy {
        PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { connectToDevice(it) }
                    } else {
                        showToast("Permission denied for device")
                    }
                }
            }
        }
    }

    private fun generateDemoSensorData() {
        // Generate random sensor values within specified ranges
        val temperature = 25 + kotlin.random.Random.nextDouble() * 20 // 25-45°C
        val humidity = 10 + kotlin.random.Random.nextDouble() * 90 // 10-100%
        val ph = 4 + kotlin.random.Random.nextDouble() * 5 // 4-9 pH
        val nitrogen = 50 + kotlin.random.Random.nextDouble() * 150 // 50-200
        val phosphorus = 80 + kotlin.random.Random.nextDouble() * 120 // 80-200
        val potassium = 50 + kotlin.random.Random.nextDouble() * 350 // 50-400

        val demoSensorData = SensorData(
            timestamp = getCurrentTimestamp(),
            suhu = temperature,
            humi = humidity,
            ph = ph,
            n = nitrogen,
            p = phosphorus,
            k = potassium
        )

        updateSensorDisplay(demoSensorData)
        Log.d(TAG, "🎭 Demo mode generated: T=${"%.1f".format(temperature)}°C, H=${"%.1f".format(humidity)}%, pH=${"%.1f".format(ph)}, N=${"%.0f".format(nitrogen)}, P=${"%.0f".format(phosphorus)}, K=${"%.0f".format(potassium)}")
    }

    companion object {
        private const val TAG = "HomeActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        private const val PERMISSIONS_REQUEST_CODE = 1
        private const val ACTION_USB_PERMISSION = "com.example.iovatel.USB_PERMISSION"
        private const val RESPONSE_TIMEOUT = 1000L
        private const val REFRESH_INTERVAL = 5000L // 5 seconds
        private const val PLANT_ANALYSIS_WEBHOOK_URL = "http://iotdashboard.online:5678/webhook/403011c6-75ae-46a6-9ee2-c28093e53a2b"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        // Check for demo mode
        isDemoMode = intent.getBooleanExtra("DEMO_MODE", false)

        // Initialize DeviceRepository
        initializeDeviceRepository()

        // Validate session - redirect to login if invalid
//        if (!SessionManager.isLoggedIn(this)) {
//            android.util.Log.d("HomeActivity", "Invalid session, redirecting to login")
//            val intent = Intent(this, QRLoginActivity::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//            startActivity(intent)
//            finish()
//            return
//        }

        initializeViews()
        setupNavigationDrawer()
        setupOnBackPressed()
        checkAndRequestPermissions()
        updateSensorDisplay(getZeroSensorData())
        setupAutoRefresh()
        initializeEnvironmentSensors()
        initializeGPS()
        initializeButtonsAndAnalysis()
        initializeLocationComponents()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()

        // Clean up USB connection without showing toast
        disconnectModbus(showToast = false)
    }

    private fun initializeViews() {
        // Toolbar
        toolbar = findViewById(R.id.toolbar)

        // Sensor Cards
        textSuhuValue = findViewById(R.id.text_suhu_value)
        textHumiValue = findViewById(R.id.text_humi_value)
        textPhValue = findViewById(R.id.text_ph_value)
        textNValue = findViewById(R.id.text_n_value)
        textPValue = findViewById(R.id.text_p_value)
        textKValue = findViewById(R.id.text_k_value)

        cardSuhu = findViewById(R.id.card_suhu)
        cardHumi = findViewById(R.id.card_humi)
        cardPh = findViewById(R.id.card_ph)
        cardN = findViewById(R.id.card_n)
        cardP = findViewById(R.id.card_p)
        cardK = findViewById(R.id.card_k)

        // Environment Cards
        textGpsValue = findViewById(R.id.text_gps_value)

        // Set initial GPS display to 0.0, 0.0
        textGpsValue.text = "0.0, 0.0"
        textAltitudeValue = findViewById(R.id.text_altitude_value)
        textLuxValue = findViewById(R.id.text_lux_value)
        textCompassValue = findViewById(R.id.text_compass_value)

        // Compass arrow
        compassArrow = findViewById(R.id.compass_arrow)

        cardGps = findViewById(R.id.card_gps)
        cardAltitude = findViewById(R.id.card_altitude)
        cardLux = findViewById(R.id.card_lux)
        cardCompass = findViewById(R.id.card_compass)

        
        // Progress UI Elements
        progressOperation = findViewById<ProgressBar>(R.id.progress_operation)
        textOperationStatus = findViewById<TextView>(R.id.text_operation_status)

        // Plant Analysis UI Elements
        editPlantName = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_text_plant_name)
        btnAnalyzePlant = findViewById<android.widget.Button>(R.id.btn_analyze_plant)
        cardAnalysisResult = findViewById<androidx.cardview.widget.CardView>(R.id.card_analysis_result)
        textAnalysisResult = findViewById<TextView>(R.id.text_analysis_result)
        btnSaveAnalysis = findViewById<android.widget.Button>(R.id.btn_save_analysis)

        // Recent Analyses UI Elements
        recyclerRecentAnalyses = findViewById<RecyclerView>(R.id.recycler_recent_analyses)

        // Initialize managers
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        // Update navigation header with device info
        updateNavigationHeader()

        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)
    }

    /**
     * Initialize DeviceRepository for API calls
     */
    private fun initializeDeviceRepository() {
        try {
            // Setup OkHttpClient
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // Setup Retrofit with lenient JSON parsing
            val gson = com.google.gson.GsonBuilder()
                .setLenient()
                .create()

            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl("http://zoan.online/")
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(gson))
                .build()

            val apiService = retrofit.create(zoan.drtaniku.network.ApiService::class.java)
            deviceRepository = zoan.drtaniku.repository.DeviceRepository(apiService)

            Log.d("HomeActivity", "DeviceRepository initialized successfully")
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error initializing DeviceRepository", e)
            showToast("Error initializing API service")
        }
    }

    /**
     * Setup Plant Analysis functionality
     */
    private fun setupPlantAnalysis() {
        btnAnalyzePlant.setOnClickListener {
            onAnalyzePlantClick()
        }

        btnSaveAnalysis.setOnClickListener {
            onSaveAnalysisClick()
        }

        // Demo Mode Indicator
        val demoModeIndicator = findViewById<LinearLayout>(R.id.demo_mode_indicator)
        if (isDemoMode) {
            demoModeIndicator.visibility = View.VISIBLE
            Log.d(TAG, "🎭 Demo mode indicator shown")
        } else {
            demoModeIndicator.visibility = View.GONE
        }

        Log.d(TAG, "🔍 Plant analysis setup completed")
    }

    private fun getZeroSensorData(): SensorData {
        return SensorData(getCurrentTimestamp(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    private fun setupAutoRefresh() {
        refreshRunnable = Runnable {
            if (isAppInForeground) { // Only scan when app is active
                if (isDemoMode) {
                    // Generate random sensor data for demo mode
                    generateDemoSensorData()
                } else if (isConnected) {
                    performAutoModbusRead()
                } else {
                    scanForUsbDevices() // Try to reconnect if not connected
                }
            }
            handler.postDelayed(refreshRunnable, REFRESH_INTERVAL)
        }
    }

    private fun performAutoModbusRead() {
        synchronized(bufferLock) {
            if (!isConnected || isWaitingForResponse) return

            isWaitingForResponse = true
            lastRequestTime = System.currentTimeMillis()
            readBuffer.clear()

            activityScope.launch(Dispatchers.IO) {
                try {
                    val requestBytes = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x06, 0xC5.toByte(), 0xC8.toByte())
                    usbSerialPort?.write(requestBytes, 1000)
                    Log.i(TAG, "Request sent: ${requestBytes.joinToString(" ") { "%02X".format(it) }}")

                    // Timeout handler
                    delay(RESPONSE_TIMEOUT)
                    synchronized(bufferLock) {
                        if (isWaitingForResponse) {
                            isWaitingForResponse = false
                            Log.w(TAG, "Response timeout")
                            runOnUiThread { showToast("Sensor timeout") }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Modbus read error", e)
                    synchronized(bufferLock) { isWaitingForResponse = false }
                }
            }
        }
    }

    private fun updateSensorDisplay(data: SensorData) {
        // Store current sensor data for saving
        currentSensorData = data

        runOnUiThread {
            textSuhuValue.text = String.format(Locale.getDefault(), "%.1f°C", data.suhu)
            updateCardStatus(cardSuhu, data.suhu, 15.0, 35.0)
            if (data.suhu != previousSuhu) {
                blinkCard(cardSuhu as CardView)
                previousSuhu = data.suhu
            }

            textHumiValue.text = String.format(Locale.getDefault(), "%.1f%%", data.humi)
            updateCardStatus(cardHumi, data.humi, 30.0, 70.0)
            if (data.humi != previousHumi) {
                blinkCard(cardHumi as CardView)
                previousHumi = data.humi
            }

            textPhValue.text = String.format(Locale.getDefault(), "%.2f", data.ph)
            updateCardStatus(cardPh, data.ph, 6.0, 7.5)
            if (data.ph != previousPh) {
                blinkCard(cardPh as CardView)
                previousPh = data.ph
            }

            data.n += data.n * 0.20
            textNValue.text = String.format(Locale.getDefault(), "%.0f", data.n)
            updateCardStatus(cardN, data.n, 50.0, 150.0)
            if (data.n != previousN) {
                blinkCard(cardN as CardView)
                previousN = data.n
            }

            data.p += data.p * 0.20
            textPValue.text = String.format(Locale.getDefault(), "%.0f", data.p)
            updateCardStatus(cardP, data.p, 20.0, 50.0)
            if (data.p != previousP) {
                blinkCard(cardP as CardView)
                previousP = data.p
            }

            data.k += data.k * 0.20
            textKValue.text = String.format(Locale.getDefault(), "%.0f", data.k)
            updateCardStatus(cardK, data.k, 20.0, 80.0)
            if (data.k != previousK) {
                blinkCard(cardK as CardView)
                previousK = data.k
            }
        }
    }

    private fun updateCardStatus(cardView: View, value: Double, minNormal: Double, maxNormal: Double) {
        cardView.alpha = if (value < minNormal || value > maxNormal) 0.7f else 1.0f
    }

    private fun blinkCard(cardView: CardView) {
        cardView.clearAnimation()
        val blinkOut = AlphaAnimation(1.0f, 0.3f).apply {
            duration = 150
            fillAfter = true
        }

        val blinkIn = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 150
            fillAfter = true
        }

        blinkOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                cardView.startAnimation(blinkIn)
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })

        cardView.startAnimation(blinkOut)
    }

    private fun initializeButtonsAndAnalysis() {
        // Setup send data button with debouncing
        
        // Setup plant analysis functionality
        setupPlantAnalysis()
    }

    private fun initializeLocationComponents() {
        geocodingManager = GeocodingManager(this)

        // Initialize analysis database
        analysisDatabaseHelper = AnalysisDatabaseHelper(this)

        // Setup recent analyses RecyclerView
        recentAnalysesAdapter = RecentAnalysesAdapter(
            analyses = emptyList(),
            onViewClick = { analysis ->
                navigateToAnalysisDetail(analysis)
            }
        )

        recyclerRecentAnalyses.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = recentAnalysesAdapter
        }

        // Load recent analyses
        loadRecentAnalyses()

        // Setup GPS card click listener
        cardGps.setOnClickListener {
            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                openLocationDetails()
            } else {
                showToast("GPS data belum tersedia")
            }
        }
    }

    /**
     * Load recent analyses and update RecyclerView
     */
    private fun loadRecentAnalyses() {
        activityScope.launch {
            try {
                val recentAnalyses = withContext(Dispatchers.IO) {
                    analysisDatabaseHelper.getAllAnalyses().take(10)
                }
                recentAnalysesAdapter.updateAnalyses(recentAnalyses)
                Log.d(TAG, "📊 Loaded ${recentAnalyses.size} recent analyses")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recent analyses", e)
            }
        }
    }

    /**
     * Navigate to analysis detail view or SavedAnalysesActivity
     */
    private fun navigateToAnalysisDetail(analysis: SavedAnalysis) {
        // Show analysis detail dialog directly
        showAnalysisDetailDialog(analysis)
    }

    private fun showAnalysisDetailDialog(analysis: SavedAnalysis) {
        val detailMessage = buildString {
            append("🌱 **Tanaman:** ${analysis.plantName}\n\n")
            append("📅 **Waktu:** ${analysis.getFormattedTimestamp()}\n\n")
            if (analysis.location.isNotEmpty()) {
                append("📍 **Lokasi:** ${analysis.location}\n\n")
            }
            append("📊 **Parameter Sensor:**\n")
            append("• 🌡️ Suhu: ${"%.1f".format(analysis.temperature)}°C\n")
            append("• 💧 Kelembaban: ${"%.1f".format(analysis.humidity)}%\n")
            append("• ⚗️ pH: ${"%.1f".format(analysis.ph)}\n")
            append("• 🧪 Nitrogen: ${"%.1f".format(analysis.nitrogen)}\n")
            append("• 🧪 Fosfor: ${"%.1f".format(analysis.phosphorus)}\n")
            append("• 🧪 Kalium: ${"%.1f".format(analysis.potassium)}\n\n")
            append("📋 **Hasil Analisa:**\n")
            append("─────────────────────────────────\n\n")
            append(analysis.analysisResult)
        }

        AlertDialog.Builder(this)
            .setTitle("🔍 Detail Analisa Tanaman")
            .setMessage(detailMessage)
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Bagikan") { _, _ ->
                shareAnalysisResult(analysis)
            }
            .show()
    }

    private fun shareAnalysisResult(analysis: SavedAnalysis) {
        val shareText = buildString {
            append("🌱 HASIL ANALISA TANAMAN - DR.TANIKU 🌱\n\n")
            append("Tanaman: ${analysis.plantName}\n")
            append("Waktu: ${analysis.getFormattedTimestamp()}\n")
            if (analysis.location.isNotEmpty()) {
                append("Lokasi: ${analysis.location}\n")
            }
            append("\n📊 Parameter Sensor:\n")
            append("• Suhu: ${"%.1f".format(analysis.temperature)}°C\n")
            append("• Kelembaban: ${"%.1f".format(analysis.humidity)}%\n")
            append("• pH: ${"%.1f".format(analysis.ph)}\n")
            append("• Nitrogen: ${"%.1f".format(analysis.nitrogen)}\n")
            append("• Fosfor: ${"%.1f".format(analysis.phosphorus)}\n")
            append("• Kalium: ${"%.1f".format(analysis.potassium)}\n\n")
            append("📋 Hasil Analisa:\n")
            append(analysis.analysisResult)
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Hasil Analisa Tanaman - ${analysis.plantName}")
        }

        try {
            startActivity(Intent.createChooser(shareIntent, "Bagikan hasil analisa"))
        } catch (e: Exception) {
            showToast("❌ Tidak dapat membagikan hasil analisa")
        }
    }

    private fun openLocationDetails() {
        val intent = LocationDetailsActivity.createIntent(
            this,
            latitude = currentLatitude,
            longitude = currentLongitude,
            altitude = currentAltitude
        )
        startActivity(intent)
    }

    
    /**
     * Send current sensor data to server
     */
    private fun sendDataToServer() {
        Log.d("HomeActivity", "==================================================")
        Log.d("HomeActivity", "🚀 STARTING SEND DATA PROCESS")
        Log.d("HomeActivity", "==================================================")

        // Get device IMEI from session
        val deviceInfo = SessionManager.getDeviceInfo(this)
        if (deviceInfo == null) {
            Log.e("HomeActivity", "❌ Device info not found in session")
            showToast("❌ Device tidak terdaftar. Silakan login kembali.")
            return
        }
        Log.d("HomeActivity", "✅ Device info retrieved: IMEI=${deviceInfo.IMEI}")

        // Get current sensor data
        val sensorData = currentSensorData ?: getZeroSensorData()
        Log.d("HomeActivity", "📊 Current sensor data:")
        Log.d("HomeActivity", "   - N (Nitrogen): ${sensorData.n}")
        Log.d("HomeActivity", "   - P (Phosphorus): ${sensorData.p}")
        Log.d("HomeActivity", "   - K (Potassium): ${sensorData.k}")
        Log.d("HomeActivity", "   - pH: ${sensorData.ph}")
        Log.d("HomeActivity", "   - Temperature: ${sensorData.suhu}°C")
        Log.d("HomeActivity", "   - Humidity: ${sensorData.humi}%")

        // Validate GPS coordinates
        if (currentLatitude == 0.0 || currentLongitude == 0.0) {
            Log.e("HomeActivity", "❌ GPS validation failed: Lat=$currentLatitude, Lng=$currentLongitude")
            showToast("⚠️ GPS data belum tersedia. Mohon tunggu hingga GPS mendapatkan lokasi.")
            return
        }
        Log.d("HomeActivity", "✅ GPS validation passed: Lat=$currentLatitude, Lng=$currentLongitude")

        // Create Google Maps URL
        val mapsUrl = "https://maps.google.com/?q=$currentLatitude,$currentLongitude"
        Log.d("HomeActivity", "🗺️ Maps URL: $mapsUrl")

        // Show loading indicator
        Log.d("HomeActivity", "🔄 Setting UI to loading state")

        // Send data to server using coroutine
        Log.d("HomeActivity", "🌐 Starting API call on IO thread")
        activityScope.launch(Dispatchers.IO) {
            try {
                Log.d("HomeActivity", "⏳ Calling DeviceRepository.sendSensorData()...")
                Log.d("HomeActivity", "📤 API Parameters summary:")
                Log.d("HomeActivity", "   IMEI: ${deviceInfo.IMEI}")
                Log.d("HomeActivity", "   N: ${sensorData.n}")
                Log.d("HomeActivity", "   P: ${sensorData.p}")
                Log.d("HomeActivity", "   K: ${sensorData.k}")
                Log.d("HomeActivity", "   pH: ${sensorData.ph}")
                Log.d("HomeActivity", "   Suhu: ${sensorData.suhu}")
                Log.d("HomeActivity", "   Humidity: ${sensorData.humi}")
                Log.d("HomeActivity", "   Maps: $mapsUrl")
                Log.d("HomeActivity", "   Lat: $currentLatitude, Lng: $currentLongitude")

                val result = deviceRepository.sendSensorData(
                    imei = deviceInfo.IMEI,
                    nitrogen = sensorData.n,
                    phosphorus = sensorData.p,
                    potassium = sensorData.k,
                    ph = sensorData.ph,
                    temperature = sensorData.suhu,
                    humidity = sensorData.humi,
                    mapsUrl = mapsUrl,
                    latitude = currentLatitude,
                    longitude = currentLongitude
                )

                Log.d("HomeActivity", "✅ API call completed, processing result...")
                runOnUiThread {
                    Log.d("HomeActivity", "🔄 Switching to UI thread for result processing")

                    result.fold(
                        onSuccess = { response: Any ->
                            Log.d("HomeActivity", "🎉 API CALL SUCCESS!")
                            Log.d("HomeActivity", "📋 Response type: ${response::class.java.simpleName}")
                            Log.d("HomeActivity", "📋 Response content: $response")

                            // Handle different response types
                            when (response) {
                                is zoan.drtaniku.network.AddDataResponse -> {
                                    Log.d("HomeActivity", "📊 Processing AddDataResponse:")
                                    Log.d("HomeActivity", "   Success: ${response.success}")
                                    Log.d("HomeActivity", "   Message: ${response.message}")
                                    Log.d("HomeActivity", "   Data ID: ${response.data_id}")

                                    if (response.success) {
                                        Log.d("HomeActivity", "✅ SUCCESS: Data berhasil dikirim ke server!")
                                        showToast("✅ Data berhasil dikirim ke server!")
                                        Log.d("HomeActivity", "📤 Toast message shown: 'Data berhasil dikirim ke server!'")
                                    } else {
                                        Log.e("HomeActivity", "❌ ERROR: Server returned error")
                                        Log.e("HomeActivity", "   Error message: ${response.message}")
                                        showToast("❌ Gagal mengirim data: ${response.message}")
                                        Log.d("HomeActivity", "📤 Toast message shown: 'Gagal mengirim data: ${response.message}'")
                                    }
                                }
                                is String -> {
                                    Log.d("HomeActivity", "📄 Processing String response:")
                                    Log.d("HomeActivity", "   Response: '$response'")
                                    Log.d("HomeActivity", "   Length: ${response.length}")

                                    // Handle string response (fallback case)
                                    val isSuccess = response.contains("berhasil") ||
                                                   response.contains("success") ||
                                                   response.contains("disimpan") ||
                                                   !response.contains("error")
                                    Log.d("HomeActivity", "🔍 Fallback success detection: $isSuccess")

                                    if (isSuccess) {
                                        Log.d("HomeActivity", "✅ SUCCESS: Fallback detection succeeded")
                                        showToast("✅ Data berhasil dikirim ke server!")
                                        Log.d("HomeActivity", "📤 Toast message shown: 'Data berhasil dikirim ke server!'")
                                        Log.d("HomeActivity", "📋 Server response logged: '$response'")
                                    } else {
                                        Log.e("HomeActivity", "❌ ERROR: Fallback detection failed")
                                        Log.e("HomeActivity", "   Response indicates error: '$response'")
                                        showToast("❌ Gagal mengirim data")
                                        Log.d("HomeActivity", "📤 Toast message shown: 'Gagal mengirim data'")
                                    }
                                }
                                else -> {
                                    Log.w("HomeActivity", "⚠️ UNKNOWN RESPONSE TYPE")
                                    Log.w("HomeActivity", "   Type: ${response::class.java.simpleName}")
                                    Log.w("HomeActivity", "   Value: $response")
                                    Log.w("HomeActivity", "   HashCode: ${response.hashCode()}")
                                    showToast("⚠️ Response tidak diketahui")
                                    Log.d("HomeActivity", "📤 Toast message shown: 'Response tidak diketahui'")
                                }
                            }
                        },
                        onFailure = { error: Throwable ->
                            Log.e("HomeActivity", "💥 API CALL FAILED!")
                            Log.e("HomeActivity", "❌ Error type: ${error::class.java.simpleName}")
                            Log.e("HomeActivity", "❌ Error message: ${error.message}")
                            Log.e("HomeActivity", "❌ Error cause: ${error.cause?.message ?: "No cause"}")
                            Log.e("HomeActivity", "❌ Error stack:")
                            Log.e("HomeActivity", error.stackTraceToString())

                            showToast("❌ Error koneksi: ${error.message}")
                            Log.d("HomeActivity", "📤 Toast message shown: 'Error koneksi: ${error.message}'")
                        }
                    )
                    Log.d("HomeActivity", "✅ Result processing completed")
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "💥 UNEXPECTED EXCEPTION IN SEND DATA!")
                Log.e("HomeActivity", "❌ Exception type: ${e::class.java.simpleName}")
                Log.e("HomeActivity", "❌ Exception message: ${e.message}")
                Log.e("HomeActivity", "❌ Exception cause: ${e.cause?.message ?: "No cause"}")
                Log.e("HomeActivity", "❌ Exception stack:")
                Log.e("HomeActivity", e.stackTraceToString())

                runOnUiThread {
                    Log.d("HomeActivity", "🔄 Handling exception on UI thread")

                    showToast("❌ Error: ${e.message}")
                    Log.d("HomeActivity", "📤 Toast message shown: 'Error: ${e.message}'")
                }
            }
            Log.d("HomeActivity", "==================================================")
            Log.d("HomeActivity", "🏁 SEND DATA PROCESS COMPLETED")
            Log.d("HomeActivity", "==================================================")
        }
    }

    
    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun checkAndRequestPermissions() {
        if (!hasPermissions()) {
            EasyPermissions.requestPermissions(this, "Required permissions for USB communication.", PERMISSIONS_REQUEST_CODE, *REQUIRED_PERMISSIONS)
        }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun scanForUsbDevices() {
        if (!isAppInForeground) return // Only scan when app is active
        val usbDevice = usbManager.deviceList.values.find { isUsbSerialDevice(it) }
        usbDevice?.let { connectToDevice(it) }
    }

    private fun connectToDevice(device: UsbDevice) {
        if (isConnected || !isAppInForeground) return // Only connect when app is active
        if (usbManager.hasPermission(device)) {
            activityScope.launch(Dispatchers.IO) {
                try {
                    val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).find { it.device.deviceId == device.deviceId } ?: return@launch
                    val port = driver.ports.getOrNull(0) ?: return@launch

                    val connection = usbManager.openDevice(driver.device)
                    port.open(connection)
                    port.setParameters(9600, 8, 1, UsbSerialPort.PARITY_NONE)

                    usbSerialPort = port
                    serialIoManager = SerialInputOutputManager(port, serialListener).apply { start() }

                    isConnected = true
                    runOnUiThread { showToast("Device connected") }

                } catch (e: Exception) {
                    Log.e(TAG, "Connection error", e)
                    disconnectModbus()
                }
            }
        } else {
            usbManager.requestPermission(device, usbPermissionIntent)
        }
    }

    private fun disconnectModbus() {
        disconnectModbus(showToast = true)
    }

    private fun disconnectModbus(showToast: Boolean) {
        // Only show toast if there was actually a device connected
        val wasConnected = isConnected || usbSerialPort != null || serialIoManager != null

        isConnected = false
        synchronized(bufferLock) { isWaitingForResponse = false }
        serialIoManager?.stop()
        serialIoManager = null
        try { usbSerialPort?.close() } catch (_: Exception) { /* Ignore */ }
        usbSerialPort = null

        // Only show toast if there was an actual device connected and toast is requested
        if (wasConnected && showToast) {
            runOnUiThread { showToast("Device disconnected") }
        }
    }

    private fun isUsbSerialDevice(device: UsbDevice): Boolean {
        val serialVendorIds = listOf(0x0403, 0x067B, 0x1A86, 0x10C4, 0x16D0)
        return serialVendorIds.contains(device.vendorId) || device.deviceClass == 2
    }

    private fun calculateCRC(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            for (i in 0 until 8) {
                crc = if (crc and 1 != 0) (crc shr 1) xor 0xA001 else crc shr 1
            }
        }
        return crc
    }

    private inner class MainListener : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            synchronized(bufferLock) {
                if (!isWaitingForResponse) return
                readBuffer.addAll(data.toList())
                handleReceivedData()
            }
        }

        override fun onRunError(e: Exception) {
            Log.e(TAG, "Serial IO Error", e)
            disconnectModbus()
        }
    }

    private fun handleReceivedData() {
        val responseBytes = readBuffer.toByteArray()
        if (responseBytes.size < 5) return

        val byteCount = responseBytes[2].toInt() and 0xFF
        val expectedLength = byteCount + 5
        if (responseBytes.size < expectedLength) return

        val completeResponse = responseBytes.copyOfRange(0, expectedLength)
        readBuffer.clear()
        isWaitingForResponse = false

        val calculatedCrc = calculateCRC(completeResponse.copyOfRange(0, expectedLength - 2))
        val receivedCrc = ((completeResponse[expectedLength - 1].toInt() and 0xFF) shl 8) or (completeResponse[expectedLength - 2].toInt() and 0xFF)

        if (calculatedCrc == receivedCrc) {
            Log.i(TAG, "Valid response: ${completeResponse.joinToString(" ") { "%02X".format(it) }}")

            if (byteCount >= 12) {
                val registers = (0 until 6).map { i -> val index = 3 + i * 2; ((completeResponse[index].toInt() and 0xFF) shl 8) or (completeResponse[index + 1].toInt() and 0xFF) }

                val sensorData = SensorData(getCurrentTimestamp(), registers[0] / 10.0, registers[1] / 10.0, registers[2] / 100.0, registers[3].toDouble(), registers[4].toDouble(), registers[5].toDouble())
                updateSensorDisplay(sensorData)

                lastTxResponseData = TxResponseData(getCurrentTimestamp(), 1, 3, 0, 6, "01 03 00 00 00 06 C5 C8", completeResponse.joinToString(" ") { "%02X".format(it) }, completeResponse, (System.currentTimeMillis() - lastRequestTime).toInt(), "Success")
            }
        } else {
            Log.w(TAG, "Invalid CRC. Calculated: $calculatedCrc, Received: $receivedCrc")
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
                        R.id.nav_saved_analyses -> {
                val intent = Intent(this, SavedAnalysesActivity::class.java)
                startActivity(intent)
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            R.id.nav_search_data -> {
                val intent = Intent(this, SearchDataActivity::class.java)
                startActivity(intent)
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            R.id.nav_location_details -> {
                val intent = Intent(this, LocationDetailsActivity::class.java)
                startActivity(intent)
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            R.id.nav_about -> {
                val intent = Intent(this, AboutActivity::class.java)
                startActivity(intent)
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            R.id.nav_logout -> {
                android.util.Log.d("HomeActivity", "Logout menu item clicked")

                // Force clear all session data
                SessionManager.logout(this)
                showToast("Logout successful")
                android.util.Log.d("HomeActivity", "Session force cleared, navigating to splash screen")

                // Navigate to SplashActivity to show splash screen and check session
                val intent = Intent(this, SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return true
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) { /* Granted */ }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) { /* Denied */ }

    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            textLuxValue.text = String.format(Locale.getDefault(), "%.0f Lux", lux)
        }

  
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values
        }

        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values
        }

        if (gravity != null && geomagnetic != null) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                textCompassValue.text = String.format(Locale.getDefault(), "%.0f°", (azimuth + 360) % 360)
                compassArrow.rotation = -azimuth
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Do nothing */ }

    private fun initializeEnvironmentSensors() {
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private fun initializeGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Request updates from both GPS and Network providers
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, locationListener)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5f, locationListener)

        // Get last known location for immediate display
        try {
            val lastLocationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastLocationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val bestLocation = when {
                lastLocationGPS != null && lastLocationNetwork != null -> {
                    if (lastLocationGPS.time > lastLocationNetwork.time) lastLocationGPS else lastLocationNetwork
                }
                lastLocationGPS != null -> lastLocationGPS
                lastLocationNetwork != null -> lastLocationNetwork
                else -> null
            }

            bestLocation?.let { location ->
                // Store initial coordinates
                currentLatitude = location.latitude
                currentLongitude = location.longitude

                textGpsValue.text = String.format(Locale.getDefault(), "%.5f, %.5f", location.latitude, location.longitude)

                if (location.hasAltitude()) {
                    currentAltitude = location.altitude
                    val altitude = location.altitude
                    textAltitudeValue.text = String.format(Locale.getDefault(), "%.1f m", altitude)
                    Log.d(TAG, "Initial altitude from last known location: $altitude m")
                } else {
                    currentAltitude = null
                }

                Log.d(TAG, "Initial GPS set: lat=$currentLatitude, lng=$currentLongitude")

                // Request location details when GPS coordinates are available
                if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                    requestLocationDetails(location)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
        }
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Store current coordinates
            currentLatitude = location.latitude
            currentLongitude = location.longitude

            textGpsValue.text = String.format(Locale.getDefault(), "%.5f, %.5f", location.latitude, location.longitude)

            // Update altitude from GPS only
            if (location.hasAltitude()) {
                currentAltitude = location.altitude
                val altitude = location.altitude
                textAltitudeValue.text = String.format(Locale.getDefault(), "%.1f m", altitude)
                Log.d(TAG, "Altitude from GPS: $altitude m")
            } else {
                currentAltitude = null
            }

            Log.d(TAG, "GPS updated: lat=$currentLatitude, lng=$currentLongitude")

            // Request location details when GPS coordinates are available
            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                requestLocationDetails(location)
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * Request location details from Nominatim API when GPS coordinates are available
     */
    private fun requestLocationDetails(location: Location) {
        // Check cooldown to prevent too frequent API requests
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastApiRequestTime < API_REQUEST_COOLDOWN) {
            Log.d(TAG, "API request cooldown active, skipping request")
            return
        }

        Log.d(TAG, "Requesting location details for: ${location.latitude}, ${location.longitude}")
        lastApiRequestTime = currentTime

        activityScope.launch(Dispatchers.IO) {
            try {
                val locationDetails = geocodingManager.getDetailedLocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else null
                )

                if (locationDetails != null) {
                    currentLocationDetails = locationDetails

                    runOnUiThread {
                        updateLocationDisplay(locationDetails)
                        Log.d(TAG, "Location details received: ${locationDetails.name}")
                    }
                } else {
                    Log.w(TAG, "Failed to get location details")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting location details", e)
                // Don't show toast for timeout errors as they're common
                if (!e.message?.contains("timeout", ignoreCase = true)!!) {
                    runOnUiThread {
                        showToast("Gagal mendapatkan detail lokasi")
                    }
                }
            }
        }
    }

    /**
     * Update UI with location details from API
     */
    private fun updateLocationDisplay(details: LocationDetails) {
        try {
            // Update GPS card to show coordinates + location name
            val locationName = details.getShortName()
            val locationInfo = if (locationName.isNotEmpty()) {
                locationName
            } else {
                "${textGpsValue.text}"
            }

            // Show a brief toast with location name
            showToast("📍 $locationInfo")

            Log.d(TAG, "Location display updated: $locationInfo")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating location display", e)
        }
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }

        handler.post(refreshRunnable)

        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }

        // Refresh recent saves when returning to home
            }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
        unregisterReceiver(usbReceiver)
        handler.removeCallbacks(refreshRunnable)
        sensorManager.unregisterListener(this)
        // Disconnect without showing toast (app going to background/other activity)
        disconnectModbus(showToast = false)
    }

    private fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastShown > TOAST_COOLDOWN) {
            Toast.makeText(this, message, length).show()
            lastToastShown = currentTime
        }
    }

    /**
     * Set button loading state with animation
     */
    private fun setButtonLoadingState(
        button: androidx.appcompat.widget.AppCompatButton,
        isLoading: Boolean,
        loadingText: String,
        originalText: String
    ) {
        if (isLoading) {
            // Disable button and show loading state
            button.isEnabled = false
            button.text = loadingText
            button.alpha = 0.7f

            // Add pulsing animation
            val pulseAnimation = AlphaAnimation(0.7f, 1.0f).apply {
                duration = 800
                repeatCount = Animation.INFINITE
                repeatMode = Animation.REVERSE
            }
            button.startAnimation(pulseAnimation)
        } else {
            // Re-enable button and restore original state
            button.isEnabled = true
            button.text = originalText
            button.alpha = 1.0f
            button.clearAnimation()
        }
    }

    /**
     * Set loading state for regular Button (for analyze button)
     */
    private fun setButtonLoadingState(
        button: android.widget.Button,
        isLoading: Boolean,
        loadingText: String,
        originalText: String
    ) {
        if (isLoading) {
            // Disable button and show loading state
            button.isEnabled = false
            button.text = loadingText
            button.alpha = 0.7f

            // Add pulsing animation
            val pulseAnimation = AlphaAnimation(0.7f, 1.0f).apply {
                duration = 800
                repeatCount = AlphaAnimation.INFINITE
                repeatMode = AlphaAnimation.REVERSE
            }
            button.startAnimation(pulseAnimation)
        } else {
            // Re-enable button and restore original state
            button.isEnabled = true
            button.text = originalText
            button.alpha = 1.0f

            // Stop any ongoing animation
            button.clearAnimation()
        }
    }

    /**
     * Show/hide progress bar with status text
     */
    private fun setOperationProgress(isLoading: Boolean, statusText: String = "") {
        progressOperation.visibility = if (isLoading) View.VISIBLE else View.GONE
        textOperationStatus.visibility = if (isLoading && statusText.isNotEmpty()) View.VISIBLE else View.GONE
        textOperationStatus.text = statusText
    }

    /**
     * Apply mutual exclusion - disable buttons when one is processing
     */
    private fun setButtonsMutualExclusion(
        analyzeButton: android.widget.Button,
        saveButton: android.widget.Button?,
        isProcessing: Boolean,
        activeButton: String = ""
    ) {
        if (isProcessing) {
            // Disable all buttons
            saveButton?.isEnabled = false
            analyzeButton.isEnabled = false

            // Set active button loading state
            when (activeButton) {
                "save" -> {
                    saveButton?.let { setButtonLoadingState(it, true, "💾 Menyimpan & Mengirim...", "💾 Simpan & Kirim Hasil Analisa") }
                    analyzeButton.alpha = 0.5f
                    analyzeButton.text = "⏳ Menunggu..."
                }
                "analyze" -> {
                    setButtonLoadingState(analyzeButton, true, "🔍 Menganalisa...", "🔍 Analisa Tanaman")
                    saveButton?.alpha = 0.5f
                    saveButton?.text = "⏳ Menunggu..."
                }
            }

            // Show progress
            setOperationProgress(
                true,
                when (activeButton) {
                    "save" -> "Sedang menyimpan & mengirim data..."
                    "analyze" -> "Sedang menganalisa tanaman..."
                    else -> "Memproses..."
                }
            )
        } else {
            // Re-enable all buttons and reset states
            saveButton?.isEnabled = true
            analyzeButton.isEnabled = true
            saveButton?.alpha = 1.0f
            analyzeButton.alpha = 1.0f
            saveButton?.let { setButtonLoadingState(it, false, "", "💾 Simpan & Kirim Hasil Analisa") }
            setButtonLoadingState(analyzeButton, false, "", "🔍 Analisa Tanaman")

            // Hide progress
            setOperationProgress(false)
        }
    }

        /**
     * Handle Plant Analysis button click
     */
    private fun onAnalyzePlantClick() {
        Log.d(TAG, "🔍 Plant analysis button clicked")

        // Validate plant name input
        val plantName = editPlantName.text.toString().trim()
        if (plantName.isEmpty()) {
            showToast("❌ Silakan masukkan nama tanaman")
            editPlantName.error = "Nama tanaman harus diisi"
            return
        }

        // Check if any button is processing (mutual exclusion)
        if (isAnalyzeButtonProcessing || isSendButtonProcessing) {
            showToast("⏳ Sedang ada proses lain yang berjalan, mohon tunggu...")
            return
        }

        // Get current sensor data
        val sensorData = currentSensorData ?: getZeroSensorData()

        // Store sensor data yang digunakan untuk analisis ini
        analysisSensorData = sensorData

        Log.d(TAG, "🌱 Starting plant analysis for: $plantName")
        Log.d(TAG, "📊 Sensor data - Suhu: ${sensorData.suhu}, Humi: ${sensorData.humi}, pH: ${sensorData.ph}")
        Log.d(TAG, "📊 Nutrient data - N: ${sensorData.n}, P: ${sensorData.p}, K: ${sensorData.k}")

        // Start analysis
        activityScope.launch {
            performPlantAnalysis(plantName, sensorData)
        }
    }

    /**
     * Perform plant analysis API call
     */
    private suspend fun performPlantAnalysis(plantName: String, sensorData: SensorData) {
        isAnalyzeButtonProcessing = true
        btnAnalyzePlant.isEnabled = false
        btnAnalyzePlant.text = "⏳ Menganalisa..."

        // Show result card with loading state
        cardAnalysisResult.visibility = android.view.View.VISIBLE
        textAnalysisResult.text = "🔄 Sedang menganalisa data tanaman ${plantName}...\n\nMohon tunggu sebentar."

        try {
            // Create Retrofit instance for plant analysis
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val gson = com.google.gson.GsonBuilder()
                .setLenient()
                .create()

            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl("https://bratanata.app.n8n.cloud/")
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(gson))
                .build()

            val apiService = retrofit.create(zoan.drtaniku.network.ApiService::class.java)

            Log.d(TAG, "🌐 Sending plant analysis request to webhook...")
            Log.d(TAG, "📤 URL: $PLANT_ANALYSIS_WEBHOOK_URL")
            Log.d(TAG, "📤 Params: suhu=${sensorData.suhu}, humi=${sensorData.humi}, ph=${sensorData.ph}, n=${sensorData.n}, p=${sensorData.p}, k=${sensorData.k}, tanaman=$plantName")

            // Make API call
            val response = apiService.analyzePlant(
                url = PLANT_ANALYSIS_WEBHOOK_URL,
                suhu = sensorData.suhu,
                humi = sensorData.humi,
                ph = sensorData.ph,
                n = sensorData.n,
                p = sensorData.p,
                k = sensorData.k,
                tanaman = plantName
            )

            if (response.isSuccessful && response.body() != null) {
                val analysisResults = response.body()!!
                Log.d(TAG, "✅ Plant analysis successful: ${analysisResults.size} results received")

                // Take the first result from the array
                val analysisResult = analysisResults.firstOrNull()
                if (analysisResult != null) {
                    Log.d(TAG, "✅ Analysis result: ${analysisResult.output}")
                    displayAnalysisResult(plantName, analysisResult.output, true)
                    showToast("✅ Analisa tanaman berhasil!")
                } else {
                    Log.w(TAG, "⚠️ Analysis successful but no results in array")
                    displayAnalysisResult(plantName, "Analisa berhasil tetapi tidak ada hasil.", false)
                    showToast("⚠️ Analisa berhasil tapi tidak ada hasil")
                }

            } else {
                val errorMsg = "Server error: ${response.code()} - ${response.message()}"
                Log.e(TAG, "❌ Plant analysis failed: $errorMsg")
                displayAnalysisResult(plantName, "Gagal mendapatkan hasil analisa.\n\n$errorMsg", false)
                showToast("❌ Gagal menganalisa tanaman")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Plant analysis error: ${e.message}", e)
            val errorMessage = "Terjadi kesalahan saat menganalisa tanaman:\n\n${e.message}"
            displayAnalysisResult(plantName, errorMessage, false)
            showToast("❌ Error: ${e.message}")

        } finally {
            // Reset button state
            isAnalyzeButtonProcessing = false
            btnAnalyzePlant.isEnabled = true
            btnAnalyzePlant.text = "🔍 Analisa Tanaman"

            // Apply debounce delay
            handler.postDelayed({
                // Button re-enabled automatically
            }, ANALYZE_DEBOUNCE_DELAY_MS)
        }
    }

    /**
     * Display analysis result in the result card
     */
    private fun displayAnalysisResult(plantName: String, result: String, isSuccess: Boolean) {
        cardAnalysisResult.visibility = android.view.View.VISIBLE

        val formattedResult = if (isSuccess) {
            "🌱 **Hasil Analisa Tanaman: $plantName**\n\n" +
            "─────────────────────────────────\n\n" +
            result.trim().replace("\n", "\n") +
            "\n\n─────────────────────────────────\n\n" +
            "💡 *Analisa berdasarkan data sensor saat ini*"
        } else {
            "❌ **Analisa Gagal: $plantName**\n\n" +
            "─────────────────────────────────\n\n" +
            result.trim() +
            "\n\n─────────────────────────────────\n\n" +
            "🔄 *Silakan coba lagi beberapa saat lagi*"
        }

        textAnalysisResult.text = formattedResult

        // Store the current analysis result for saving
        currentAnalysisResult = result

        // Show save button only for successful analysis
        btnSaveAnalysis.visibility = if (isSuccess) android.view.View.VISIBLE else android.view.View.GONE

        Log.d(TAG, "📋 Analysis result displayed for $plantName")
    }

    /**
     * Handle save analysis button click
     */
    private fun onSaveAnalysisClick() {
        val plantName = editPlantName.text.toString().trim()
        if (plantName.isEmpty()) {
            showToast("❌ Nama tanaman tidak ditemukan")
            return
        }

        if (currentAnalysisResult.isEmpty()) {
            showToast("❌ Tidak ada hasil analisa untuk disimpan")
            return
        }

        // Check if any button is processing
        if (isSendButtonProcessing || isAnalyzeButtonProcessing) {
            showToast("Mohon tunggu, sedang ada proses lain yang berjalan...")
            return
        }

        isSendButtonProcessing = true

        // Apply mutual exclusion - disable all buttons
        setButtonsMutualExclusion(btnAnalyzePlant, btnSaveAnalysis, true, "save")

        // Execute save and send operation
        activityScope.launch {
            try {
                // Get sensor data yang digunakan saat analisis (jika ada), gunakan current sensor data sebagai fallback
                val sensorData = analysisSensorData ?: currentSensorData ?: getZeroSensorData()

                Log.d(TAG, "💾 Starting save & send process for analysis:")
                Log.d(TAG, "   - Plant: $plantName")
                Log.d(TAG, "   - Suhu: ${sensorData.suhu}°C")
                Log.d(TAG, "   - Humi: ${sensorData.humi}%")
                Log.d(TAG, "   - pH: ${sensorData.ph}")
                Log.d(TAG, "   - N: ${sensorData.n}")
                Log.d(TAG, "   - P: ${sensorData.p}")
                Log.d(TAG, "   - K: ${sensorData.k}")

                // Step 1: Save to local database
                val locationString = if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                    "Lat: $currentLatitude, Lng: $currentLongitude"
                } else {
                    ""
                }

                val savedAnalysis = SavedAnalysis(
                    plantName = plantName,
                    analysisResult = currentAnalysisResult,
                    temperature = sensorData.suhu,
                    humidity = sensorData.humi,
                    ph = sensorData.ph,
                    nitrogen = sensorData.n,
                    phosphorus = sensorData.p,
                    potassium = sensorData.k,
                    location = locationString
                )

                // Save to database
                val resultId = analysisDatabaseHelper.insertAnalysis(savedAnalysis)
                if (resultId <= 0) {
                    throw Exception("Gagal menyimpan hasil analisa ke database lokal")
                }

                Log.d(TAG, "✅ Analysis saved locally with ID: $resultId")

                // Step 2: Send to server
                Log.d(TAG, "🌐 Sending data to server...")
                val serverResult = sendDataToServerInternal()

                // Wait for debounce delay
                delay(SEND_DEBOUNCE_DELAY_MS)

                runOnUiThread {
                    // Hide progress and show result
                    setButtonsMutualExclusion(btnAnalyzePlant, btnSaveAnalysis, false)
                    isSendButtonProcessing = false

                    // Show combined result
                    serverResult.fold(
                        onSuccess = { response ->
                            showToast("✅ Hasil analisa berhasil disimpan & dikirim ke server!")
                            Log.d(TAG, "🎉 Save & send process completed successfully")
                            // Refresh recent analyses
                            loadRecentAnalyses()
                        },
                        onFailure = { error ->
                            showToast("⚠️ Analisa tersimpan lokal, tapi gagal kirim ke server: ${error.message}")
                            Log.w(TAG, "⚠️ Local save successful, but server send failed: ${error.message}")
                            // Still refresh recent analyses since local save worked
                            loadRecentAnalyses()
                        }
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "💥 Save & send process failed", e)

                runOnUiThread {
                    // Hide progress
                    setButtonsMutualExclusion(btnAnalyzePlant, btnSaveAnalysis, false)
                    isSendButtonProcessing = false

                    showToast("❌ Gagal menyimpan hasil analisa: ${e.message}")
                }
            }
        }
    }

    /**
     * Internal send function without button state management
     */
    private suspend fun sendDataToServerInternal(): Result<zoan.drtaniku.network.AddDataResponse> {
        Log.d("HomeActivity", "==================================================")
        Log.d("HomeActivity", "🚀 STARTING SEND DATA PROCESS")
        Log.d("HomeActivity", "==================================================")

        // Get device IMEI from session
        val deviceInfo = SessionManager.getDeviceInfo(this)
        if (deviceInfo == null) {
            Log.e("HomeActivity", "❌ Device info not found in session")
            return Result.failure(Exception("Device tidak terdaftar. Silakan login kembali."))
        }

        Log.d("HomeActivity", "✅ Device info retrieved: IMEI=${deviceInfo.IMEI}")

        // Get current sensor data (use zero values if no data available)
        val sensorData = currentSensorData ?: getZeroSensorData()

        Log.d("HomeActivity", "📊 Current sensor data:")
        Log.d("HomeActivity", "   - N (Nitrogen): ${sensorData.n}")
        Log.d("HomeActivity", "   - P (Phosphorus): ${sensorData.p}")
        Log.d("HomeActivity", "   - K (Potassium): ${sensorData.k}")
        Log.d("HomeActivity", "   - pH: ${sensorData.ph}")
        Log.d("HomeActivity", "   - Temperature: ${sensorData.suhu}°C")
        Log.d("HomeActivity", "   - Humidity: ${sensorData.humi}%")

        // Validate GPS coordinates
        if (currentLatitude == 0.0 || currentLongitude == 0.0) {
            Log.w("HomeActivity", "⚠️ Invalid GPS coordinates - using default location")
        } else {
            Log.d("HomeActivity", "✅ GPS validation passed: Lat=$currentLatitude, Lng=$currentLongitude")
        }

        // Generate Google Maps URL
        val mapsUrl = "https://maps.google.com/?q=$currentLatitude,$currentLongitude"
        Log.d("HomeActivity", "🗺️ Maps URL: $mapsUrl")

        Log.d("HomeActivity", "🌐 Starting API call on IO thread")

        return withContext(Dispatchers.IO) {
            try {
                Log.d("HomeActivity", "⏳ Calling DeviceRepository.sendSensorData()...")
                Log.d("HomeActivity", "📤 API Parameters summary:")
                Log.d("HomeActivity", "   IMEI: ${deviceInfo.IMEI}")
                Log.d("HomeActivity", "   N: ${sensorData.n}")
                Log.d("HomeActivity", "   P: ${sensorData.p}")
                Log.d("HomeActivity", "   K: ${sensorData.k}")
                Log.d("HomeActivity", "   pH: ${sensorData.ph}")
                Log.d("HomeActivity", "   Suhu: ${sensorData.suhu}")
                Log.d("HomeActivity", "   Humidity: ${sensorData.humi}")
                Log.d("HomeActivity", "   Maps: $mapsUrl")
                Log.d("HomeActivity", "   Lat: $currentLatitude, Lng: $currentLongitude")
                Log.d("HomeActivity", "   Analisa: ${if (currentAnalysisResult.isNotEmpty()) "Available (${currentAnalysisResult.length} chars)" else "None"}")

                val result = deviceRepository.sendSensorData(
                    imei = deviceInfo.IMEI,
                    nitrogen = sensorData.n,
                    phosphorus = sensorData.p,
                    potassium = sensorData.k,
                    ph = sensorData.ph,
                    temperature = sensorData.suhu,
                    humidity = sensorData.humi,
                    mapsUrl = mapsUrl,
                    latitude = currentLatitude,
                    longitude = currentLongitude,
                    analisa = if (currentAnalysisResult.isNotEmpty()) currentAnalysisResult else null
                )

                Log.d("HomeActivity", "✅ API call completed, processing result...")
                Log.d("HomeActivity", "==================================================")
                Log.d("HomeActivity", "🏁 SEND DATA PROCESS COMPLETED")
                Log.d("HomeActivity", "==================================================")

                result
            } catch (e: Exception) {
                Log.e("HomeActivity", "💥 API CALL FAILED!")
                Log.e("HomeActivity", "❌ Error type: ${e::class.java.simpleName}")
                Log.e("HomeActivity", "❌ Error message: ${e.message}")
                Log.e("HomeActivity", "❌ Error cause: ${e.cause}")
                Log.e("HomeActivity", "❌ Error stack: ${e.stackTraceToString()}")
                Result.failure(e)
            }
        }
    }

    /**
     * Update navigation header with device ID and token information
     */
    private fun updateNavigationHeader() {
        val headerView = navigationView.getHeaderView(0)

        // Get TextViews from header
        val tvImeiVal = headerView.findViewById<android.widget.TextView>(R.id.tvImeiVal)
        val tvTokenVal = headerView.findViewById<android.widget.TextView>(R.id.tvTokenVal)

        if (isDemoMode) {
            // Demo mode: show hardcoded values
            tvImeiVal.text = "00000000"
            tvTokenVal.text = "100.000"
            Log.d(TAG, "🎭 Demo mode: Device ID = 00000000, Token = 100.000")
        } else {
            // Production mode: show actual device info
            val deviceInfo = SessionManager.getDeviceInfo(this)
            if (deviceInfo != null) {
                tvImeiVal.text = deviceInfo.IMEI
                // Format token with thousand separator or show "N/A"
                val token = deviceInfo.Token
                if (token != null && token.isNotBlank()) {
                    try {
                        val tokenNumber = token.toLong()
                        tvTokenVal.text = String.format("%,d", tokenNumber)
                    } catch (e: NumberFormatException) {
                        tvTokenVal.text = token
                    }
                } else {
                    tvTokenVal.text = "N/A"
                }
                Log.d(TAG, "📱 Device ID = ${deviceInfo.IMEI}, Token = ${deviceInfo.Token ?: "null"}")
            } else {
                // Fallback if no device info
                tvImeiVal.text = "N/A"
                tvTokenVal.text = "N/A"
                Log.w(TAG, "⚠️ No device info found in session")
            }
        }
    }
}
