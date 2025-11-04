package zoan.drtaniku

import android.content.Context
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * DataSaveManager - Manages saving and loading sensor data to internal storage
 *
 * Features:
 * - Save complete sensor snapshot to internal storage
 * - Load saved data from internal storage
 * - Manage list of recent saves (max 10)
 * - Generate unique filenames with timestamps
 * - Format data as readable text files
 */
class DataSaveManager(private val context: Context) {

    companion object {
        private const val TAG = "DataSaveManager"
        private const val SAVE_DIR = "sensor_data"
        private const val FILE_EXTENSION = ".txt"
        private const val MAX_RECENT_SAVES = 10
    }

    data class SavedDataInfo(
        val filename: String,
        val timestamp: String,
        val filePath: String
    )

    private val saveDir = File(context.filesDir, SAVE_DIR)

    init {
        // Create save directory if it doesn't exist
        if (!saveDir.exists()) {
            saveDir.mkdirs()
            Log.d(TAG, "Created save directory: ${saveDir.absolutePath}")
        }
    }

    /**
     * Save all current sensor and environment data to internal storage
     */
    fun saveData(
        sensorData: SensorData,
        gpsCoordinates: String,
        altitude: String,
        lightLevel: String,
        compass: String
    ): SavedDataInfo? {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val filename = "sensor_data_$timestamp$FILE_EXTENSION"
            val file = File(saveDir, filename)

            val content = generateDataContent(sensorData, gpsCoordinates, altitude, lightLevel, compass, timestamp)

            FileWriter(file).use { writer ->
                writer.write(content)
            }

            Log.d(TAG, "Data saved successfully to: ${file.absolutePath}")

            return SavedDataInfo(
                filename = filename,
                timestamp = timestamp,
                filePath = file.absolutePath
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error saving data", e)
            return null
        }
    }

    /**
     * Generate formatted content for saved data file
     */
    private fun generateDataContent(
        sensorData: SensorData,
        gpsCoordinates: String,
        altitude: String,
        lightLevel: String,
        compass: String,
        timestamp: String
    ): String {
        return buildString {
            appendLine("=== DR TANIKU SENSOR DATA ===")
            appendLine("Saved: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("--- SENSOR DATA ---")
            appendLine("Timestamp: ${sensorData.timestamp}")
            appendLine("Temperature: ${sensorData.suhu}°C")
            appendLine("Humidity: ${sensorData.humi}%")
            appendLine("pH Level: ${sensorData.ph}")
            appendLine("Nitrogen (N): ${sensorData.n}")
            appendLine("Phosphorus (P): ${sensorData.p}")
            appendLine("Potassium (K): ${sensorData.k}")
            appendLine()
            appendLine("--- ENVIRONMENTAL DATA ---")
            appendLine("GPS Coordinates: $gpsCoordinates")
            appendLine("Altitude: $altitude")
            appendLine("Light Level: $lightLevel")
            appendLine("Compass: $compass")
            appendLine()
            appendLine("=== END OF DATA ===")
        }
    }

    /**
     * Get list of recent saved data (max 10)
     */
    fun getRecentSaves(): List<SavedDataInfo> {
        return try {
            saveDir.listFiles { file -> file.extension == "txt" }
                ?.sortedByDescending { it.lastModified() }
                ?.take(MAX_RECENT_SAVES)
                ?.map { file ->
                    SavedDataInfo(
                        filename = file.name,
                        timestamp = extractTimestampFromFilename(file.name),
                        filePath = file.absolutePath
                    )
                }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent saves", e)
            emptyList()
        }
    }

    /**
     * Get all saved data
     */
    fun getAllSaves(): List<SavedDataInfo> {
        return try {
            saveDir.listFiles { file -> file.extension == "txt" }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    SavedDataInfo(
                        filename = file.name,
                        timestamp = extractTimestampFromFilename(file.name),
                        filePath = file.absolutePath
                    )
                }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all saves", e)
            emptyList()
        }
    }

    /**
     * Load specific saved data file content
     */
    fun loadSavedData(filename: String): String? {
        return try {
            val file = File(saveDir, filename)
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved data: $filename", e)
            null
        }
    }

    /**
     * Delete specific saved data file
     */
    fun deleteSavedData(filename: String): Boolean {
        return try {
            val file = File(saveDir, filename)
            val deleted = file.delete()
            Log.d(TAG, "File deleted: $filename, Success: $deleted")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting saved data: $filename", e)
            false
        }
    }

    /**
     * Extract timestamp from filename
     */
    private fun extractTimestampFromFilename(filename: String): String {
        return try {
            val timestampStr = filename.removePrefix("sensor_data_").removeSuffix(FILE_EXTENSION)
            val inputFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(timestampStr)
            date?.let { outputFormat.format(it) } ?: timestampStr
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing timestamp from filename: $filename", e)
            filename
        }
    }

    /**
     * Get total number of saved files
     */
    fun getTotalSavesCount(): Int {
        return try {
            saveDir.listFiles { file -> file.extension == "txt" }?.size ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting saves count", e)
            0
        }
    }
}