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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
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
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions
import zoan.drtaniku.utils.SessionManager
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

    // Save Data Components
    private lateinit var dataSaveManager: DataSaveManager
    private lateinit var recentSavesAdapter: RecentSavesAdapter
    private lateinit var recyclerRecentSaves: RecyclerView
    private var currentSensorData: SensorData? = null

    // Toast cooldown to prevent duplicates
    private var lastToastShown = 0L
    private val TOAST_COOLDOWN = 3000L // 3 seconds cooldown

    // App state tracking
    private var isAppInForeground = false

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

    companion object {
        private const val TAG = "HomeActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        private const val PERMISSIONS_REQUEST_CODE = 1
        private const val ACTION_USB_PERMISSION = "com.example.iovatel.USB_PERMISSION"
        private const val RESPONSE_TIMEOUT = 1000L
        private const val REFRESH_INTERVAL = 5000L // 5 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        // Validate session - redirect to login if invalid
        if (!SessionManager.isLoggedIn(this)) {
            android.util.Log.d("HomeActivity", "Invalid session, redirecting to login")
            val intent = Intent(this, QRLoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        initializeViews()
        setupNavigationDrawer()
        setupOnBackPressed()
        checkAndRequestPermissions()
        updateSensorDisplay(getZeroSensorData())
        setupAutoRefresh()
        initializeEnvironmentSensors()
        initializeGPS()
        initializeSaveDataComponents()
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
        textAltitudeValue = findViewById(R.id.text_altitude_value)
        textLuxValue = findViewById(R.id.text_lux_value)
        textCompassValue = findViewById(R.id.text_compass_value)

        // Compass arrow
        compassArrow = findViewById(R.id.compass_arrow)

        cardGps = findViewById(R.id.card_gps)
        cardAltitude = findViewById(R.id.card_altitude)
        cardLux = findViewById(R.id.card_lux)
        cardCompass = findViewById(R.id.card_compass)

        // Save Data UI Elements
        recyclerRecentSaves = findViewById(R.id.recycler_recent_saves)

        // Initialize managers
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun getZeroSensorData(): SensorData {
        return SensorData(getCurrentTimestamp(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    private fun setupAutoRefresh() {
        refreshRunnable = Runnable {
            if (isAppInForeground) { // Only scan when app is active
                if (isConnected) {
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

    private fun initializeSaveDataComponents() {
        dataSaveManager = DataSaveManager(this)

        // Setup recent saves RecyclerView
        recentSavesAdapter = RecentSavesAdapter(
            context = this,
            saves = emptyList(),
            onViewClick = { savedData ->
                showSaveDataDialog(savedData)
            }
        )

        recyclerRecentSaves.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = recentSavesAdapter
        }

        // Setup save button
        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_save_data).setOnClickListener {
            saveCurrentData()
        }

        // View all saves functionality moved to navigation drawer

        // Load recent saves
        loadRecentSaves()
    }

    private fun saveCurrentData() {
        val sensorData = currentSensorData ?: getZeroSensorData()
        val gpsValue = textGpsValue.text.toString()
        val altitudeValue = textAltitudeValue.text.toString()
        val lightValue = textLuxValue.text.toString()
        val compassValue = textCompassValue.text.toString()

        activityScope.launch(Dispatchers.IO) {
            val savedData = dataSaveManager.saveData(
                sensorData = sensorData,
                gpsCoordinates = gpsValue,
                altitude = altitudeValue,
                lightLevel = lightValue,
                compass = compassValue
            )

            runOnUiThread {
                if (savedData != null) {
                    showToast("Data saved successfully!")
                    loadRecentSaves()
                } else {
                    showToast("Failed to save data")
                }
            }
        }
    }

    private fun loadRecentSaves() {
        val recentSaves = dataSaveManager.getRecentSaves()
        recentSavesAdapter.updateSaves(recentSaves)
    }

    private fun showSaveDataDialog(savedData: DataSaveManager.SavedDataInfo) {
        val dataContent = dataSaveManager.loadSavedData(savedData.filename)

        if (dataContent != null) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_data_view, null)
            val textDataContent = dialogView.findViewById<TextView>(R.id.text_data_content)

            textDataContent.text = dataContent
            textDataContent.movementMethod = android.text.method.ScrollingMovementMethod()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Data: ${savedData.timestamp}")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .setNegativeButton("Share") { _, _ ->
                    shareData(dataContent, savedData.filename)
                }
                .show()
        } else {
            showToast("Failed to load data")
        }
    }

    private fun shareData(dataContent: String, filename: String) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, dataContent)
                putExtra(Intent.EXTRA_SUBJECT, "DR Taniku Sensor Data - $filename")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Sensor Data"))
        } catch (e: Exception) {
            showToast("Failed to share data")
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
            R.id.nav_saved_data -> {
                val intent = Intent(this, SavedDataActivity::class.java)
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
                android.util.Log.d("HomeActivity", "Session force cleared, restarting app")

                // Restart app completely to clear any cached state
                val packageManager = packageManager
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
                finish()
                System.exit(0) // Force exit to clear all memory
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
                textGpsValue.text = String.format(Locale.getDefault(), "%.5f, %.5f", location.latitude, location.longitude)

                if (location.hasAltitude()) {
                    val altitude = location.altitude
                    textAltitudeValue.text = String.format(Locale.getDefault(), "%.1f m", altitude)
                    Log.d(TAG, "Initial altitude from last known location: $altitude m")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
        }
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            textGpsValue.text = String.format(Locale.getDefault(), "%.5f, %.5f", location.latitude, location.longitude)

            // Update altitude from GPS only
            if (location.hasAltitude()) {
                val altitude = location.altitude
                textAltitudeValue.text = String.format(Locale.getDefault(), "%.1f m", altitude)
                Log.d(TAG, "Altitude from GPS: $altitude m")
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
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
        loadRecentSaves()
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
}
