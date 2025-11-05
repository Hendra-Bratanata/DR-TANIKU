package zoan.drtaniku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * LocationDetailsActivity - Display detailed location information
 *
 * Features:
 * - Show comprehensive location details obtained from geocoding
 * - Display administrative boundaries for Indonesian locations
 * - Show GPS coordinates and altitude information
 * - Share location functionality
 * - Open location in maps
 * - Error handling and retry functionality
 */
class LocationDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LocationDetailsActivity"
        private const val EXTRA_LATITUDE = "extra_latitude"
        private const val EXTRA_LONGITUDE = "extra_longitude"
        private const val EXTRA_ALTITUDE = "extra_altitude"

        fun createIntent(
            context: android.content.Context,
            latitude: Double,
            longitude: Double,
            altitude: Double? = null
        ): Intent {
            return Intent(context, LocationDetailsActivity::class.java).apply {
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
                putExtra(EXTRA_ALTITUDE, altitude)
            }
        }
    }

    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutError: LinearLayout
    private lateinit var layoutContent: LinearLayout

    // Content Views
    private lateinit var textCoordinates: TextView
    private lateinit var textLatitude: TextView
    private lateinit var textLongitude: TextView
    private lateinit var textAltitude: TextView
    private lateinit var textLocationName: TextView
    private lateinit var textFullAddress: TextView
    private lateinit var textAdministrativeInfo: TextView
    private lateinit var textDataSource: TextView
    private lateinit var textQualityScore: TextView
    private lateinit var textTimestamp: TextView
    private lateinit var textErrorMessage: TextView

    // Buttons
    private lateinit var btnRetry: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnShareLocation: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnOpenMaps: androidx.appcompat.widget.AppCompatButton

    // Data
    private lateinit var geocodingManager: GeocodingManager
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var altitude: Double? = null
    private var locationDetails: LocationDetails? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_details)

        // Initialize managers
        geocodingManager = GeocodingManager(this)

        // Get intent data
        extractIntentData()

        // Initialize views
        initializeViews()
        setupToolbar()

        // Setup click listeners
        setupClickListeners()

        // Load location details
        loadLocationDetails()
    }

    private fun extractIntentData() {
        latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
        altitude = if (intent.hasExtra(EXTRA_ALTITUDE)) {
            intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0)
        } else null

        Log.d(TAG, "Received location: lat=$latitude, lng=$longitude, alt=$altitude")
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        layoutLoading = findViewById(R.id.layout_loading)
        layoutError = findViewById(R.id.layout_error)
        layoutContent = findViewById(R.id.layout_content)

        // Content views
        textCoordinates = findViewById(R.id.text_coordinates)
        textLatitude = findViewById(R.id.text_latitude)
        textLongitude = findViewById(R.id.text_longitude)
        textAltitude = findViewById(R.id.text_altitude)
        textLocationName = findViewById(R.id.text_location_name)
        textFullAddress = findViewById(R.id.text_full_address)
        textAdministrativeInfo = findViewById(R.id.text_administrative_info)
        textDataSource = findViewById(R.id.text_data_source)
        textQualityScore = findViewById(R.id.text_quality_score)
        textTimestamp = findViewById(R.id.text_timestamp)
        textErrorMessage = findViewById(R.id.text_error_message)

        // Buttons
        btnRetry = findViewById(R.id.btn_retry)
        btnShareLocation = findViewById(R.id.btn_share_location)
        btnOpenMaps = findViewById(R.id.btn_open_maps)

        // Set initial GPS coordinates display
        textCoordinates.text = String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
        textLatitude.text = String.format(Locale.getDefault(), "Lat: %.5f°", latitude)
        textLongitude.text = String.format(Locale.getDefault(), "Lng: %.5f°", longitude)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Detail Lokasi"
        }
    }

    private fun setupClickListeners() {
        btnRetry.setOnClickListener {
            loadLocationDetails()
        }

        btnShareLocation.setOnClickListener {
            shareLocation()
        }

        btnOpenMaps.setOnClickListener {
            openInMaps()
        }
    }

    private fun loadLocationDetails() {
        showLoadingState()

        lifecycleScope.launch {
            try {
                locationDetails = geocodingManager.getLocationDetails(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude
                )

                runOnUiThread {
                    hideLoadingState()
                    if (locationDetails != null) {
                        showLocationDetails(locationDetails!!)
                    } else {
                        showErrorState("Gagal mendapatkan detail lokasi")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading location details", e)
                runOnUiThread {
                    hideLoadingState()
                    showErrorState("Terjadi kesalahan: ${e.message}")
                }
            }
        }
    }

    private fun showLocationDetails(details: LocationDetails) {
        locationDetails = details

        // Update basic info
        textLocationName.text = details.getShortName()
        textFullAddress.text = details.getFormattedAddress()

        // Update altitude if available
        if (details.hasAltitude && details.altitude != null) {
            textAltitude.text = String.format(Locale.getDefault(), "Alt: %.1f m", details.altitude!!)
            textAltitude.visibility = View.VISIBLE
        } else {
            textAltitude.visibility = View.GONE
        }

        // Update administrative information
        val adminInfo = details.getAdministrativeHierarchy()
        if (adminInfo.isNotEmpty()) {
            textAdministrativeInfo.text = adminInfo
        } else {
            textAdministrativeInfo.text = "Informasi administratif tidak tersedia"
        }

        // Update additional information
        textDataSource.text = "Sumber Data: ${details.dataSource}"
        textQualityScore.text = "Kualitas Lokasi: ${details.getLocationQualityScore()}/100"

        // Format timestamp
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID"))
        textTimestamp.text = "Waktu: ${sdf.format(Date(details.timestamp))}"

        // Show content
        layoutContent.visibility = View.VISIBLE
        layoutError.visibility = View.GONE

        Log.d(TAG, "Location details displayed successfully")
    }

    private fun showLoadingState() {
        layoutLoading.visibility = View.VISIBLE
        layoutError.visibility = View.GONE
        layoutContent.visibility = View.GONE
    }

    private fun hideLoadingState() {
        layoutLoading.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        layoutError.visibility = View.VISIBLE
        layoutContent.visibility = View.GONE
        textErrorMessage.text = message

        Log.e(TAG, "Error state: $message")
    }

    private fun shareLocation() {
        val details = locationDetails ?: return

        try {
            val shareText = buildString {
                appendLine("📍 Lokasi GPS")
                appendLine("Koordinat: ${String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)}")
                if (details.hasAltitude && details.altitude != null) {
                    appendLine("Ketinggian: ${String.format(Locale.getDefault(), "%.1f m", details.altitude!!)}")
                }
                appendLine("Lokasi: ${details.getFormattedAddress()}")
                appendLine()
                appendLine("📱 Diperoleh dari DR Taniku")
                appendLine("Waktu: ${SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID")).format(Date())}")
            }

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Lokasi GPS - DR Taniku")
            }

            startActivity(Intent.createChooser(shareIntent, "Bagikan Lokasi"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing location", e)
            Toast.makeText(this, "Gagal membagikan lokasi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInMaps() {
        try {
            // Create Google Maps URI
            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }

            // Try Google Maps first
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                // Fallback to any maps app
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
                if (fallbackIntent.resolveActivity(packageManager) != null) {
                    startActivity(fallbackIntent)
                } else {
                    // Fallback to browser
                    val webUri = Uri.parse("https://www.google.com/maps?q=$latitude,$longitude")
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                    startActivity(webIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening maps", e)
            Toast.makeText(this, "Gagal membuka peta", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when activity resumes
        if (locationDetails == null && latitude != 0.0 && longitude != 0.0) {
            loadLocationDetails()
        }
    }
}