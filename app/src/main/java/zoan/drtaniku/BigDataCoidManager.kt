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
 * BigDataCoidManager - Indonesian Geocoding Service Manager
 *
 * Specialized geocoding service for Indonesian regions using BigDataCoid API
 * Provides comprehensive location information including administrative boundaries,
 * demographics, and agricultural context for Indonesian locations
 *
 * Features:
 * - Indonesia-specific geocoding with detailed administrative info
 * - Fallback to Android Geocoder if API fails
 * - Caching mechanism for performance
 * - Agricultural context integration
 * - Detailed demographics and economic data
 */
class BigDataCoidManager(private val context: Context) {

    companion object {
        private const val TAG = "BigDataCoidManager"
        private const val BASE_URL = "https://api.bigdatacoid.com/"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 30L
        private const val WRITE_TIMEOUT = 30L

        // Cache expiry: 24 hours
        private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L
    }

    private val bigDataCoidService: BigDataCoidService
    private val locationCache = mutableMapOf<String, CacheEntry>()

    data class CacheEntry(
        val locationDetails: LocationDetails,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
    }

    init {
        // Setup Retrofit with logging for debugging
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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

        bigDataCoidService = retrofit.create(BigDataCoidService::class.java)
    }

    /**
     * Get detailed location information using BigDataCoid API
     * Falls back to Android Geocoder if API fails
     */
    suspend fun getLocationDetails(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null
    ): LocationDetails = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "${latitude}_${longitude}_bigdata"

            // Check cache first
            locationCache[cacheKey]?.let { cached ->
                if (!cached.isExpired()) {
                    Log.d(TAG, "Using cached BigDataCoid location for $cacheKey")
                    return@withContext cached.locationDetails.copy(
                        altitude = altitude,
                        hasAltitude = altitude != null,
                        timestamp = System.currentTimeMillis()
                    )
                } else {
                    locationCache.remove(cacheKey)
                }
            }

            // Try BigDataCoid API first
            val apiResponse = bigDataCoidService.reverseGeocode(latitude, longitude)

            if (apiResponse.isSuccessful && apiResponse.body()?.success == true) {
                val bigDataData = apiResponse.body()?.data
                if (bigDataData != null) {
                    val locationDetails = parseBigDataCoidResponse(bigDataData, latitude, longitude, altitude)

                    // Cache the result
                    locationCache[cacheKey] = CacheEntry(locationDetails, System.currentTimeMillis())

                    Log.d(TAG, "Successfully geocoded using BigDataCoid: ${locationDetails.getShortName()}")
                    return@withContext locationDetails
                }
            }

            Log.w(TAG, "BigDataCoid API failed, falling back to Android Geocoder")
            getFallbackLocationDetails(latitude, longitude, altitude)

        } catch (e: IOException) {
            Log.e(TAG, "Network error with BigDataCoid, falling back to Android Geocoder", e)
            getFallbackLocationDetails(latitude, longitude, altitude)
        } catch (e: Exception) {
            Log.e(TAG, "Error with BigDataCoid geocoding", e)
            getFallbackLocationDetails(latitude, longitude, altitude)
        }
    }

    /**
     * Get detailed location information with comprehensive data
     */
    suspend fun getDetailedLocationInfo(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null
    ): LocationDetails = withContext(Dispatchers.IO) {
        try {
            val response = bigDataCoidService.getCoordinateDetail(latitude, longitude)

            if (response.isSuccessful && response.body()?.success == true) {
                val detailData = response.body()?.data
                if (detailData != null) {
                    val locationDetails = parseBigDataCoidDetailResponse(detailData, latitude, longitude, altitude)

                    // Cache the detailed result
                    val cacheKey = "${latitude}_${longitude}_bigdata_detail"
                    locationCache[cacheKey] = CacheEntry(locationDetails, System.currentTimeMillis())

                    return@withContext locationDetails
                }
            }

            // Fallback to basic geocoding
            getLocationDetails(latitude, longitude, altitude)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting detailed location info", e)
            getLocationDetails(latitude, longitude, altitude)
        }
    }

    /**
     * Parse BigDataCoid response to LocationDetails
     */
    private fun parseBigDataCoidResponse(
        data: BigDataCoidLocationData,
        latitude: Double,
        longitude: Double,
        altitude: Double?
    ): LocationDetails {
        val address = data.address
        val administrative = data.administrative
        val coordinate = data.coordinate
        val metadata = data.metadata

        return LocationDetails(
            // Basic info
            address = address.formattedAddress,
            name = address.village ?: address.district ?: address.regency ?: "Unknown Location",
            latitude = latitude,
            longitude = longitude,

            // Administrative boundaries (Indonesia-specific)
            province = address.province,
            regency = address.regency,
            district = address.district,
            village = address.village,
            postalCode = address.postalCode,

            // Geographic info
            country = address.country,
            countryCode = address.countryCode,
            locality = address.village,

            // Street address
            streetName = address.street,
            streetNumber = address.streetNumber,
            subLocality = address.village,

            // Metadata
            accuracy = coordinate.accuracy.toFloat(),
            dataSource = "BigDataCoid (${metadata.dataSource})",
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(metadata.timestamp)?.time
                ?: System.currentTimeMillis(),
            hasAltitude = altitude != null || coordinate.altitude != null,
            altitude = altitude ?: coordinate.altitude,

            // Additional info
            featureName = address.street,
            premises = null,
            subAdminArea = address.regency,
            thoroughfare = address.street
        )
    }

    /**
     * Parse detailed BigDataCoid response with additional context
     */
    private fun parseBigDataCoidDetailResponse(
        data: BigDataCoidDetailData,
        latitude: Double,
        longitude: Double,
        altitude: Double?
    ): LocationDetails {
        val basicLocation = parseBigDataCoidResponse(data.basic, latitude, longitude, altitude)
        val geography = data.geography
        val demographics = data.demographics
        val economy = data.economy

        // Enhance location details with additional context
        return basicLocation.copy(
            // Add agricultural context in notes
            name = buildString {
                append(basicLocation.name)
                if (economy?.mainIndustry != null) {
                    append(" (${economy.mainIndustry})")
                }
            },
            // Additional metadata
            dataSource = "BigDataCoid Detailed (${data.basic.metadata.dataSource})"
        )
    }

    /**
     * Fallback to Android Geocoder when BigDataCoid fails
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
                dataSource = "${fallbackLocation.dataSource} (BigDataCoid Fallback)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Android Geocoder also failed", e)
            LocationDetails.createFromCoordinates(latitude, longitude).copy(
                altitude = altitude,
                hasAltitude = altitude != null,
                dataSource = "BigDataCoid & Android Geocoder Failed"
            )
        }
    }

    /**
     * Search for locations by address using BigDataCoid
     */
    suspend fun searchAddress(address: String): List<LocationDetails> = withContext(Dispatchers.IO) {
        try {
            val response = bigDataCoidService.searchAddress(address)

            if (response.isSuccessful) {
                response.body()?.mapNotNull { bigDataResponse ->
                    val data = bigDataResponse.data ?: return@mapNotNull null
                    parseBigDataCoidResponse(
                        data,
                        data.coordinate.latitude,
                        data.coordinate.longitude,
                        data.coordinate.altitude
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching address", e)
            emptyList()
        }
    }

    /**
     * Get agricultural context for location
     */
    suspend fun getAgriculturalContext(
        latitude: Double,
        longitude: Double
    ): AgriculturalContext? = withContext(Dispatchers.IO) {
        try {
            val response = bigDataCoidService.getCoordinateDetail(latitude, longitude)

            if (response.isSuccessful && response.body()?.success == true) {
                val detailData = response.body()?.data
                val economy = detailData?.economy
                val climate = detailData?.climate

                if (economy != null || climate != null) {
                    AgriculturalContext(
                        mainIndustry = economy?.mainIndustry ?: "Unknown",
                        agriculturalProducts = economy?.agriculturalProducts ?: emptyList(),
                        incomeLevel = economy?.incomeLevel ?: "Unknown",
                        growingSeason = climate?.growingSeason ?: "Unknown",
                        climateZone = climate?.climateZone ?: "Unknown",
                        averageTemperature = climate?.averageTemperature ?: 0.0,
                        annualRainfall = climate?.annualRainfall ?: 0.0,
                        elevation = detailData.geography.elevation.average
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting agricultural context", e)
            null
        }
    }

    /**
     * Clear location cache
     */
    fun clearCache() {
        locationCache.clear()
        Log.d(TAG, "BigDataCoid location cache cleared")
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
            "cache_expiry_hours" to (CACHE_EXPIRY_MS / (1000 * 60 * 60))
        )
    }

    /**
     * Check if BigDataCoid service is available
     */
    suspend fun isServiceAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Test with a known coordinate in Jakarta
            val response = bigDataCoidService.reverseGeocode(-6.2088, 106.8456)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "BigDataCoid service unavailable", e)
            false
        }
    }
}

/**
 * Agricultural context data for Indonesian locations
 */
data class AgriculturalContext(
    val mainIndustry: String,
    val agriculturalProducts: List<String>,
    val incomeLevel: String,
    val growingSeason: String,
    val climateZone: String,
    val averageTemperature: Double,
    val annualRainfall: Double,
    val elevation: Double
) {
    /**
     * Get agricultural recommendations based on context
     */
    fun getRecommendations(): List<String> {
        return buildList {
            // Climate-based recommendations
            when {
                annualRainfall < 1000 -> add("💧 Pertimbangkan sistem irigasi karena curah hujan rendah")
                annualRainfall > 3000 -> add("🌾 Cocok untuk padi sawah dengan curah hujan tinggi")
                else -> add("☀️ Curah hujan normal untuk berbagai jenis tanaman")
            }

            // Elevation-based recommendations
            when {
                elevation < 200 -> add("🏠 Dataran rendah - cocok untuk palawija dan sayuran")
                elevation < 500 -> add("⛰️ Dataran menengah - cocok untuk perkebunan")
                else -> add("🏔️ Dataran tinggi - cocok untuk hortikultura tropis")
            }

            // Temperature-based recommendations
            when {
                averageTemperature < 20 -> add("🌡️ Suhu rendah - cocok untuk sayuran daun")
                averageTemperature > 28 -> add("🔥 Suhu tinggi - pastikan sistem drainase baik")
                else -> add("🌤️ Suhu ideal untuk tanaman tropis")
            }

            // Industry-specific recommendations
            when (mainIndustry.lowercase()) {
                "pertanian" -> add("🌱 Wilayah agraris - tingkatkan dengan teknologi pertanian presisi")
                "perkebunan" -> add("🌴 Fokus pada tanaman perkebunan bernilai tinggi")
                "perikanan" -> add("🐨 Integrasikan dengan akuakultur jika memungkinkan")
                "peternakan" -> add("🐄 Pertimbangkan integrasi ternak-tanaman")
            }
        }
    }
}