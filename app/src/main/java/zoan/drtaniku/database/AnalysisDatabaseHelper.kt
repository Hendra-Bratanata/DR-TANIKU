package zoan.drtaniku.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import zoan.drtaniku.model.SavedAnalysis

class AnalysisDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "analysis_database"
        private const val DATABASE_VERSION = 1

        private const val TABLE_ANALYSIS = "saved_analysis"
        private const val COLUMN_ID = "id"
        private const val COLUMN_PLANT_NAME = "plant_name"
        private const val COLUMN_ANALYSIS_RESULT = "analysis_result"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_TEMPERATURE = "temperature"
        private const val COLUMN_HUMIDITY = "humidity"
        private const val COLUMN_PH = "ph"
        private const val COLUMN_NITROGEN = "nitrogen"
        private const val COLUMN_PHOSPHORUS = "phosphorus"
        private const val COLUMN_POTASSIUM = "potassium"
        private const val COLUMN_LOCATION = "location"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_ANALYSIS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PLANT_NAME TEXT NOT NULL,
                $COLUMN_ANALYSIS_RESULT TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_TEMPERATURE REAL NOT NULL,
                $COLUMN_HUMIDITY REAL NOT NULL,
                $COLUMN_PH REAL NOT NULL,
                $COLUMN_NITROGEN REAL NOT NULL,
                $COLUMN_PHOSPHORUS REAL NOT NULL,
                $COLUMN_POTASSIUM REAL NOT NULL,
                $COLUMN_LOCATION TEXT
            )
        """.trimIndent()

        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ANALYSIS")
        onCreate(db)
    }

    // Insert new analysis result
    fun insertAnalysis(analysis: SavedAnalysis): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PLANT_NAME, analysis.plantName)
            put(COLUMN_ANALYSIS_RESULT, analysis.analysisResult)
            put(COLUMN_TIMESTAMP, analysis.timestamp)
            put(COLUMN_TEMPERATURE, analysis.temperature)
            put(COLUMN_HUMIDITY, analysis.humidity)
            put(COLUMN_PH, analysis.ph)
            put(COLUMN_NITROGEN, analysis.nitrogen)
            put(COLUMN_PHOSPHORUS, analysis.phosphorus)
            put(COLUMN_POTASSIUM, analysis.potassium)
            put(COLUMN_LOCATION, analysis.location)
        }

        return db.insert(TABLE_ANALYSIS, null, values)
    }

    // Get all saved analyses
    fun getAllAnalyses(): List<SavedAnalysis> {
        val analysesList = mutableListOf<SavedAnalysis>()
        val db = this.readableDatabase
        val cursor: Cursor?

        try {
            cursor = db.query(
                TABLE_ANALYSIS,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val analysis = SavedAnalysis(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        plantName = it.getString(it.getColumnIndexOrThrow(COLUMN_PLANT_NAME)),
                        analysisResult = it.getString(it.getColumnIndexOrThrow(COLUMN_ANALYSIS_RESULT)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        temperature = it.getDouble(it.getColumnIndexOrThrow(COLUMN_TEMPERATURE)),
                        humidity = it.getDouble(it.getColumnIndexOrThrow(COLUMN_HUMIDITY)),
                        ph = it.getDouble(it.getColumnIndexOrThrow(COLUMN_PH)),
                        nitrogen = it.getDouble(it.getColumnIndexOrThrow(COLUMN_NITROGEN)),
                        phosphorus = it.getDouble(it.getColumnIndexOrThrow(COLUMN_PHOSPHORUS)),
                        potassium = it.getDouble(it.getColumnIndexOrThrow(COLUMN_POTASSIUM)),
                        location = it.getString(it.getColumnIndexOrThrow(COLUMN_LOCATION)) ?: ""
                    )
                    analysesList.add(analysis)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return analysesList
    }

    // Get analysis by ID
    fun getAnalysisById(id: Long): SavedAnalysis? {
        val db = this.readableDatabase
        val cursor: Cursor?

        try {
            cursor = db.query(
                TABLE_ANALYSIS,
                null,
                "$COLUMN_ID = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    return SavedAnalysis(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        plantName = it.getString(it.getColumnIndexOrThrow(COLUMN_PLANT_NAME)),
                        analysisResult = it.getString(it.getColumnIndexOrThrow(COLUMN_ANALYSIS_RESULT)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        temperature = it.getDouble(it.getColumnIndexOrThrow(COLUMN_TEMPERATURE)),
                        humidity = it.getDouble(it.getColumnIndexOrThrow(COLUMN_HUMIDITY)),
                        ph = it.getDouble(it.getColumnIndexOrThrow(COLUMN_PH)),
                        nitrogen = it.getDouble(it.getColumnIndexOrThrow(COLUMN_NITROGEN)),
                        phosphorus = it.getDouble(it.getColumnIndexOrThrow(COLUMN_PHOSPHORUS)),
                        potassium = it.getDouble(it.getColumnIndexOrThrow(COLUMN_POTASSIUM)),
                        location = it.getString(it.getColumnIndexOrThrow(COLUMN_LOCATION)) ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    // Delete analysis by ID
    fun deleteAnalysis(id: Long): Int {
        val db = this.writableDatabase
        return db.delete(TABLE_ANALYSIS, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    // Get analyses by plant name
    fun getAnalysesByPlantName(plantName: String): List<SavedAnalysis> {
        val analysesList = mutableListOf<SavedAnalysis>()
        val db = this.readableDatabase
        val cursor: Cursor?

        try {
            cursor = db.query(
                TABLE_ANALYSIS,
                null,
                "$COLUMN_PLANT_NAME LIKE ?",
                arrayOf("%$plantName%"),
                null,
                null,
                "$COLUMN_TIMESTAMP DESC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val analysis = SavedAnalysis(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        plantName = it.getString(it.getColumnIndexOrThrow(COLUMN_PLANT_NAME)),
                        analysisResult = it.getString(it.getColumnIndexOrThrow(COLUMN_ANALYSIS_RESULT)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        temperature = it.getDouble(it.getColumnIndexOrThrow(COLUMN_TEMPERATURE)),
                        humidity = it.getDouble(it.getColumnIndexOrThrow(COLUMN_HUMIDITY)),
                        ph = it.getDouble(it.getColumnIndexOrThrow(COLUMN_PH)),
                        nitrogen = it.getDouble(it.getColumnIndexOrThrow(COLUMN_NITROGEN)),
                        phosphorus = it.getDouble(it.getColumnIndexOrThrow(COLUMN_PHOSPHORUS)),
                        potassium = it.getDouble(it.getColumnIndexOrThrow(COLUMN_POTASSIUM)),
                        location = it.getString(it.getColumnIndexOrThrow(COLUMN_LOCATION)) ?: ""
                    )
                    analysesList.add(analysis)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return analysesList
    }

    // Get analyses count
    fun getAnalysesCount(): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ANALYSIS", null)
        var count = 0

        cursor?.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }

        cursor?.close()
        return count
    }
}