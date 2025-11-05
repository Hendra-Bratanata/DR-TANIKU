package zoan.drtaniku

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

/**
 * GeocodingManager - Manages reverse geocoding to get location details
 *
 * Features:
 * - Convert GPS coordinates to detailed address information
 * - Support for Indonesian administrative boundaries
 * - Offline capability using Android Geocoder
 * - Caching mechanism for performance
 * - Fallback handling for geocoding failures
 */
class GeocodingManager(private val context: Context) {

    companion object {
        private const val TAG = "GeocodingManager"
        private const val MAX_RESULTS = 5
        private const val LOCALE_LANGUAGE = "id"
        private const val LOCALE_COUNTRY = "ID"
    }

    private val geocoder = Geocoder(context, Locale(LOCALE_LANGUAGE, LOCALE_COUNTRY))
    private val locationCache = mutableMapOf<String, LocationDetails>()

    /**
     * Get location details from coordinates using Android Geocoder
     */
    suspend fun getLocationDetails(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null
    ): LocationDetails = withContext(Dispatchers.IO) {
        try {
            // Create cache key
            val cacheKey = "${latitude}_${longitude}"

            // Check cache first
            locationCache[cacheKey]?.let { cachedDetails ->
                Log.d(TAG, "Using cached location details for $cacheKey")
                return@withContext cachedDetails.copy(
                    altitude = altitude,
                    hasAltitude = altitude != null,
                    timestamp = System.currentTimeMillis()
                )
            }

            // Get location details from Geocoder
            val addresses = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latitude, longitude, MAX_RESULTS) ?: emptyList()
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, MAX_RESULTS) ?: emptyList()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error in geocoding", e)
                emptyList()
            }

            if (addresses.isNotEmpty()) {
                val bestAddress = selectBestAddress(addresses, latitude, longitude)
                val locationDetails = parseAddress(bestAddress, latitude, longitude, altitude)

                // Cache the result
                locationCache[cacheKey] = locationDetails

                Log.d(TAG, "Successfully geocoded location: ${locationDetails.getShortName()}")
                return@withContext locationDetails
            } else {
                Log.w(TAG, "No address found for coordinates: $latitude, $longitude")
                return@withContext createFallbackLocationDetails(latitude, longitude, altitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in geocoding", e)
            return@withContext createFallbackLocationDetails(latitude, longitude, altitude)
        }
    }

    /**
     * Get location details from Location object
     */
    suspend fun getLocationDetails(location: Location): LocationDetails {
        return getLocationDetails(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null
        )
    }

    /**
     * Select the best address from multiple results
     */
    private fun selectBestAddress(addresses: List<Address>, lat: Double, lng: Double): Address {
        return addresses.maxByOrNull { address ->
            var score = 0

            // Prefer addresses with complete administrative hierarchy
            if (!address.adminArea.isNullOrEmpty()) score += 20  // Province
            if (!address.subAdminArea.isNullOrEmpty()) score += 15  // Regency
            if (!address.locality.isNullOrEmpty()) score += 15  // District
            if (!address.subLocality.isNullOrEmpty()) score += 10  // Village
            if (!address.thoroughfare.isNullOrEmpty()) score += 10  // Street
            if (!address.postalCode.isNullOrEmpty()) score += 5   // Postal code
            if (!address.featureName.isNullOrEmpty()) score += 5   // Landmark

            // Prefer addresses with more complete info
            if (!address.countryName.isNullOrEmpty()) score += 2
            if (!address.countryCode.isNullOrEmpty()) score += 1

            // Prefer addresses closer to the exact coordinates
            if (address.hasLatitude() && address.hasLongitude()) {
                val distance = calculateDistance(
                    lat, lng,
                    address.latitude, address.longitude
                )
                if (distance < 0.001) score += 10  // Very close
                else if (distance < 0.01) score += 5  // Close
            }

            score
        } ?: addresses.first()
    }

    /**
     * Parse Android Address to LocationDetails
     */
    private fun parseAddress(
        address: Address,
        latitude: Double,
        longitude: Double,
        altitude: Double?
    ): LocationDetails {
        return LocationDetails(
            // Basic info
            address = address.getAddressLine(0) ?: "${latitude}, ${longitude}",
            name = address.featureName ?: address.subLocality ?: "Unknown Location",
            latitude = latitude,
            longitude = longitude,

            // Administrative boundaries (Indonesia-specific)
            province = address.adminArea,        // Provinsi
            regency = address.subAdminArea,      // Kabupaten/Kota
            district = address.locality,         // Kecamatan
            village = address.subLocality,       // Desa/Kelurahan
            postalCode = address.postalCode,     // Kode Pos

            // Geographic info
            country = address.countryName ?: "Indonesia",
            countryCode = address.countryCode ?: "ID",
            locality = address.subLocality,      // Area/Neighborhood

            // Street address
            streetName = address.thoroughfare,   // Nama jalan
            streetNumber = address.subThoroughfare, // Nomor rumah
            subLocality = address.subLocality,   // Sub-area

            // Metadata
            accuracy = if (address.hasLatitude() && address.hasLongitude()) {
                calculateDistance(latitude, longitude, address.latitude, address.longitude).toFloat()
            } else null,
            dataSource = "Android Geocoder",
            timestamp = System.currentTimeMillis(),
            hasAltitude = altitude != null,
            altitude = altitude,

            // Additional context
            featureName = address.featureName,   // Nama landmark
            premises = address.premises,         // Gedung/lokasi spesifik
            subAdminArea = address.subAdminArea, // Sub-administrative area
            thoroughfare = address.thoroughfare  // Jalan/boulevard
        )
    }

    /**
     * Create fallback location details when geocoding fails
     */
    private fun createFallbackLocationDetails(
        latitude: Double,
        longitude: Double,
        altitude: Double?
    ): LocationDetails {
        return LocationDetails(
            address = "${latitude}, ${longitude}",
            name = "Unknown Location",
            latitude = latitude,
            longitude = longitude,
            province = null,
            regency = null,
            district = null,
            village = null,
            postalCode = null,
            country = "Indonesia",
            countryCode = "ID",
            locality = null,
            streetName = null,
            streetNumber = null,
            subLocality = null,
            accuracy = null,
            dataSource = "GPS Only (Geocoding Failed)",
            timestamp = System.currentTimeMillis(),
            hasAltitude = altitude != null,
            altitude = altitude,
            featureName = null,
            premises = null,
            subAdminArea = null,
            thoroughfare = null
        )
    }

    /**
     * Calculate distance between two coordinates in degrees
     */
    private fun calculateDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val R = 6371000.0 // Earth's radius in meters
        val latDistance = Math.toRadians(lat2 - lat1)
        val lngDistance = Math.toRadians(lng2 - lng1)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c // Distance in meters
    }

    /**
     * Clear location cache
     */
    fun clearCache() {
        locationCache.clear()
        Log.d(TAG, "Location cache cleared")
    }

    /**
     * Get cache size
     */
    fun getCacheSize(): Int {
        return locationCache.size
    }

    /**
     * Check if geocoder is available
     */
    fun isGeocoderAvailable(): Boolean {
        return Geocoder.isPresent()
    }

    /**
     * Get current locale being used
     */
    fun getCurrentLocale(): Locale {
        return Locale(LOCALE_LANGUAGE, LOCALE_COUNTRY)
    }
}