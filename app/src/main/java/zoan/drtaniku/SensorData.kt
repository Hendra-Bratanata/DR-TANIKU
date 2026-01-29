package zoan.drtaniku

data class SensorData(
    val timestamp: String,
    var suhu: Double,
    var humi: Double,
    var ec: Double,
    var sl: Double,
    var ph: Double,
    var n: Double,
    var p: Double,
    var k: Double
)
