package zoan.drtaniku

/**
 * LocationDetails - Data class for comprehensive location information
 *
 * Contains detailed address information obtained from geocoding services
 * Used for displaying location details in DR Taniku app
 */
data class LocationDetails(
    // Basic Location Info
    val address: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,

    // Administrative Boundaries (Indonesia-specific)
    val province: String?,      // Provinsi
    val regency: String?,       // Kabupaten/Kota
    val district: String?,      // Kecamatan
    val village: String?,       // Desa/Kelurahan
    val postalCode: String?,    // Kode Pos

    // Geographic Info
    val country: String?,       // Negara
    val countryCode: String?,   // ID
    val locality: String?,      // Area/Neighborhood

    // Street Address
    val streetName: String?,    // Nama jalan
    val streetNumber: String?,  // Nomor rumah
    val subLocality: String?,   // Sub-area

    // Metadata
    val accuracy: Float?,       // Akurasi geocoding
    val dataSource: String,     // Sumber data (Geocoder/Google/OSM)
    val timestamp: Long,        // Timestamp data diperoleh
    val hasAltitude: Boolean,   // Apakah ada data altitude
    val altitude: Double?,      // Ketinggian dalam meter

    // Additional Context for Agriculture
    val featureName: String?,   // Nama landmark
    val premises: String?,      // Gedung/lokasi spesifik
    val subAdminArea: String?,  // Sub-administrative area
    val thoroughfare: String?    // Jalan/boulevard
) {
    companion object {
        fun getEmpty(): LocationDetails {
            return LocationDetails(
                address = "Unknown Location",
                name = "Unknown",
                latitude = 0.0,
                longitude = 0.0,
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
                dataSource = "Unknown",
                timestamp = System.currentTimeMillis(),
                hasAltitude = false,
                altitude = null,
                featureName = null,
                premises = null,
                subAdminArea = null,
                thoroughfare = null
            )
        }

        fun createFromCoordinates(lat: Double, lng: Double): LocationDetails {
            return LocationDetails(
                address = "$lat, $lng",
                name = "Current Location",
                latitude = lat,
                longitude = lng,
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
                dataSource = "GPS Only",
                timestamp = System.currentTimeMillis(),
                hasAltitude = false,
                altitude = null,
                featureName = null,
                premises = null,
                subAdminArea = null,
                thoroughfare = null
            )
        }
    }

    /**
     * Get formatted address for display
     */
    fun getFormattedAddress(): String {
        return if (address != "Unknown Location") {
            address
        } else {
            buildString {
                if (!streetName.isNullOrEmpty()) {
                    append(streetName)
                    if (!streetNumber.isNullOrEmpty()) {
                        append(" No. $streetNumber")
                    }
                }
                if (!village.isNullOrEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(village)
                }
                if (!district.isNullOrEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(district)
                }
                if (!regency.isNullOrEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(regency)
                }
                if (!province.isNullOrEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(province)
                }
                if (isEmpty()) {
                    append("${latitude}, ${longitude}")
                }
            }
        }
    }

    /**
     * Get short location name for display
     */
    fun getShortName(): String {
        return when {
            !name.isNullOrEmpty() && name != "Unknown" -> name
            !village.isNullOrEmpty() -> village
            !district.isNullOrEmpty() -> district
            !regency.isNullOrEmpty() -> regency
            else -> "${latitude}, ${longitude}"
        }
    }

    /**
     * Get administrative hierarchy
     */
    fun getAdministrativeHierarchy(): String {
        return buildString {
            if (!village.isNullOrEmpty()) {
                append("Desa: $village")
                append("\n")
            }
            if (!district.isNullOrEmpty()) {
                append("Kecamatan: $district")
                append("\n")
            }
            if (!regency.isNullOrEmpty()) {
                append("Kabupaten/Kota: $regency")
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
     * Check if location has complete address information
     */
    fun hasCompleteAddress(): Boolean {
        return !province.isNullOrEmpty() &&
               !regency.isNullOrEmpty() &&
               !district.isNullOrEmpty()
    }

    /**
     * Get location quality score (0-100)
     */
    fun getLocationQualityScore(): Int {
        var score = 0

        // Basic coordinates
        if (latitude != 0.0 && longitude != 0.0) score += 20

        // Administrative info
        if (!village.isNullOrEmpty()) score += 15
        if (!district.isNullOrEmpty()) score += 15
        if (!regency.isNullOrEmpty()) score += 15
        if (!province.isNullOrEmpty()) score += 15

        // Street address
        if (!streetName.isNullOrEmpty()) score += 10
        if (!postalCode.isNullOrEmpty()) score += 5

        // Additional info
        if (!name.isNullOrEmpty() && name != "Unknown") score += 3
        if (!country.isNullOrEmpty()) score += 2

        return score.coerceAtMost(100)
    }
}