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

/**
 * SimpleWeatherManager - Simplified weather data management
 */
class SimpleWeatherManager(private val context: Context) {

    companion object {
        private const val TAG = "SimpleWeatherManager"
        private const val BASE_URL = "https://api.bmkg.go.id/"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 30L
    }

    private val bmkgService: BMKGService

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "DR-TANIKU-App/1.0")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        bmkgService = retrofit.create(BMKGService::class.java)
    }

    /**
     * Get weather forecast by administrative code (adm4)
     */
    suspend fun getWeatherForecastByAdm4(adm4Code: String): BMKGWeatherResponse? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting BMKG weather API for adm4: $adm4Code")
            val response = bmkgService.getWeatherForecastByAdm4(adm4Code)

            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Successfully retrieved weather forecast for ${response.body()!!.lokasi.desa}")
                return@withContext response.body()
            } else {
                Log.w(TAG, "BMKG API returned error: ${response.code()}")
                return@withContext null
            }

        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching weather data", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather forecast", e)
            return@withContext null
        }
    }

    /**
     * Get weather forecast by coordinates
     */
    suspend fun getWeatherForecastByCoordinates(latitude: Double, longitude: Double): BMKGWeatherResponse? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting BMKG weather API for coordinates: $latitude, $longitude")
            val response = bmkgService.getWeatherForecastByCoordinates(latitude, longitude)

            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Successfully retrieved weather forecast")
                return@withContext response.body()
            } else {
                Log.w(TAG, "BMKG API returned error: ${response.code()}")
                return@withContext null
            }

        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching weather data", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather forecast", e)
            return@withContext null
        }
    }

    /**
     * Get current weather by administrative code for display
     */
    suspend fun getCurrentWeatherByAdm4(adm4Code: String): BMKGWeatherData? = withContext(Dispatchers.IO) {
        try {
            val forecast = getWeatherForecastByAdm4(adm4Code)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

            // Access weather data from the new structure: data[0].cuaca.flatten()
            val todayForecast = forecast?.data?.firstOrNull()?.cuaca?.flatten()?.filter {
                it.localDatetime.startsWith(today)
            }

            return@withContext todayForecast?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current weather by adm4", e)
            return@withContext null
        }
    }

    /**
     * Get current weather for display
     */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): BMKGWeatherData? = withContext(Dispatchers.IO) {
        try {
            val forecast = getWeatherForecastByCoordinates(latitude, longitude)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

            // Access weather data from the new structure: data[0].cuaca.flatten()
            val todayForecast = forecast?.data?.firstOrNull()?.cuaca?.flatten()?.filter {
                it.localDatetime.startsWith(today)
            }

            return@withContext todayForecast?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current weather", e)
            return@withContext null
        }
    }

    /**
     * Get location display name
     */
    fun getLocationDisplayName(location: LocationInfo): String {
        return buildString {
            if (location.desa.isNotEmpty()) {
                append(location.desa)
                append(", ")
            }
            if (location.kecamatan.isNotEmpty()) {
                append(location.kecamatan)
                append(", ")
            }
            if (location.kotkab.isNotEmpty()) {
                append(location.kotkab)
                append(", ")
            }
            if (location.provinsi.isNotEmpty()) {
                append(location.provinsi)
            }
        }
    }
}