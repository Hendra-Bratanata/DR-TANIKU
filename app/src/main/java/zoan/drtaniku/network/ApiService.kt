package zoan.drtaniku.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class DeviceListResponse(
    val Data_Count: Int,
    val data: List<Device>
)

data class Device(
    val IMEI: String,
    val Lokasi: String,
    val Alamat: String,
    val Status: String
)

data class AddDataResponse(
    val success: Boolean,
    val message: String,
    val data_id: String? = null
)

interface ApiService {
    @GET("api/id")
    suspend fun getDeviceList(
        @Query("api_key") apiKey: String
    ): Response<DeviceListResponse>

    @GET("api/tambahData")
    suspend fun sendDataToServer(
        @Query("imei") imei: String,
        @Query("n") nitrogen: Double,
        @Query("p") phosphorus: Double,
        @Query("k") potassium: Double,
        @Query("ph") ph: Double,
        @Query("st") temperature: Double,
        @Query("sh") humidity: Double,
        @Query("maps") mapsUrl: String,
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double
    ): Response<String>
}