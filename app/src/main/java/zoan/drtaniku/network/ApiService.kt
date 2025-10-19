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

interface ApiService {
    @GET("api/id")
    suspend fun getDeviceList(
        @Query("api_key") apiKey: String
    ): Response<DeviceListResponse>
}