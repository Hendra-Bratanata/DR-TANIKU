package zoan.drtaniku

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * BMKGService - Retrofit interface for BMKG Weather API
 *
 * Badan Meteorologi, Klimatologi, dan Geofisika Indonesia
 * Provides weather forecast data for Indonesian regions
 *
 * API Documentation: https://api.bmkg.go.id/
 * Free to use with reasonable rate limits
 * No API key required
 */
interface BMKGService {

    /**
     * Get weather forecast by administrative code (adm4)
     *
     * @param adm4 Administrative code level 4 (desa/kelurahan code)
     * @return Response containing weather forecast data
     */
    @GET("publik/prakiraan-cuaca")
    suspend fun getWeatherForecastByAdm4(
        @Query("adm4") adm4: String
    ): Response<BMKGWeatherResponse>

    /**
     * Get weather forecast by coordinates
     *
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @return Response containing weather forecast data
     */
    @GET("publik/prakiraan-cuaca")
    suspend fun getWeatherForecastByCoordinates(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<BMKGWeatherResponse>
}

/**
 * BMKG Weather API Data Models
 */

data class BMKGWeatherResponse(
    val lokasi: LocationInfo,
    val data: List<BMKGWeatherArea>
)

data class BMKGWeatherArea(
    val lokasi: LocationInfo,
    val cuaca: List<List<BMKGWeatherData>>
)

data class LocationInfo(
    @SerializedName("adm1") val adm1: String,         // Provinsi code
    @SerializedName("adm2") val adm2: String,         // Kabupaten/Kota code
    @SerializedName("adm3") val adm3: String,         // Kecamatan code
    @SerializedName("adm4") val adm4: String,         // Desa/Kelurahan code
    val provinsi: String,                            // Provinsi name
    @SerializedName("kotkab") val kotkab: String,     // Kabupaten/Kota name
    val kecamatan: String,                            // Kecamatan name
    val desa: String,                                // Desa/Kelurahan name
    val lon: Double,                                 // Longitude
    val lat: Double,                                 // Latitude
    val timezone: String                              // Timezone
)

data class BMKGWeatherData(
    @SerializedName("datetime") val datetime: String,           // UTC datetime
    val t: Int,                                            // Temperature (°C)
    @SerializedName("tcc") val tcc: Int,                      // Total cloud cover (%)
    @SerializedName("tp") val tp: Double,                     // Precipitation (mm)
    @SerializedName("weather") val weather: Int,              // Weather code
    @SerializedName("weather_desc") val weatherDesc: String,  // Weather description (Indonesian)
    @SerializedName("weather_desc_en") val weatherDescEn: String, // Weather description (English)
    @SerializedName("wd_deg") val wdDeg: Int,               // Wind direction (degrees)
    @SerializedName("wd") val wd: String,                   // Wind direction abbreviation
    @SerializedName("wd_to") val wdTo: String,             // Wind direction to
    @SerializedName("ws") val ws: Double,                  // Wind speed (km/h)
    @SerializedName("hu") val hu: Int,                      // Humidity (%)
    @SerializedName("vs") val vs: Int,                      // Visibility (meters)
    @SerializedName("vs_text") val vsText: String,          // Visibility text
    @SerializedName("time_index") val timeIndex: String,    // Time range index
    @SerializedName("analysis_date") val analysisDate: String, // Forecast date
    val image: String,                                     // Weather icon URL
    @SerializedName("utc_datetime") val utcDatetime: String, // UTC datetime string
    @SerializedName("local_datetime") val localDatetime: String, // Local datetime string
    val source: String                                      // Data source
)

/**
 * Weather forecast summary for easy display
 */
data class WeatherForecastSummary(
    val location: LocationInfo,
    val dailyForecasts: List<DailyWeatherForecast>,
    val overallScore: Int,
    val lastUpdated: Long
)

data class DailyWeatherForecast(
    val date: String,
    val periods: List<BMKGWeatherData>,
    val avgTemperature: Int,
    val maxPrecipitation: Double,
    val avgHumidity: Int,
    val dominantWeather: String,
    val isGoodForAgriculture: Boolean
) {
    fun getAgriculturalRecommendation(): String {
        return periods.firstOrNull()?.getAgriculturalRecommendation() ?: "Data tidak tersedia"
    }
}

/**
 * Extension functions for BMKGWeatherData
 */

/**
 * Get formatted time for display
 */
fun BMKGWeatherData.getFormattedTime(): String {
    return try {
        val parts = localDatetime.split(" ")
        if (parts.size >= 2) {
            val date = parts[0] // YYYY-MM-DD
            val time = parts[1] // HH:mm:ss

            // Convert to Indonesian format
            val timeOnly = time.substring(0, 5) // HH:mm
            "$date $timeOnly"
        } else {
            localDatetime
        }
    } catch (e: Exception) {
        localDatetime
    }
}

/**
 * Get time period description
 */
fun BMKGWeatherData.getTimePeriod(): String {
    return try {
        val hour = localDatetime.split(" ")[1].substring(0, 2).toInt()
        when {
            hour in 6..11 -> "Pagi"
            hour in 12..17 -> "Siang"
            hour in 18..23 -> "Malam"
            else -> "Dini Hari"
        }
    } catch (e: Exception) {
        "Unknown"
    }
}

/**
 * Get agricultural relevance score for weather conditions
 */
fun BMKGWeatherData.getAgriculturalRelevanceScore(): Int {
    var score = 0

    // Temperature scoring
    when {
        t in 20..30 -> score += 30  // Optimal temperature
        t in 15..35 -> score += 20  // Acceptable temperature
        else -> score += 10         // Extreme temperature
    }

    // Humidity scoring
    when {
        hu in 60..80 -> score += 20  // Optimal humidity
        hu in 50..90 -> score += 15  // Acceptable humidity
        else -> score += 10         // Extreme humidity
    }

    // Precipitation scoring
    when {
        tp == 0.0 -> score += 20     // No rain - good for field work
        tp in 0.1..5.0 -> score += 15 // Light rain - acceptable
        tp in 5.1..20.0 -> score += 10 // Moderate rain - limited work
        else -> score += 5          // Heavy rain - field work not recommended
    }

    // Cloud cover scoring
    when {
        tcc <= 30 -> score += 15    // Clear sky - good for field work
        tcc <= 60 -> score += 10    // Partly cloudy
        tcc <= 80 -> score += 5     // Mostly cloudy
        else -> score += 0         // Overcast
    }

    // Wind speed scoring
    when {
        ws <= 10.0 -> score += 15     // Light wind - good for spraying
        ws <= 20.0 -> score += 10     // Moderate wind
        ws <= 30.0 -> score += 5      // Strong wind - careful with spraying
        else -> score += 0         // Very strong wind - not suitable
    }

    return score
}

/**
 * Get agricultural recommendation based on weather
 */
fun BMKGWeatherData.getAgriculturalRecommendation(): String {
    return when {
        tp > 20.0 -> "Hujan lebat - Tidak disarankan aktivitas pertanian lapangan"
        tp > 10.0 -> "Hujan sedang - Pertimbangkan untuk menunda aktivitas lapangan"
        tp > 5.0 -> "Hujan ringan - Aktivitas lapangan dengan persiapan hujan"
        t > 35 -> "Suhu tinggi - Pastikan irigasi cukup dan lindungi tanaman"
        t < 15 -> "Suhu rendah - Pertimbangkan proteksi tanaman dari dingin"
        ws > 25.0 -> "Angin kencang - Hindari penyemprotan pestisida"
        hu > 85 -> "Kelembaban tinggi - Waspada penyakit jamur"
        hu < 40 -> "Kelembaban rendah - Pastikan irigasi cukup"
        tcc > 80 -> "Mendung tebal - Kurang optimal untuk fotosintesis maksimal"
        else -> "Cuaca baik untuk aktivitas pertanian"
    }
}

/**
 * Check if weather is suitable for field work
 */
fun BMKGWeatherData.isSuitableForFieldWork(): Boolean {
    return getAgriculturalRelevanceScore() >= 60 &&
           tp <= 10.0 &&
           ws <= 25.0 &&
           t in 15..35
}

/**
 * Get weather icon URL or fallback
 */
fun BMKGWeatherData.getWeatherIconUrl(): String {
    return if (image.isNotEmpty()) {
        image
    } else {
        "https://api-apps.bmkg.go.id/storage/icon/cuaca/cerah-pm.svg" // Default clear weather icon
    }
}

/**
 * Get simplified weather condition category
 */
fun BMKGWeatherData.getWeatherCategory(): String {
    return when (weather) {
        0, 1 -> "Cerah"      // Clear
        2, 3 -> "Berawan"    // Cloudy
        4, 5 -> "Ujan Ringan" // Light Rain
        6, 7, 8 -> "Hujan"    // Rain
        10, 11 -> "Hujan Lebat" // Heavy Rain
        12, 13 -> "Badai"     // Storm
        14, 15 -> "Kabut"     // Fog
        else -> weatherDesc   // Use description
    }
}