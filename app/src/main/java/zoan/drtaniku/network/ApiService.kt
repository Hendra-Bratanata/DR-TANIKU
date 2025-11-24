package zoan.drtaniku.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import retrofit2.converter.scalars.ScalarsConverterFactory

data class DeviceListResponse(
    val Data_Count: Int,
    val data: List<Device>
)

data class Device(
    val IMEI: String,
    val Lokasi: String,
    val Alamat: String,
    val Status: String,
    val Token: String? = null
)

data class AddDataResponse(
    val success: Boolean,
    val message: String,
    val data_id: String? = null
)

data class UsageInfo(
    val input_tokens: Int,
    val output_tokens: Int,
    val total_tokens: Int,
    val cached_tokens: Int = 0,
    val reasoning_tokens: Int = 0
)

data class PlantAnalysisResponse(
    val output: String,
    val usage: UsageInfo? = null
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
        @Query("lng") longitude: Double,
        @Query("a") analisa: String? = null
    ): Response<String>

    @GET
    suspend fun analyzePlant(
        @Url url: String,
        @Query("suhu") suhu: Double,
        @Query("humi") humi: Double,
        @Query("ph") ph: Double,
        @Query("n") n: Double,
        @Query("p") p: Double,
        @Query("k") k: Double,
        @Query("tanaman") tanaman: String
    ): Response<List<PlantAnalysisResponse>>

    @GET("api/id.php")
    suspend fun updateDeviceToken(
        @Query("api_key") apiKey: String,
        @Query("imei") imei: String,
        @Query("token") token: Long
    ): Response<String>

    companion object {
        private const val BASE_URL = "http://zoan.online/"

        fun getInstance(): ApiService {
            val logging = HttpLoggingInterceptor()
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
        }

        /**
         * Get Retrofit instance specifically for plain text responses
         * Used for updateDeviceToken API that returns plain text
         */
        fun getInstancePlainText(): ApiService {
            val logging = HttpLoggingInterceptor()
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}