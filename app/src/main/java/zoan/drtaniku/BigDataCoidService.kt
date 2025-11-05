package zoan.drtaniku

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * BigDataCoidService - Retrofit interface for BigDataCoid API
 *
 * Indonesian geocoding service that provides detailed location information
 * for Indonesian regions including administrative boundaries and addresses
 *
 * API Documentation: https://documentation.bigdatacoid.com
 * Free tier available with reasonable limits for development
 */
interface BigDataCoidService {

    /**
     * Reverse geocoding - get address from coordinates
     *
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @return Response containing detailed Indonesian address information
     */
    @GET("api/coordinate/reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<BigDataCoidResponse>

    /**
     * Geocoding - get coordinates from address
     *
     * @param address Address to search for
     * @param limit Maximum number of results (default: 5)
     * @return Response containing coordinate information
     */
    @GET("api/coordinate/search")
    suspend fun searchAddress(
        @Query("address") address: String,
        @Query("limit") limit: Int = 5
    ): Response<List<BigDataCoidResponse>>

    /**
     * Get detailed administrative information for coordinates
     *
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @return Response with comprehensive administrative data
     */
    @GET("api/coordinate/detail")
    suspend fun getCoordinateDetail(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<BigDataCoidDetailResponse>
}

/**
 * BigDataCoid response data models
 */

data class BigDataCoidResponse(
    val success: Boolean,
    val data: BigDataCoidLocationData?,
    val message: String?,
    val code: Int?
)

data class BigDataCoidLocationData(
    val coordinate: CoordinateData,
    val address: AddressData,
    val administrative: AdministrativeData,
    val metadata: MetadataData
)

data class CoordinateData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val altitude: Double?
)

data class AddressData(
    val formattedAddress: String,
    val street: String?,
    val streetNumber: String?,
    val village: String?,        // Desa/Kelurahan
    val district: String?,       // Kecamatan
    val regency: String?,        // Kabupaten/Kota
    val province: String,        // Provinsi
    val postalCode: String?,
    val country: String = "Indonesia",
    val countryCode: String = "ID"
)

data class AdministrativeData(
    val provinceId: String?,
    val regencyId: String?,
    val districtId: String?,
    val villageId: String?,
    val bpsCode: String?,         // BPS (Badan Pusat Statistik) code
    val regionType: String?,      // Tipe wilayah (Kota/Kabupaten)
    val areaLevel: String?,       // Level wilayah
    val parentRegion: String?     // Wilayah induk
)

data class MetadataData(
    val dataSource: String,
    val timestamp: String,
    val quality: LocationQuality,
    val reliability: Double,      // 0.0 - 1.0
    val confidence: Double        // 0.0 - 1.0
)

data class LocationQuality(
    val addressCompleteness: Double,
    val administrativeLevel: Int,  // 1-4 (1=Province, 4=Village)
    val accuracyLevel: String,     // High/Medium/Low
    val score: Int               // 0-100
)

data class BigDataCoidDetailResponse(
    val success: Boolean,
    val data: BigDataCoidDetailData?,
    val message: String?,
    val code: Int?
)

data class BigDataCoidDetailData(
    val basic: BigDataCoidLocationData,
    val geography: GeographyData,
    val demographics: DemographicsData?,
    val economy: EconomyData?,
    val climate: ClimateData?
)

data class GeographyData(
    val area: Double,            // Luas wilayah dalam km²
    val elevation: ElevationData,
    val boundaries: BoundaryData,
    val landmarks: List<LandmarkData>?
)

data class ElevationData(
    val average: Double,         // Ketinggian rata-rata
    val minimum: Double,
    val maximum: Double,
    val unit: String = "m"
)

data class BoundaryData(
    val northBoundary: Double,
    val southBoundary: Double,
    val eastBoundary: Double,
    val westBoundary: Double,
    val perimeter: Double        // Keliling dalam km
)

data class LandmarkData(
    val name: String,
    val type: String,            // Government/Education/Health/etc
    val distance: Double,        // Jarak dari koordinat dalam km
    val coordinates: CoordinateData
)

data class DemographicsData(
    val population: Int,
    val density: Double,         // Jumlah per km²
    val growthRate: Double,      // Persen per tahun
    val ageDistribution: AgeDistributionData?
)

data class AgeDistributionData(
    val children: Double,        // 0-14 tahun
    val workingAge: Double,      // 15-64 tahun
    val elderly: Double          // 65+ tahun
)

data class EconomyData(
    val mainIndustry: String,
    val agriculturalProducts: List<String>?,
    val incomeLevel: String,      // Low/Medium/High
    val unemploymentRate: Double
)

data class ClimateData(
    val averageTemperature: Double,
    val averageHumidity: Double,
    val annualRainfall: Double,
    val climateZone: String,
    val growingSeason: String
)