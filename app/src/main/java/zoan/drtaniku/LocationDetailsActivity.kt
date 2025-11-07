package zoan.drtaniku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
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

    // New administrative detail views
    private lateinit var textIndustrialArea: TextView
    private lateinit var textVillage: TextView
    private lateinit var textCounty: TextView
    private lateinit var textState: TextView
    private lateinit var textProvinceCode: TextView
    private lateinit var textRegion: TextView
    private lateinit var textRegionCode: TextView
    private lateinit var textCountry: TextView

    // Weather Views
    private lateinit var progressWeather: ProgressBar
    private lateinit var layoutWeatherContent: LinearLayout
    private lateinit var layoutWeatherError: LinearLayout
    private lateinit var imageWeatherIcon: ImageView
    private lateinit var textWeatherDesc: TextView
    private lateinit var textTemperature: TextView
    private lateinit var textHumidity: TextView
    private lateinit var textWeatherLocation: TextView
    private lateinit var textWind: TextView
    private lateinit var textPrecipitation: TextView
    private lateinit var textAgriculturalRecommendation: TextView
    private lateinit var textAgriculturalScore: TextView
    private lateinit var textWeatherErrorDetail: TextView

    // Buttons
    private lateinit var btnRetry: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnShareLocation: androidx.appcompat.widget.AppCompatButton
    private lateinit var btnOpenMaps: androidx.appcompat.widget.AppCompatButton

    // Data
    private lateinit var geocodingManager: GeocodingManager
    private lateinit var weatherManager: SimpleWeatherManager
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var altitude: Double? = null
    private var locationDetails: LocationDetails? = null
    private var agriculturalContext: AgriculturalContext? = null
    private var foundKodeDesa: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_details)

        // Initialize managers
        geocodingManager = GeocodingManager(this)
        weatherManager = SimpleWeatherManager(this)

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

        // New administrative detail views
        textIndustrialArea = findViewById(R.id.text_industrial_area)
        textVillage = findViewById(R.id.text_village)
        textCounty = findViewById(R.id.text_county)
        textState = findViewById(R.id.text_state)
        textProvinceCode = findViewById(R.id.text_province_code)
        textRegion = findViewById(R.id.text_region)
        textRegionCode = findViewById(R.id.text_region_code)
        textCountry = findViewById(R.id.text_country)

        // Weather views
        progressWeather = findViewById(R.id.progress_weather)
        layoutWeatherContent = findViewById(R.id.layout_weather_content)
        layoutWeatherError = findViewById(R.id.layout_weather_error)
        imageWeatherIcon = findViewById(R.id.image_weather_icon)
        textWeatherDesc = findViewById(R.id.text_weather_desc)
        textTemperature = findViewById(R.id.text_temperature)
        textHumidity = findViewById(R.id.text_humidity)
        textWeatherLocation = findViewById(R.id.text_weather_location)
        textWind = findViewById(R.id.text_wind)
        textPrecipitation = findViewById(R.id.text_precipitation)
        textAgriculturalRecommendation = findViewById(R.id.text_agricultural_recommendation)
        textAgriculturalScore = findViewById(R.id.text_agricultural_score)
        textWeatherErrorDetail = findViewById(R.id.text_weather_error_detail)

        textDataSource = findViewById(R.id.text_data_source)
        textQualityScore = findViewById(R.id.text_quality_score)
        textTimestamp = findViewById(R.id.text_timestamp)
        textErrorMessage = findViewById(R.id.text_error_message)

        // Buttons
        btnRetry = findViewById(R.id.btn_retry)
        btnShareLocation = findViewById(R.id.btn_share_location)
        btnOpenMaps = findViewById(R.id.btn_open_maps)
        // btnRefreshWeather removed - weather data loads automatically

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

        // btnRefreshWeather removed - weather data loads automatically
    }

    private fun loadLocationDetails() {
        showLoadingState()

        lifecycleScope.launch {
            try {
                // Try to get detailed location info with BigDataCoid first
                locationDetails = geocodingManager.getDetailedLocationInfo(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude
                )

                // Get agricultural context if available
                agriculturalContext = geocodingManager.getAgriculturalContext(latitude, longitude)

                runOnUiThread {
                    hideLoadingState()
                    if (locationDetails != null) {
                        locationDetails?.let { showLocationDetails(it) }
                        showAgriculturalContext(agriculturalContext)
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
            textAltitude.text = String.format(Locale.getDefault(), "Alt: %.1f m", details.altitude)
            textAltitude.visibility = View.VISIBLE
        } else {
            textAltitude.visibility = View.GONE
        }

        // Update detailed administrative information
        updateAdministrativeFields(details)

        // Update additional information
        textDataSource.text = "Sumber Data: ${details.dataSource}"
        textQualityScore.text = "Kualitas Lokasi: ${details.getLocationQualityScore()}/100"

        // Format timestamp
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID"))
        textTimestamp.text = "Waktu: ${sdf.format(Date(details.timestamp))}"

        // Show content
        layoutContent.visibility = View.VISIBLE
        layoutError.visibility = View.GONE

        // Auto-search kode desa using Nominatim data (will trigger weather loading)
        searchKodeDesaFromNominatim()

        Log.d(TAG, "Location details displayed successfully")
    }

    /**
     * Update administrative detail fields with new API data structure
     */
    private fun updateAdministrativeFields(details: LocationDetails) {
        // Industrial Area (if available)
        if (!details.premises.isNullOrEmpty()) {
            textIndustrialArea.text = "🏭 Kawasan Industri: ${details.premises}"
            textIndustrialArea.visibility = View.VISIBLE
        } else {
            textIndustrialArea.visibility = View.GONE
        }

        // Village/Desa
        textVillage.text = "🏘️ Desa/Kelurahan: ${details.village ?: "-"}"

        // County/Kabupaten
        textCounty.text = "📋 Kabupaten: ${details.regency ?: "-"}"

        // State/Province
        textState.text = "🗺️ Provinsi: ${details.province ?: "-"}"

        // Province Code (extract from additional data if available)
        textProvinceCode.text = "🏷️ Kode Provinsi: ${getProvinceCode(details)}"

        // Region (subAdminArea in our structure)
        textRegion.text = "🌏 Wilayah: ${details.subAdminArea ?: "-"}"

        // Region Code (extract from additional data if available)
        textRegionCode.text = "🏷️ Kode Wilayah: ${getRegionCode(details)}"

        // Country
        textCountry.text = "🌍 Negara: ${details.country ?: "-"}"

        // Full formatted address
        val adminInfo = details.getAdministrativeHierarchy()
        if (adminInfo.isNotEmpty()) {
            textAdministrativeInfo.text = "Alamat Lengkap:\n$adminInfo"
        } else {
            textAdministrativeInfo.text = "Alamat Lengkap: ${details.address}"
        }
    }

    /**
     * Extract province code from data source or location details
     */
    private fun getProvinceCode(details: LocationDetails): String {
        // Try to extract from dataSource if it contains ISO codes
        if (details.dataSource.contains("ISO3166-2-lvl4")) {
            // This would require parsing the raw API response
            // For now, try to get from province name mapping
            return when (details.province?.uppercase()) {
                "BANTEN" -> "ID-BT"
                "DKI JAKARTA" -> "ID-JK"
                "JAWA BARAT" -> "ID-JB"
                "JAWA TENGAH" -> "ID-JT"
                "JAWA TIMUR" -> "ID-JI"
                "DAERAH ISTIMEWA YOGYAKARTA" -> "ID-YO"
                else -> details.province?.let { "Provinsi: $it" } ?: "-"
            }
        }
        return "-"
    }

    /**
     * Extract region code from data source or location details
     */
    private fun getRegionCode(details: LocationDetails): String {
        if (details.dataSource.contains("ISO3166-2-lvl3")) {
            return when (details.subAdminArea?.uppercase()) {
                "JAWA" -> "ID-JW"
                "SUMATERA" -> "ID-SU"
                "KALIMANTAN" -> "ID-KA"
                "SULAWESI" -> "ID-SL"
                "BALI" -> "ID-BA"
                "PAPUA" -> "ID-PA"
                else -> details.subAdminArea?.let { "Wilayah: $it" } ?: "-"
            }
        }
        return "-"
    }

    /**
     * Auto-search kode desa using county/city and village data from Nominatim
     */
    private fun searchKodeDesaFromNominatim() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🔍 Starting Auto-search Kode Desa from Nominatim Data...")

                // Get county and village data from locationDetails
                val county = locationDetails?.regency // county dari Nominatim
                val village = locationDetails?.village // village dari Nominatim

                Log.d(TAG, "📍 Nominatim Data for Search:")
                Log.d(TAG, "   County/City: '$county'")
                Log.d(TAG, "   Village: '$village'")

                if (!county.isNullOrEmpty() && !village.isNullOrEmpty()) {
                    // Search for kode desa
                    val searchResults = geocodingManager.searchKodeDesa(county, village)

                    Log.d(TAG, "📊 Auto-search Results Summary:")
                    Log.d(TAG, "   Search query: '$county' + '$village'")
                    Log.d(TAG, "   Results found: ${searchResults.size}")

                    if (searchResults.isNotEmpty()) {
                        Log.d(TAG, "🎉 Kode Desa Found Successfully:")
                        searchResults.forEach { result ->
                            Log.d(TAG, "   ✅ ${result.nama} - ${result.kode}")
                        }

                        // Show the first result as primary match and store it for BMKG API
                        val primaryMatch = searchResults.first()
                        foundKodeDesa = primaryMatch.kode
                        Log.d(TAG, "🏆 Primary Match: ${primaryMatch.nama} (${primaryMatch.kode})")
                        Log.d(TAG, "💾 Kode desa saved for BMKG API: $foundKodeDesa")

                        // Load weather data using the found kode desa
                        loadWeatherDataWithKodeDesa()
                    } else {
                        Log.w(TAG, "❌ No Kode Desa found for location")
                        foundKodeDesa = null

                        // Fallback to coordinate-based weather
                        loadWeatherData()
                    }
                } else {
                    Log.w(TAG, "❌ Incomplete Nominatim data for search:")
                    Log.w(TAG, "   County available: ${!county.isNullOrEmpty()}")
                    Log.w(TAG, "   Village available: ${!village.isNullOrEmpty()}")
                    foundKodeDesa = null

                    // Fallback to coordinate-based weather
                    loadWeatherData()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in auto-search kode desa", e)
            }
        }
    }

    private fun showAgriculturalContext(context: AgriculturalContext?) {
        if (context == null) {
            // Hide agricultural context card if not available
            findViewById<View>(R.id.card_agricultural_context)?.visibility = View.GONE
            return
        }

        try {
            // Find agricultural context views (need to add these to layout)
            val textMainIndustry = findViewById<TextView>(R.id.text_main_industry)
            val textClimateInfo = findViewById<TextView>(R.id.text_climate_info)
            val textElevation = findViewById<TextView>(R.id.text_agricultural_elevation)
            val textRecommendations = findViewById<TextView>(R.id.text_agricultural_recommendations)
            val cardAgriculturalContext = findViewById<View>(R.id.card_agricultural_context)

            // Set agricultural information
            textMainIndustry?.text = "Industri Utama: ${context.mainIndustry}"

            textClimateInfo?.text = buildString {
                append("Iklim: ${context.climateZone}")
                append("\nMusim Tanam: ${context.growingSeason}")
                append("\nSuhu Rata-rata: ${String.format("%.1f°C", context.averageTemperature)}")
                append("\nCurah Hujan Tahunan: ${String.format("%.0f mm", context.annualRainfall)}")
            }

            textElevation?.text = "Ketinggian: ${String.format("%.0f mdpl", context.elevation)}"

            // Get recommendations
            val recommendations = context.getRecommendations()
            textRecommendations?.text = recommendations.joinToString("\n")

            // Show agricultural context card
            cardAgriculturalContext?.visibility = View.VISIBLE

            Log.d(TAG, "Agricultural context displayed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error displaying agricultural context", e)
            // Hide card if there's an error
            findViewById<View>(R.id.card_agricultural_context)?.visibility = View.GONE
        }
    }

    private fun showLoadingState() {
        layoutLoading.visibility = View.VISIBLE
        layoutError.visibility = View.GONE
        layoutContent.visibility = View.GONE
    }

    private fun hideLoadingState() {
        layoutLoading.visibility = View.GONE
    }

    private fun loadWeatherData() {
        // Fallback to coordinate-based weather if no kode desa found
        Log.d(TAG, "🌤️ Loading weather data using coordinates (fallback)")
        loadWeatherDataByCoordinates()
    }

    /**
     * Load weather data using found kode desa as ADM4 parameter for BMKG API
     */
    private fun loadWeatherDataWithKodeDesa() {
        foundKodeDesa?.let { kodeDesa ->
            Log.d(TAG, "🌤️ Loading weather data using kode desa as ADM4: $kodeDesa")
            loadWeatherDataForAdm4(kodeDesa)
        } ?: run {
            Log.w(TAG, "❌ No kode desa available, falling back to coordinates")
            loadWeatherDataByCoordinates()
        }
    }

    /**
     * Load weather data using coordinates as fallback
     */
    private fun loadWeatherDataByCoordinates() {
        // Show loading state for weather
        progressWeather.visibility = View.VISIBLE
        layoutWeatherContent.visibility = View.GONE
        layoutWeatherError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                Log.d(TAG, "🌤️ Loading weather data using coordinates: $latitude, $longitude")

                // Get weather data by coordinates
                val coordinateWeatherResponse = weatherManager.getWeatherForecastByCoordinates(latitude, longitude)

                if (coordinateWeatherResponse != null) {
                    showWeatherData(coordinateWeatherResponse)
                    Toast.makeText(this@LocationDetailsActivity, "Menggunakan data cuaca berdasarkan koordinat", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "✅ Weather data loaded successfully for coordinates")
                } else {
                    showWeatherError("Data cuaca tidak tersedia untuk lokasi ini")
                    Log.w(TAG, "❌ Weather data not available for this location")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading weather data by coordinates", e)
                showWeatherError("Gagal memuat data cuaca: ${e.message}")
            }
        }
    }

    // refreshWeatherData() and showAdm4InputDialog() removed - weather data loads automatically

    private fun loadWeatherDataForAdm4(adm4Code: String) {
        // Show loading state for weather
        progressWeather.visibility = View.VISIBLE
        layoutWeatherContent.visibility = View.GONE
        layoutWeatherError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Check if this is auto-search using kode desa
                val isAutoSearch = foundKodeDesa == adm4Code

                if (isAutoSearch) {
                    Log.d(TAG, "🎯 Auto-loading BMKG weather data using found kode desa")
                    Log.d(TAG, "   Kode Desa: $adm4Code")
                    Log.d(TAG, "   Source: Auto-search from Nominatim data")
                    Log.d(TAG, "   API Endpoint: https://api.bmkg.go.id/publik/prakiraan-cuaca?adm4=$adm4Code")
                } else {
                    Log.d(TAG, "Loading weather data for adm4: $adm4Code")
                    Log.d(TAG, "   Source: Manual user input")
                    Log.d(TAG, "   API Endpoint: https://api.bmkg.go.id/publik/prakiraan-cuaca?adm4=$adm4Code")
                }

                // Get weather data by adm4 code
                val weatherResponse = weatherManager.getWeatherForecastByAdm4(adm4Code)

                if (weatherResponse != null) {
                    showWeatherData(weatherResponse)

                    val message = if (isAutoSearch) {
                        "Cuaca otomatis untuk ${weatherResponse.lokasi.desa} (Kode: $adm4Code)"
                    } else {
                        "Cuaca diperbarui untuk ${weatherResponse.lokasi.desa}"
                    }
                    Toast.makeText(this@LocationDetailsActivity, message, Toast.LENGTH_LONG).show()

                    Log.d(TAG, "✅ Weather data loaded successfully for adm4: $adm4Code")
                    Log.d(TAG, "   Location: ${weatherResponse.lokasi.desa}, ${weatherResponse.lokasi.kecamatan}")
                    Log.d(TAG, "   Source: ${if (isAutoSearch) "Auto-search" else "Manual input"}")
                } else {
                    // Fallback to coordinates if adm4 fails
                    Log.w(TAG, "Weather data not available for adm4: $adm4Code, trying coordinates")
                    val coordinateWeatherResponse = weatherManager.getWeatherForecastByCoordinates(latitude, longitude)

                    if (coordinateWeatherResponse != null) {
                        showWeatherData(coordinateWeatherResponse)
                        Toast.makeText(this@LocationDetailsActivity, "Menggunakan data cuaca berdasarkan koordinat", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Weather data loaded successfully for coordinates")
                    } else {
                        showWeatherError("Data cuaca tidak tersedia untuk kode wilayah $adm4Code")
                        Log.w(TAG, "Weather data not available for this location")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading weather data", e)
                showWeatherError("Gagal memuat data cuaca: ${e.message}")
            }
        }
    }

    private fun showWeatherData(weatherResponse: BMKGWeatherResponse) {
        hideWeatherLoading()

        try {
            // Log the weather response structure
            Log.d(TAG, "Weather response location: ${weatherResponse.lokasi.desa}, ${weatherResponse.lokasi.kecamatan}")
            Log.d(TAG, "Weather response data size: ${weatherResponse.data.size} areas")

            // Get current weather (first period from new structure)
            val currentWeather = weatherResponse.data.firstOrNull()?.cuaca?.flatten()?.firstOrNull()

            if (currentWeather != null) {
                // Log current weather details
                Log.d(TAG, "Current weather: ${currentWeather.weatherDesc}, Temp: ${currentWeather.t}°C, Humidity: ${currentWeather.hu}%")
                Log.d(TAG, "Precipitation: ${currentWeather.tp}mm, Wind: ${currentWeather.ws}km/h ${currentWeather.wd}")

                // Show weather content
                layoutWeatherContent.visibility = View.VISIBLE
                layoutWeatherError.visibility = View.GONE

                // Update current weather display
                textWeatherDesc.text = "Cuaca: ${currentWeather.weatherDesc}"
                textTemperature.text = "🌡️ Suhu: ${currentWeather.t}°C"
                textHumidity.text = "💧 Kelembaban: ${currentWeather.hu}%"
                textPrecipitation.text = "🌧️ Curah Hujan: ${currentWeather.tp} mm"
                textWind.text = "💨 Angin: ${currentWeather.ws} km/h (${currentWeather.wd})"
                textWeatherLocation.text = "📍 Lokasi: ${weatherManager.getLocationDisplayName(weatherResponse.lokasi)}"

                // Simple agricultural recommendation
                textAgriculturalRecommendation.text = "📋 Rekomendasi: ${getSimpleAgriculturalRecommendation(currentWeather)}"
                textAgriculturalScore.text = "🏆 Skor Pertanian: ${getSimpleAgriculturalScore(currentWeather)}/100"

                // Load BMKG weather icon with proper URL encoding
                Log.d(TAG, "Loading BMKG weather icon for: ${currentWeather.weatherDesc}")

                // Make sure ImageView is visible
                imageWeatherIcon.visibility = View.VISIBLE

                var imageUrl = currentWeather.image
                Log.d(TAG, "Original BMKG URL: $imageUrl")

                // Fix URL encoding using proper URL encoding
                // Extract filename part and encode it
                val urlParts = imageUrl.split("/")
                if (urlParts.size >= 2) {
                    val filename = urlParts.last()
                    val encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8")
                        .replace("+", "%20") // Replace + with %20 for spaces

                    // Rebuild URL with encoded filename
                    val pathWithoutFile = urlParts.dropLast(1).joinToString("/")
                    imageUrl = "$pathWithoutFile/$encodedFilename"
                }

                Log.d(TAG, "Encoded BMKG URL: $imageUrl")

                // Additional manual fixes for common cases
                imageUrl = imageUrl.replace(" ", "%20")
                    .replace("%2520", "%20") // Fix double-encoded spaces

                // Load BMKG image with detailed logging
                imageWeatherIcon.load(imageUrl) {
                    placeholder(android.R.drawable.ic_dialog_info)
                    error(android.R.drawable.ic_dialog_info)
                    crossfade(true)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    networkCachePolicy(CachePolicy.ENABLED)
                    listener(
                        onSuccess = { _, _ ->
                            Log.d(TAG, "✅ BMKG weather icon loaded successfully: $imageUrl")
                        },
                        onError = { _, _ ->
                            Log.e(TAG, "❌ BMKG weather icon FAILED: $imageUrl")
                        }
                    )
                }

                Log.d(TAG, "Weather data displayed successfully")
            } else {
                showWeatherError("Data cuaca saat ini tidak tersedia")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error displaying weather data", e)
            showWeatherError("Error menampilkan data cuaca: ${e.message}")
        }
    }

    private fun getSimpleAgriculturalRecommendation(weather: BMKGWeatherData): String {
        return when {
            weather.tp > 10.0 -> "Hujan lebat - Tidak disarankan aktivitas lapangan"
            weather.tp > 5.0 -> "Hujan sedang - Pertimbangkan untuk menunda aktivitas"
            weather.t > 35 -> "Suhu tinggi - Pastikan irigasi cukup"
            weather.t < 15 -> "Suhu rendah - Perlindungi tanaman dari dingin"
            weather.ws > 25.0 -> "Angin kencang - Hindari penyemprotan"
            weather.hu > 85 -> "Kelembaban tinggi - Waspada penyakit jamur"
            weather.hu < 40 -> "Kelembaban rendah - Pastikan irigasi cukup"
            else -> "Cuaca baik untuk aktivitas pertanian"
        }
    }

    private fun getSimpleAgriculturalScore(weather: BMKGWeatherData): Int {
        var score = 0

        // Temperature scoring
        when {
            weather.t in 20..30 -> score += 30
            weather.t in 15..35 -> score += 20
            else -> score += 10
        }

        // Humidity scoring
        when {
            weather.hu in 60..80 -> score += 20
            weather.hu in 50..90 -> score += 15
            else -> score += 10
        }

        // Precipitation scoring
        when {
            weather.tp == 0.0 -> score += 20
            weather.tp in 0.1..5.0 -> score += 15
            else -> score += 5
        }

        return score.coerceAtMost(100)
    }

    private fun getWeatherIconResource(weatherDesc: String): Int {
        return when {
            weatherDesc.lowercase().contains("cerah") -> {
                if (weatherDesc.lowercase().contains("berawan")) {
                    android.R.drawable.ic_menu_gallery // Partly cloudy
                } else {
                    android.R.drawable.ic_menu_view // Clear/Sunny
                }
            }
            weatherDesc.lowercase().contains("berawan") -> android.R.drawable.ic_menu_preferences // Cloudy
            weatherDesc.lowercase().contains("hujan") -> {
                if (weatherDesc.lowercase().contains("ringan") || weatherDesc.lowercase().contains("sedang")) {
                    android.R.drawable.ic_menu_save // Light/Moderate rain
                } else {
                    android.R.drawable.ic_menu_search // Heavy rain
                }
            }
            weatherDesc.lowercase().contains("kabut") || weatherDesc.lowercase().contains("berkabut") -> android.R.drawable.ic_menu_info_details // Fog
            weatherDesc.lowercase().contains("mendung") -> android.R.drawable.ic_menu_camera // Overcast
            else -> android.R.drawable.ic_dialog_info // Default icon
        }
    }

    private fun showWeatherError(message: String) {
        hideWeatherLoading()

        layoutWeatherContent.visibility = View.GONE
        layoutWeatherError.visibility = View.VISIBLE
        textWeatherErrorDetail.text = message

        Log.w(TAG, "Weather error: $message")
    }

    private fun hideWeatherLoading() {
        progressWeather.visibility = View.GONE
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
                    appendLine("Ketinggian: ${String.format(Locale.getDefault(), "%.1f m", details.altitude)}")
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
        finish()
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
