package zoan.drtaniku

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.*

/**
 * NominatimManager - OpenStreetMap Nominatim API Manager
 *
 * Free geocoding service for global locations with good Indonesian coverage
 * Provides comprehensive location information using OpenStreetMap data
 *
 * Features:
 * - Free to use with no API key required
 * - Good coverage for Indonesian regions
 * - Indonesian language support
 * - Rate limiting compliance (1 request/second)
 * - Fallback to Android Geocoder
 * - Smart caching mechanism
 * - Agricultural context integration
 *
 * Rate Limiting:
 * - Maximum 1 request per second
 * - Please use caching to avoid hitting limits
 * - Use your app email in requests
 */
class NominatimManager(private val context: Context) {

    companion object {
        private const val TAG = "NominatimManager"
        private const val BASE_URL = "https://nominatim.openstreetmap.org/"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 30L
        private const val WRITE_TIMEOUT = 30L
        private const val RATE_LIMIT_DELAY = 1100L // 1.1 seconds between requests

        // Cache expiry: 24 hours for stable locations
        private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L

        // Rate limiting
        private var lastRequestTime = 0L
        private const val REQUEST_TIMEOUT = 5000L // 5 seconds timeout
    }

    private val nominatimService: NominatimService
    private val locationCache = mutableMapOf<String, CacheEntry>()

    data class CacheEntry(
        val locationDetails: LocationDetails,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
    }

    init {
        // Setup Retrofit with conservative settings for free API
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC // Reduce logging for production
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        nominatimService = retrofit.create(NominatimService::class.java)
    }

    /**
     * Get detailed location information using Nominatim API
     * Falls back to Android Geocoder if API fails
     */
    suspend fun getLocationDetails(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null
    ): LocationDetails = withContext(Dispatchers.IO) {
        try {
            // Rate limiting check
            enforceRateLimit()

            val cacheKey = "${latitude}_${longitude}_nominatim"

            // Check cache first
            locationCache[cacheKey]?.let { cached ->
                if (!cached.isExpired()) {
                    Log.d(TAG, "Using cached Nominatim location for $cacheKey")
                    return@withContext cached.locationDetails.copy(
                        altitude = altitude,
                        hasAltitude = altitude != null,
                        timestamp = System.currentTimeMillis()
                    )
                } else {
                    locationCache.remove(cacheKey)
                }
            }

            // Try Nominatim API
            Log.d(TAG, "Requesting Nominatim API for coordinates: $latitude, $longitude")
            val response = nominatimService.reverseGeocode(
                lat = latitude,
                lon = longitude,
                addressdetails = 1,
                zoom = 18,
                language = "id",
                email = "drtaniku@app.com"
            )

            if (response.isSuccessful && response.body() != null) {
                val indonesianAddress = parseNominatimResponse(response.body()!!)

                val locationDetails = createLocationDetailsFromNominatim(
                    indonesianAddress,
                    latitude,
                    longitude,
                    altitude
                )

                // Cache the result
                locationCache[cacheKey] = CacheEntry(locationDetails, System.currentTimeMillis())

                Log.d(TAG, "Successfully geocoded using Nominatim: ${locationDetails.getShortName()}")
                return@withContext locationDetails
            } else {
                Log.w(TAG, "Nominatim API returned empty or failed response: ${response.code()}")
                getFallbackLocationDetails(latitude, longitude, altitude)
            }

        } catch (e: IOException) {
            Log.e(TAG, "Network error with Nominatim, falling back to Android Geocoder", e)
            getFallbackLocationDetails(latitude, longitude, altitude)
        } catch (e: Exception) {
            Log.e(TAG, "Error with Nominatim geocoding", e)
            getFallbackLocationDetails(latitude, longitude, altitude)
        }
    }

    /**
     * Search for locations by address using Nominatim
     */
    suspend fun searchAddress(address: String): List<LocationDetails> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            Log.d(TAG, "Searching for address: $address")
            val response = nominatimService.searchAddress(
                query = address,
                countrycodes = "id",
                limit = 10
            )

            if (response.isSuccessful && response.body() != null) {
                val results = response.body()!!.mapNotNull { nominatimResponse ->
                    val indonesianAddress = parseNominatimResponse(nominatimResponse)
                    if (indonesianAddress.isInIndonesia()) {
                        createLocationDetailsFromNominatim(
                            indonesianAddress,
                            nominatimResponse.lat.toDouble(),
                            nominatimResponse.lon.toDouble()
                        )
                    } else null
                }

                Log.d(TAG, "Found ${results.size} Indonesian locations for: $address")
                return@withContext results
            } else {
                Log.w(TAG, "Search failed or no results found")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching address", e)
            emptyList()
        }
    }

    /**
     * Search for nearby locations around coordinates
     */
    suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 1
    ): List<LocationDetails> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            Log.d(TAG, "Searching nearby locations for: $latitude, $longitude")
            val response = nominatimService.searchNearby(
                lat = latitude,
                lon = longitude,
                radius = radiusKm,
                limit = 10
            )

            if (response.isSuccessful && response.body() != null) {
                val results = response.body()!!.mapNotNull { nominatimResponse ->
                    val indonesianAddress = parseNominatimResponse(nominatimResponse)
                    createLocationDetailsFromNominatim(
                        indonesianAddress,
                        nominatimResponse.lat.toDouble(),
                        nominatimResponse.lon.toDouble()
                    )
                }

                Log.d(TAG, "Found ${results.size} nearby locations")
                return@withContext results
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching nearby locations", e)
            emptyList()
        }
    }

    /**
     * Parse Nominatim response to Indonesian address
     */
    private fun parseNominatimResponse(response: NominatimResponse): IndonesianAddress {
        val address = response.address

        return IndonesianAddress(
            industrial = address?.industrial,
            village = address?.village,
            county = address?.county,
            state = address?.state,
            provinceIso = address?.getProvinceIso(),
            region = address?.getRegion(),
            regionIso = address?.getRegionIso(),
            country = address?.country ?: "Indonesia",
            countryCode = address?.country_code ?: "id",
            road = address?.road,
            postalCode = address?.postcode,
            displayName = response.display_name,
            osmType = response.osm_type,
            osmId = response.osm_id,
            importance = response.importance,
            placeClass = response.`class`,
            placeType = response.type
        )
    }

    /**
     * Create LocationDetails from Nominatim data
     */
    private fun createLocationDetailsFromNominatim(
        indonesianAddress: IndonesianAddress,
        latitude: Double,
        longitude: Double,
        altitude: Double? = null
    ): LocationDetails {
        return LocationDetails(
            // Basic info
            address = indonesianAddress.getFormattedAddress(),
            name = indonesianAddress.getShortName(),
            latitude = latitude,
            longitude = longitude,

            // Administrative boundaries (Indonesia-specific)
            province = indonesianAddress.state,
            regency = indonesianAddress.county,
            district = null, // Not available in simplified structure
            village = indonesianAddress.village,
            postalCode = indonesianAddress.postalCode,

            // Geographic info
            country = indonesianAddress.country,
            countryCode = indonesianAddress.countryCode,
            locality = indonesianAddress.village,

            // Street address
            streetName = indonesianAddress.road,
            streetNumber = null, // Not available in simplified structure
            subLocality = indonesianAddress.village,

            // Metadata
            accuracy = null, // Nominatim doesn't provide accuracy
            dataSource = "OpenStreetMap Nominatim",
            timestamp = System.currentTimeMillis(),
            hasAltitude = altitude != null,
            altitude = altitude,

            // Additional info
            featureName = indonesianAddress.displayName,
            premises = indonesianAddress.industrial, // Use industrial area as premises
            subAdminArea = indonesianAddress.region,
            thoroughfare = indonesianAddress.road
        )
    }

    /**
     * Fallback to Android Geocoder when Nominatim fails
     */
    private suspend fun getFallbackLocationDetails(
        latitude: Double,
        longitude: Double,
        altitude: Double?
    ): LocationDetails {
        return try {
            val geocodingManager = GeocodingManager(context)
            val fallbackLocation = geocodingManager.getLocationDetails(latitude, longitude, altitude)

            // Add note about fallback
            fallbackLocation.copy(
                dataSource = "${fallbackLocation.dataSource} (Nominatim Fallback)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Android Geocoder also failed", e)
            LocationDetails.createFromCoordinates(latitude, longitude).copy(
                altitude = altitude,
                hasAltitude = altitude != null,
                dataSource = "Nominatim & Android Geocoder Failed"
            )
        }
    }

    /**
     * Get agricultural context for Indonesian location
     * Uses heuristics based on location type and administrative data
     */
    suspend fun getAgriculturalContext(
        latitude: Double,
        longitude: Double,
        locationDetails: LocationDetails? = null
    ): AgriculturalContext? = withContext(Dispatchers.IO) {
        try {
            // Get location details if not provided
            val details = locationDetails ?: getLocationDetails(latitude, longitude)

            if (!details.isInIndonesia()) {
                return@withContext null
            }

            // Search for nearby agricultural facilities
            val nearbyPlaces = searchNearby(latitude, longitude, 2)
            val agriculturalKeywords = listOf(
                "pertanian", "kebun", "sawah", "ladang", "perkebunan",
                "peternakan", "ikan", "tanaman", "bibit", "pupuk"
            )

            val isAgriculturalArea = nearbyPlaces.any { place ->
                agriculturalKeywords.any { keyword ->
                    place.name.contains(keyword, ignoreCase = true)
                } || place.dataSource.contains("agricultural", ignoreCase = true)
            }

            // Use heuristics based on location and administrative data
            val mainIndustry = when {
                isAgriculturalArea -> "Pertanian"
                details.regency?.contains("kota", ignoreCase = true) == true -> "Perdagangan & Jasa"
                details.province?.contains("bali", ignoreCase = true) == true -> "Pariwisata"
                details.province?.contains("sumatera", ignoreCase = true) == true -> "Perkebunan"
                details.province?.contains("jawa", ignoreCase = true) == true -> "Industri & Pertanian"
                else -> "Umum"
            }

            // Estimate climate based on location (Indonesia is tropical)
            val avgTemp = when {
                details.province?.contains("papua", ignoreCase = true) == true -> 24.0
                details.province?.contains("sumatera", ignoreCase = true) == true -> 27.0
                details.province?.contains("jawa", ignoreCase = true) == true -> 26.0
                details.province?.contains("sulawesi", ignoreCase = true) == true -> 25.0
                details.province?.contains("kalimantan", ignoreCase = true) == true -> 26.5
                else -> 26.0
            }

            // Estimate rainfall based on region
            val annualRainfall = when {
                details.province?.contains("papua", ignoreCase = true) == true -> 2500
                details.province?.contains("sumatera", ignoreCase = true) == true -> 2000
                details.province?.contains("kalimantan", ignoreCase = true) == true -> 3000
                details.province?.contains("jawa", ignoreCase = true) == true -> 1500
                else -> 2000
            }

            // Estimate elevation based on province (rough estimate)
            val elevation = when {
                details.province?.contains("papua", ignoreCase = true) == true -> 500.0
                details.province?.contains("sumatera barat", ignoreCase = true) == true -> 100.0
                details.province?.contains("sumatera", ignoreCase = true) == true -> 300.0
                details.province?.contains("jawa barat", ignoreCase = true) == true -> 50.0
                details.province?.contains("jawa", ignoreCase = true) == true -> 200.0
                else -> 200.0
            }

            return@withContext AgriculturalContext(
                mainIndustry = mainIndustry,
                agriculturalProducts = getIndonesianAgriculturalProducts(details.province),
                incomeLevel = "Menengah", // Rough estimate
                growingSeason = "Sepanjang Tahun",
                climateZone = "Tropis Basah",
                averageTemperature = avgTemp,
                annualRainfall = annualRainfall.toDouble(),
                elevation = elevation
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error getting agricultural context", e)
            null
        }
    }

    /**
     * Get Indonesian agricultural products by province
     */
    private fun getIndonesianAgriculturalProducts(province: String?): List<String> {
        return when {
            province?.contains("sumatera", ignoreCase = true) == true -> listOf(
                "Kelapa Sawit", "Kopi", "Karet", "Kakao", "Tebu", "Padi", "Jagung"
            )
            province?.contains("jawa", ignoreCase = true) == true -> listOf(
                "Padi", "Jagung", "Kedelai", "Tebu", "Sayuran", "Buah-buahan"
            )
            province?.contains("kalimantan", ignoreCase = true) == true -> listOf(
                "Kelapa Sawit", "Karet", "Padi Ladang", "Ubi Kayu"
            )
            province?.contains("sulawesi", ignoreCase = true) == true -> listOf(
                "Kelapa", "Kopi", "Cengkeh", "Pala", "Meti"
            )
            province?.contains("papua", ignoreCase = true) == true -> listOf(
                "Kelapa", "Sagu", "Coklat", "Vanili", "Kopi"
            )
            province?.contains("bali", ignoreCase = true) == true -> listOf(
                "Padi", "Jeruk", "Kopi", "Cengkeh", "Pala"
            )
            else -> listOf("Padi", "Jagung", "Kedelai", "Tebu")
        }
    }

    /**
     * Enforce rate limiting for Nominatim API
     */
    private suspend fun enforceRateLimit() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime

        if (timeSinceLastRequest < RATE_LIMIT_DELAY) {
            val delayTime = RATE_LIMIT_DELAY - timeSinceLastRequest
            Log.d(TAG, "Rate limiting: waiting ${delayTime}ms")
            kotlinx.coroutines.delay(delayTime)
        }

        lastRequestTime = currentTime
    }

    /**
     * Clear location cache
     */
    fun clearCache() {
        locationCache.clear()
        Log.d(TAG, "Nominatim location cache cleared")
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Any> {
        val totalEntries = locationCache.size
        val expiredEntries = locationCache.values.count { it.isExpired() }

        return mapOf(
            "total_entries" to totalEntries,
            "expired_entries" to expiredEntries,
            "active_entries" to (totalEntries - expiredEntries),
            "cache_expiry_hours" to (CACHE_EXPIRY_MS / (1000 * 60 * 60)),
            "rate_limit_delay_ms" to RATE_LIMIT_DELAY
        )
    }

    /**
     * Check if Nominatim service is available
     */
    suspend fun isServiceAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Test with a known coordinate in Jakarta
            val response = nominatimService.reverseGeocode(
                lat = -6.2088,
                lon = 106.8456
            )
            response.isSuccessful && response.body() != null
        } catch (e: Exception) {
            Log.e(TAG, "Nominatim service unavailable", e)
            false
        }
    }

    /**
     * Get service information
     */
    fun getServiceInfo(): Map<String, Any> {
        return mapOf(
            "service" to "OpenStreetMap Nominatim",
            "base_url" to BASE_URL,
            "rate_limit" to "1 request/second",
            "auth_required" to false,
            "cost" to "Free",
            "language_support" to "Indonesian (id)",
            "coverage" to "Global (Excellent for Indonesia)"
        )
    }
}