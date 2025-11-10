package zoan.drtaniku.repository

import retrofit2.HttpException
import zoan.drtaniku.network.ApiService
import zoan.drtaniku.network.Device
import zoan.drtaniku.network.AddDataResponse
import java.io.IOException
import android.util.Log

class DeviceRepository(private val apiService: ApiService) {

    suspend fun getDeviceList(apiKey: String): Result<List<Device>> {
        return try {
            val response = apiService.getDeviceList(apiKey)
            if (response.isSuccessful) {
                response.body()?.let { deviceResponse ->
                    // Filter only registered devices
                    val registeredDevices = deviceResponse.data.filter {
                        it.Status.equals("Registered", ignoreCase = true)
                    }
                    Result.success(registeredDevices)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: IOException) {
            Log.e("DeviceRepository", "--- NETWORK ERROR ---")
            Log.e("DeviceRepository", "❌ IOException: ${e.message}")
            Log.e("DeviceRepository", "❌ Network stack: ${e.stackTraceToString()}")
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: HttpException) {
            Log.e("DeviceRepository", "--- HTTP EXCEPTION ---")
            Log.e("DeviceRepository", "❌ HttpException code: ${e.code()}")
            Log.e("DeviceRepository", "❌ HttpException message: ${e.message()}")
            Log.e("DeviceRepository", "❌ HTTP stack: ${e.stackTraceToString()}")
            Result.failure(Exception("HTTP error: ${e.code()} - ${e.message()}"))
        } catch (e: Exception) {
            Log.e("DeviceRepository", "--- UNKNOWN ERROR ---")
            Log.e("DeviceRepository", "❌ Unexpected error: ${e.message}")
            Log.e("DeviceRepository", "❌ Error type: ${e::class.java.simpleName}")
            Log.e("DeviceRepository", "❌ Error stack: ${e.stackTraceToString()}")
            Result.failure(Exception("Unknown error: ${e.message}"))
        }
    }

    suspend fun isDeviceRegistered(imei: String): Result<Boolean> {
        return try {
            val result = getDeviceList("50bfbf93-76db-4cc9-9cc9-eaeb6d5a88b4")
            result.fold(
                onSuccess = { devices ->
                    val isRegistered = devices.any { it.IMEI == imei }
                    Result.success(isRegistered)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeviceInfo(imei: String): Result<Device?> {
        return try {
            val result = getDeviceList("50bfbf93-76db-4cc9-9cc9-eaeb6d5a88b4")
            result.fold(
                onSuccess = { devices ->
                    val device = devices.find { it.IMEI == imei }
                    Result.success(device)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send sensor data to server API
     */
    suspend fun sendSensorData(
        imei: String,
        nitrogen: Double,
        phosphorus: Double,
        potassium: Double,
        ph: Double,
        temperature: Double,
        humidity: Double,
        mapsUrl: String,
        latitude: Double,
        longitude: Double
    ): Result<AddDataResponse> {
        return try {
            Log.d("DeviceRepository", "=== SENDING DATA TO SERVER ===")
            Log.d("DeviceRepository", "IMEI: $imei")
            Log.d("DeviceRepository", "Nitrogen: $nitrogen")
            Log.d("DeviceRepository", "Phosphorus: $phosphorus")
            Log.d("DeviceRepository", "Potassium: $potassium")
            Log.d("DeviceRepository", "pH: $ph")
            Log.d("DeviceRepository", "Temperature: $temperature")
            Log.d("DeviceRepository", "Humidity: $humidity")
            Log.d("DeviceRepository", "Maps URL: $mapsUrl")
            Log.d("DeviceRepository", "Latitude: $latitude")
            Log.d("DeviceRepository", "Longitude: $longitude")
            Log.d("DeviceRepository", "=====================================")

            val response = apiService.sendDataToServer(
                imei = imei,
                nitrogen = nitrogen,
                phosphorus = phosphorus,
                potassium = potassium,
                ph = ph,
                temperature = temperature,
                humidity = humidity,
                mapsUrl = mapsUrl,
                latitude = latitude,
                longitude = longitude
            )

            Log.d("DeviceRepository", "--- API RESPONSE RECEIVED ---")
            Log.d("DeviceRepository", "HTTP Status: ${response.code()} ${response.message()}")
            Log.d("DeviceRepository", "Headers: ${response.headers()}")
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    Log.d("DeviceRepository", "--- RESPONSE BODY PROCESSING ---")
                    Log.d("DeviceRepository", "Response type: ${responseBody::class.java.simpleName}")
                    Log.d("DeviceRepository", "Response content: $responseBody")

                    // Handle plain text response from PHP script
                    val responseString = responseBody.trim()
                    Log.d("DeviceRepository", "✅ Plain text response detected: '$responseString'")

                    // Handle simple PHP responses: "Saved", "Fail"
                    val isSuccess = responseString.equals("Saved", ignoreCase = true) ||
                                  responseString.equals("Berhasil", ignoreCase = true) ||
                                  responseString.equals("Success", ignoreCase = true) ||
                                  responseString.contains("berhasil", ignoreCase = true) ||
                                  responseString.contains("success", ignoreCase = true) ||
                                  responseString.contains("disimpan", ignoreCase = true)

                    Log.d("DeviceRepository", "✅ Plain text success detection: $isSuccess")

                    val addDataResponse = AddDataResponse(
                        success = isSuccess,
                        message = if (isSuccess) {
                            when (responseString.lowercase()) {
                                "saved" -> "Data berhasil disimpan ke database"
                                "berhasil" -> "Data berhasil disimpan ke database"
                                "success" -> "Data berhasil disimpan ke database"
                                else -> "Data berhasil disimpan: $responseString"
                            }
                        } else {
                            when (responseString.lowercase()) {
                                "fail" -> "Gagal menyimpan data ke database"
                                else -> "Error: $responseString"
                            }
                        },
                        data_id = null
                    )

                    Log.d("Repository", "✅ Created AddDataResponse: success=${addDataResponse.success}, message=${addDataResponse.message}")
                    Result.success(addDataResponse)
                } else {
                    Log.w("DeviceRepository", "❌ Response body is null")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                // Log error response body for debugging
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Log.e("DeviceRepository", "--- HTTP ERROR RESPONSE ---")
                Log.e("DeviceRepository", "❌ HTTP Status: ${response.code()}")
                Log.e("DeviceRepository", "❌ HTTP Message: ${response.message()}")
                Log.e("DeviceRepository", "❌ Error Body: $errorBody")
                Log.e("DeviceRepository", "❌ Headers: ${response.headers()}")
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()} - $errorBody"))
            }
        } catch (e: IOException) {
            Log.e("DeviceRepository", "--- NETWORK ERROR ---")
            Log.e("DeviceRepository", "❌ IOException: ${e.message}")
            Log.e("DeviceRepository", "❌ Network stack: ${e.stackTraceToString()}")
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: HttpException) {
            Log.e("DeviceRepository", "--- HTTP EXCEPTION ---")
            Log.e("DeviceRepository", "❌ HttpException code: ${e.code()}")
            Log.e("DeviceRepository", "❌ HttpException message: ${e.message()}")
            Log.e("DeviceRepository", "❌ HTTP stack: ${e.stackTraceToString()}")
            Result.failure(Exception("HTTP error: ${e.code()} - ${e.message()}"))
        } catch (e: Exception) {
            Log.e("DeviceRepository", "--- UNKNOWN ERROR ---")
            Log.e("DeviceRepository", "❌ Unexpected error: ${e.message}")
            Log.e("DeviceRepository", "❌ Error type: ${e::class.java.simpleName}")
            Log.e("DeviceRepository", "❌ Error stack: ${e.stackTraceToString()}")
            Result.failure(Exception("Unknown error: ${e.message}"))
        }
    }
}