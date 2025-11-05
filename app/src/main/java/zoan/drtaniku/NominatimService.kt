package zoan.drtaniku

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
    val house_number: String?,     // Nomor rumah
    val road: String?,            // Nama jalan
    val pedestrian: String?,      // Jalur pedestrian
    val suburb: String?,          // Suburb
    val hamlet: String?,          // Dusun
    val city_district: String?,   // Kecamatan
    val city: String?,            // Kota
    val county: String?,          // Kabupaten
    val state: String?,           // Provinsi
    val postcode: String?,        // Kode pos
    val country: String?,         // Negara
    val country_code: String?,    // Kode negara
    val town: String?,            // Kota kecil
    val village: String?,         // Desa/kelurahan
    val municipality: String?,     // Kotamadya
    val state_district: String?,  // District
    // Indonesian specific fields
    val subdistrict: String?,     // Kecamatan (alternative)
    val district: String?,        // Kabupaten (alternative)
    val province: String?,        // Provinsi (alternative)
    val regency: String?          // Kabupaten/Kota (alternative)
)

/**
 * Enhanced address parser for Indonesian administrative boundaries
 */
data class IndonesianAddress(
    // Basic address
    val houseNumber: String?,
    val streetName: String?,
    val village: String?,         // Desa/Kelurahan
    val subdistrict: String?,    // Kecamatan
    val district: String?,        // Kabupaten
    val city: String?,            // Kota
    val regency: String?,         // Kabupaten/Kota (alternative)
    val province: String?,        // Provinsi
    val postalCode: String?,
    val country: String = "Indonesia",
    val countryCode: String = "ID",

    // Additional context
    val suburb: String?,
    val hamlet: String?,
    val town: String?,
    val municipality: String?,

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
            !village.isNullOrEmpty() -> village
            !subdistrict.isNullOrEmpty() -> subdistrict
            !town.isNullOrEmpty() -> town
            !city.isNullOrEmpty() -> city
            !regency.isNullOrEmpty() -> regency
            !province.isNullOrEmpty() -> province
            !streetName.isNullOrEmpty() -> streetName
            else -> displayName
        }
    }

    /**
     * Get formatted Indonesian address
     */
    fun getFormattedAddress(): String {
        return buildString {
            if (!streetName.isNullOrEmpty()) {
                append(streetName)
                if (!houseNumber.isNullOrEmpty()) {
                    append(" No. $houseNumber")
                }
            }

            if (!village.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append(village)
            }
            if (!subdistrict.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append("Kec. $subdistrict")
            }
            if (!regency.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append("Kab. $regency")
            } else if (!city.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append("Kota $city")
            }
            if (!province.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append(province)
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
            if (!village.isNullOrEmpty()) {
                append("Desa/Kelurahan: $village")
                append("\n")
            }
            if (!subdistrict.isNullOrEmpty()) {
                append("Kecamatan: $subdistrict")
                append("\n")
            }
            if (!regency.isNullOrEmpty()) {
                append("Kabupaten/Kota: $regency")
                append("\n")
            } else if (!city.isNullOrEmpty()) {
                append("Kota: $city")
                append("\n")
            }
            if (!province.isNullOrEmpty()) {
                append("Provinsi: $province")
                append("\n")
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
     * Get administrative level (1=Province, 2=Regency/City, 3=District, 4=Village)
     */
    fun getAdministrativeLevel(): Int {
        return when {
            !province.isNullOrEmpty() -> {
                when {
                    !village.isNullOrEmpty() || !subdistrict.isNullOrEmpty() -> 4
                    !regency.isNullOrEmpty() || !city.isNullOrEmpty() -> 3
                    else -> 2
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
        return !province.isNullOrEmpty() &&
               (!regency.isNullOrEmpty() || !city.isNullOrEmpty()) &&
               (!subdistrict.isNullOrEmpty() || !village.isNullOrEmpty())
    }
}