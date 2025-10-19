package zoan.drtaniku

data class EnvironmentData(
    val timestamp: String,
    // GPS Data
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    // Sensor Data
    val ambientTemperature: Double,
    val ambientHumidity: Double,
    val lightLevel: Double,
    val compass: Float,
    val pressure: Double,
    // Weather Data
    val weatherCondition: String,
    val weatherDescription: String,
    val locationName: String
) {
    companion object {
        fun getEmpty(): EnvironmentData {
            return EnvironmentData(
                timestamp = "",
                latitude = 0.0,
                longitude = 0.0,
                altitude = 0.0,
                ambientTemperature = 0.0,
                ambientHumidity = 0.0,
                lightLevel = 0.0,
                compass = 0.0f,
                pressure = 0.0,
                weatherCondition = "Unknown",
                weatherDescription = "No data",
                locationName = "Unknown"
            )
        }
    }
}

data class GPSData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val provider: String,
    val timestamp: Long
)

data class DeviceSensorData(
    val temperature: Float,
    val humidity: Float,
    val light: Float,
    val pressure: Float,
    val compass: Float
)

data class WeatherData(
    val condition: String,
    val description: String,
    val temperature: Double,
    val humidity: Int,
    val pressure: Double,
    val location: String
)