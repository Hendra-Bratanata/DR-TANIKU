package zoan.drtaniku

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * NominatimService - Retrofit interface for OpenStreetMap Nominatim API
 *
 * Free geocoding service that provides detailed location information
 * for global regions with good coverage for Indonesian areas
 *
 * API Documentation: https://nominatim.org/release-docs/develop/api/
 * Free to use with reasonable rate limits (1 request per second max)
 * No API key required
 *
 * Usage Policy: https://operations.osmfoundation.org/policies/nominatim/
 */
interface NominatimService {

    /**
     * Reverse geocoding - get address from coordinates
     *
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @param format Response format (json, xml, html, jsonv2)
     * @param addressdetails Include address details (1 = yes, 0 = no)
     * @param zoom Zoom level (0-18, default: 18)
     * @param accept-language Language preference (id for Indonesian)
     * @param email Your email for API usage identification
     * @return Response containing detailed address information
     */
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressdetails: Int = 1,
        @Query("zoom") zoom: Int = 18,
        @Query("accept-language") language: String = "id",
        @Query("email") email: String = "drtaniku@app.com"
    ): Response<NominatimResponse>

    /**
     * Search for locations by address or place name
     *
     * @param q Query string (address or place name)
     * @param format Response format (json, xml, html, jsonv2)
     * @param addressdetails Include address details (1 = yes, 0 = no)
     * @param limit Maximum number of results (1-50, default: 10)
     * @param countrycodes Limit search to specific countries (id for Indonesia)
     * @param accept-language Language preference
     * @param email Your email for API usage identification
     * @return Response containing search results
     */
    @GET("search")
    suspend fun searchAddress(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressdetails: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("countrycodes") countrycodes: String = "id",
        @Query("accept-language") language: String = "id",
        @Query("email") email: String = "drtaniku@app.com"
    ): Response<List<NominatimResponse>>

    /**
     * Search for locations near coordinates
     *
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @param format Response format
     * @param addressdetails Include address details
     * @param limit Maximum number of results
     * @param radius Search radius in kilometers
     * @param accept-language Language preference
     * @param email Your email for API usage identification
     * @return Response containing nearby locations
     */
    @GET("search")
    suspend fun searchNearby(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressdetails: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("radius") radius: Int = 1, // 1 km radius
        @Query("accept-language") language: String = "id",
        @Query("email") email: String = "drtaniku@app.com"
    ): Response<List<NominatimResponse>>

    /**
     * Search by specific address components
     *
     * @param street Street address
     * @param city City name
     @param county County name
     @param state State name
     * @param country Country name
     * @param postalcode Postal code
     * @param format Response format
     * @param addressdetails Include address details
     * @param limit Maximum number of results
     * @param countrycodes Limit to Indonesia
     * @param accept-language Language preference
     * @param email Your email for API usage identification
     * @return Response containing search results
     */
    @GET("search")
    suspend fun searchStructuredAddress(
        @Query("street") street: String? = null,
        @Query("city") city: String? = null,
        @Query("county") county: String? = null,
        @Query("state") state: String? = null,
        @Query("country") country: String? = null,
        @Query("postalcode") postalcode: String? = null,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressdetails: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("countrycodes") countrycodes: String = "id",
        @Query("accept-language") language: String = "id",
        @Query("email") email: String = "drtaniku@app.com"
    ): Response<List<NominatimResponse>>
}

/**
 * Nominatim response data models
 */

data class NominatimResponse(
    val place_id: Long,
    val licence: String,
    val osm_type: String,
    val osm_id: Long,
    val boundingbox: List<String>,
    val lat: String,
    val lon: String,
    val display_name: String,
    val `class`: String,
    val type: String,
    val importance: Double,
    val icon: String,
    val address: NominatimAddress?
)

data class NominatimAddress(
    // Simplified Indonesian address structure based on requirements
    val industrial: String?,      // Industrial estate/area name
    val village: String?,         // Desa/Kelurahan
    val county: String?,          // Kabupaten
    val state: String?,           // Provinsi
    val country: String?,         // Negara
    val country_code: String?,    // Kode negara

    // Additional fields that might be useful
    val road: String?,            // Nama jalan (optional)
    val postcode: String?,        // Kode pos (optional)

    // Additional Indonesian-specific fields
    val city: String?,            // Kota (if available)
    val town: String?,            // Town (if available)
    val suburb: String?,          // Suburb (if available)

    // Raw JSON fields with special characters that need custom parsing
    @SerializedName("ISO3166-2-lvl4")
    val iso31662lvl4: String? = null, // Province ISO code (ID-BT)

    @SerializedName("ISO3166-2-lvl3")
    val iso31662lvl3: String? = null  // Region ISO code (ID-JW)
) {
    // Helper methods to access fields with dashes and ensure consistency
    fun getProvinceIso(): String? = iso31662lvl4
    fun getRegionIso(): String? = iso31662lvl3

    // Try to get region from various possible fields
    fun getRegion(): String? {
        return when {
            !state.isNullOrEmpty() && state != "Indonesia" -> state
            !suburb.isNullOrEmpty() -> suburb
            !city.isNullOrEmpty() -> city
            !town.isNullOrEmpty() -> town
            else -> null
        }
    }
}

/**
 * Simplified Indonesian address parser based on API requirements
 */
data class IndonesianAddress(
    // Core address fields from API
    val industrial: String?,      // Industrial estate/area name
    val village: String?,         // Desa/Kelurahan
    val county: String?,          // Kabupaten
    val state: String?,           // Provinsi
    val provinceIso: String?,     // Province ISO code (ID-BT)
    val region: String?,          // Region (Jawa)
    val regionIso: String?,       // Region ISO code (ID-JW)
    val country: String?,         // Negara
    val countryCode: String?,    // Kode negara

    // Additional optional fields
    val road: String?,            // Nama jalan (optional)
    val postalCode: String?,      // Kode pos (optional)

    // Raw address data for parsing
    val displayName: String,
    val osmType: String,
    val osmId: Long,
    val importance: Double,
    val placeClass: String,
    val placeType: String
) {
    /**
     * Get short name for display
     */
    fun getShortName(): String {
        return when {
            !industrial.isNullOrEmpty() -> industrial
            !village.isNullOrEmpty() -> village
            !county.isNullOrEmpty() -> county
            !state.isNullOrEmpty() -> state
            !road.isNullOrEmpty() -> road
            else -> displayName
        }
    }

    /**
     * Get formatted Indonesian address
     */
    fun getFormattedAddress(): String {
        return buildString {
            if (!road.isNullOrEmpty()) {
                append(road)
            }

            if (!village.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append("Desa $village")
            }

            if (!county.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append("Kab. $county")
            }

            if (!state.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append(state)
            }

            if (!region.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append(region)
            }

            if (!country.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append(country)
            }

            if (!postalCode.isNullOrEmpty()) {
                if (isNotEmpty()) append(" ")
                append(postalCode)
            }

            if (isEmpty()) {
                append(displayName)
            }
        }
    }

    /**
     * Get administrative hierarchy
     */
    fun getAdministrativeHierarchy(): String {
        return buildString {
            if (!industrial.isNullOrEmpty()) {
                append("Kawasan: $industrial")
                append("\n")
            }
            if (!village.isNullOrEmpty()) {
                append("Desa/Kelurahan: $village")
                append("\n")
            }
            if (!county.isNullOrEmpty()) {
                append("Kabupaten: $county")
                append("\n")
            }
            if (!state.isNullOrEmpty()) {
                append("Provinsi: $state")
                append("\n")
                if (!provinceIso.isNullOrEmpty()) {
                    append("Kode Provinsi: $provinceIso")
                    append("\n")
                }
            }
            if (!region.isNullOrEmpty()) {
                append("Wilayah: $region")
                append("\n")
                if (!regionIso.isNullOrEmpty()) {
                    append("Kode Wilayah: $regionIso")
                    append("\n")
                }
            }
            if (!postalCode.isNullOrEmpty()) {
                append("Kode Pos: $postalCode")
            }
        }
    }

    /**
     * Check if address is in Indonesia
     */
    fun isInIndonesia(): Boolean {
        return countryCode.equals("ID", ignoreCase = true) ||
               country.equals("Indonesia", ignoreCase = true)
    }

    /**
     * Get administrative level (1=Province, 2=County, 3=Village, 4=Industrial)
     */
    fun getAdministrativeLevel(): Int {
        return when {
            !state.isNullOrEmpty() -> {
                when {
                    !industrial.isNullOrEmpty() -> 4
                    !village.isNullOrEmpty() -> 3
                    !county.isNullOrEmpty() -> 2
                    else -> 1
                }
            }
            else -> 0
        }
    }

    /**
     * Get location type classification
     */
    fun getLocationType(): String {
        return when (placeClass) {
            "building" -> "Bangunan"
            "highway" -> "Jalan"
            "landuse" -> "Lahan"
            "amenity" -> "Fasilitas"
            "shop" -> "Toko"
            "tourism" -> "Wisata"
            "leisure" -> "Rekreasi"
            "natural" -> "Alam"
            else -> placeType
        }
    }

    /**
     * Check if address has complete administrative info
     */
    fun hasCompleteAddress(): Boolean {
        return !state.isNullOrEmpty() &&
               !county.isNullOrEmpty()
    }
}