package zoan.drtaniku

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
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
 * - Integration with BigDataCoid for Indonesian locations
 */
class GeocodingManager(private val context: Context) {

    private lateinit var nominatimManager: NominatimManager

    companion object {
        private const val TAG = "GeocodingManager"
        private const val MAX_RESULTS = 5
        private const val LOCALE_LANGUAGE = "id"
        private const val LOCALE_COUNTRY = "ID"
    }

    private val geocoder = Geocoder(context, Locale(LOCALE_LANGUAGE, LOCALE_COUNTRY))
    private val locationCache = mutableMapOf<String, LocationDetails>()

    init {
        // Initialize Nominatim manager
        nominatimManager = NominatimManager(context)
    }

    /**
     * Get location details from coordinates using OpenStreetMap Nominatim (primary)
     * Falls back to Android Geocoder if Nominatim fails
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

            // Try Nominatim API first for global locations
            Log.d(TAG, "Trying Nominatim API for geocoding")
            val nominatimLocation = try {
                nominatimManager.getLocationDetails(latitude, longitude, altitude)
            } catch (e: Exception) {
                Log.w(TAG, "Nominatim API failed, falling back to Android Geocoder", e)
                null
            }

            if (nominatimLocation != null && nominatimLocation.hasCompleteAddress()) {
                // Cache Nominatim result
                locationCache[cacheKey] = nominatimLocation
                Log.d(TAG, "Successfully geocoded using Nominatim: ${nominatimLocation.getShortName()}")
                return@withContext nominatimLocation
            }

            // Fallback to Android Geocoder
            Log.d(TAG, "Using Android Geocoder as fallback")
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

    /**
     * Get detailed location information with agricultural context
     */
    suspend fun getDetailedLocationInfo(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null
    ): LocationDetails = withContext(Dispatchers.IO) {
        try {
            // Try Nominatim detailed info first
            val locationDetails = nominatimManager.getLocationDetails(latitude, longitude, altitude)

            // Get agricultural context from Nominatim
            val agriculturalContext = nominatimManager.getAgriculturalContext(latitude, longitude, locationDetails)

            // Cache detailed result
            val cacheKey = "${latitude}_${longitude}_detailed"
            locationCache[cacheKey] = locationDetails

            return@withContext locationDetails
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim detailed API failed, using basic geocoding", e)
            getLocationDetails(latitude, longitude, altitude)
        }
    }

    /**
     * Get agricultural context for current location
     */
    suspend fun getAgriculturalContext(
        latitude: Double,
        longitude: Double
    ): AgriculturalContext? {
        return try {
            nominatimManager.getAgriculturalContext(latitude, longitude)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting agricultural context", e)
            null
        }
    }

    /**
     * Check if Nominatim service is available
     */
    suspend fun isNominatimAvailable(): Boolean {
        return try {
            nominatimManager.isServiceAvailable()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Nominatim availability", e)
            false
        }
    }

    /**
     * Get cache statistics from both geocoders
     */
    fun getCacheStatistics(): Map<String, Any> {
        val nominatimStats = nominatimManager.getCacheStats()
        val androidCacheSize = locationCache.size

        return mapOf(
            "nominatim_cache" to nominatimStats,
            "android_geocoder_cache_size" to androidCacheSize,
            "total_cache_entries" to (nominatimStats["active_entries"] as Int + androidCacheSize)
        )
    }

    /**
     * Get service information
     */
    /**
     * Search kode desa using county/city and village data from Nominatim
     * This function performs automatic search for village codes using CSV data
     */
    suspend fun searchKodeDesa(
        county: String?,
        village: String?
    ): List<DesaData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Auto-search Kode Desa:")
                Log.d(TAG, "   County/City: '$county'")
                Log.d(TAG, "   Village: '$village'")

                if (county.isNullOrEmpty() || village.isNullOrEmpty()) {
                    Log.w(TAG, "❌ Missing county or village data for search")
                    return@withContext emptyList<DesaData>()
                }

                // Normalize county input (kabupaten -> kab, etc)
                val normalizedCounty = normalizeCountyInput(county)
                val normalizedVillage = village.lowercase().trim()

                Log.d(TAG, "📝 Normalized Search Parameters:")
                Log.d(TAG, "   Normalized County: '$normalizedCounty'")
                Log.d(TAG, "   Normalized Village: '$normalizedVillage'")

                // Load CSV data for search (following SearchDataActivity model)
                val allDesaData = loadCSVDataForSearch()
                Log.d(TAG, "📋 Data Snapshot Created:")
                Log.d(TAG, "   Total desa data: ${allDesaData.size}")

                // Pre-build kabupaten lookup map for efficiency (exactly like SearchDataActivity)
                val kabupatenMap = allDesaData
                    .filter { it.kode.replace(".", "").length == 4 }
                    .associate { it.kode to it.nama.lowercase() }

                Log.d(TAG, "🗺️ Kabupaten Map Built:")
                Log.d(TAG, "   Total kabupaten entries: ${kabupatenMap.size}")

                // Step 1: Finding matching kabupaten (exactly like SearchDataActivity)
                Log.d(TAG, "📍 Step 1: Finding matching kabupaten...")
                val matchingKabupaten = mutableListOf<Pair<String, String>>() // kode -> nama
                var kabupatenMatchesCount = 0

                kabupatenMap.forEach { (kode, nama) ->
                    if (nama.contains(normalizedCounty)) {
                        matchingKabupaten.add(Pair(kode, nama))
                        kabupatenMatchesCount++
                        Log.d(TAG, "   ✅ Found Kabupaten: $kode - $nama")
                    }
                }

                Log.d(TAG, "   Total matching kabupaten: $kabupatenMatchesCount")

                // Step 2: Filter desa berdasarkan kode awal kabupaten yang ditemukan (exactly like SearchDataActivity)
                Log.d(TAG, "📍 Step 2: Filtering desa by kabupaten code...")
                var desaFilteredCount = 0
                var finalMatchesCount = 0

                val searchResults: List<DesaData> = if (matchingKabupaten.isNotEmpty()) {
                    // Only search desa that belong to matching kabupaten
                    val kabupatenCodes = matchingKabupaten.map { it.first }.toSet()

                    allDesaData.filter { desaData ->
                        // Check if desa belongs to any matching kabupaten (desa should start with kabupaten code)
                        val belongsToMatchingKabupaten = kabupatenCodes.any { kodeKabupaten ->
                            desaData.kode.startsWith(kodeKabupaten)
                        }

                        if (belongsToMatchingKabupaten) {
                            desaFilteredCount++
                        }

                        // Only apply desa name filter if desa input is provided
                        val namaDesa = desaData.nama.lowercase()
                        val matchesDesa = namaDesa.contains(normalizedVillage)

                        val finalMatch = belongsToMatchingKabupaten && matchesDesa
                        if (finalMatch) {
                            finalMatchesCount++
                            if (finalMatchesCount <= 5) { // Log first 5 matches for debugging
                                Log.d(TAG, "✅ Found Match #$finalMatchesCount:")
                                Log.d(TAG, "   Kode: ${desaData.kode}")
                                Log.d(TAG, "   Nama: ${desaData.nama}")
                                Log.d(TAG, "   Belongs to Kabupaten: ${desaData.kode} (exists in matches: ${kabupatenCodes.any { desaData.kode.startsWith(it) }})")
                                Log.d(TAG, "   Matches Desa: $matchesDesa")
                            }
                        }

                        finalMatch
                    }
                } else {
                    Log.w(TAG, "   ❌ No matching kabupaten found for: '$normalizedCounty'")
                    emptyList<DesaData>()
                }

                Log.d(TAG, "📊 Search Results:")
                Log.d(TAG, "   Total entries checked: ${allDesaData.size}")
                Log.d(TAG, "   Desa filtered by kabupaten: $desaFilteredCount")
                Log.d(TAG, "   Final matches found: $finalMatchesCount")

                searchResults.forEach { result ->
                    Log.d(TAG, "   ✅ Found: ${result.nama} (${result.kode})")
                }

                if (searchResults.isEmpty()) {
                    Log.w(TAG, "❌ No matching kode desa found for '$county' + '$village'")
                }

                searchResults

            } catch (e: Exception) {
                Log.e(TAG, "Error searching kode desa", e)
                emptyList<DesaData>()
            }
        }
    }

    /**
     * Normalize county input similar to SearchDataActivity
     */
    private fun normalizeCountyInput(input: String): String {
        val normalized = input.lowercase().trim()

        return when {
            // Variasi penulisan "kabupaten"
            normalized.startsWith("kabupaten") -> "kab. ${normalized.substringAfter("kabupaten").trim()}"
            normalized.startsWith("kab") && !normalized.startsWith("kab.") -> "kab. ${normalized.substringAfter("kab").trim()}"
            normalized.startsWith("kab.") -> normalized

            // Variasi penulisan "kota"
            normalized.startsWith("kota") -> when {
                normalized.startsWith("kota ") -> "kota ${normalized.substringAfter("kota ").trim()}"
                else -> "kota $normalized"
            }

            // Jika sudah sesuai format, kembalikan as-is
            normalized.startsWith("kab.") || normalized.startsWith("kota ") -> normalized

            // Default: tambahkan "kab." di depan
            else -> "kab. $normalized"
        }
    }

    /**
     * Load CSV data for searching kode desa
     */
    private suspend fun loadCSVDataForSearch(): List<DesaData> {
        return try {
            val inputStream = context.assets.open("base.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))

            val desaList = mutableListOf<DesaData>()

            reader.useLines { lines ->
                lines.forEach { line ->
                    try {
                        val parts = line.split(",", limit = 2)
                        if (parts.size == 2) {
                            val kode = parts[0].trim()
                            val nama = parts[1].trim()

                            // Only include desa entries (10 digits) and kabupaten entries (4 digits) for county matching
                            val digitsInKode = kode.replace(".", "").length
                            if (digitsInKode == 10 || digitsInKode == 4) {
                                desaList.add(DesaData(kode, nama))
                            }
                        }
                    } catch (e: Exception) {
                        Log.v(TAG, "Error parsing CSV line: $line", e)
                    }
                }
            }

            Log.d(TAG, "📁 CSV Data Loaded for Search: ${desaList.size} entries")
            desaList

        } catch (e: Exception) {
            Log.e(TAG, "Error loading CSV data for search", e)
            emptyList<DesaData>()
        }
    }

    fun getServiceInfo(): Map<String, Any> {
        return mapOf(
            "primary_service" to "OpenStreetMap Nominatim",
            "fallback_service" to "Android Geocoder",
            "service_info" to nominatimManager.getServiceInfo()
        )
    }
}