package zoan.drtaniku.model

import java.text.SimpleDateFormat
import java.util.*

data class SavedAnalysis(
    val id: Long = 0,
    val plantName: String,
    val analysisResult: String,
    val timestamp: Long = System.currentTimeMillis(),
    val temperature: Double,
    val humidity: Double,
    val ph: Double,
    val nitrogen: Double,
    val phosphorus: Double,
    val potassium: Double,
    val location: String = ""
) {
    fun getFormattedTimestamp(): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    fun getFormattedDate(): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    fun getFormattedTime(): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}