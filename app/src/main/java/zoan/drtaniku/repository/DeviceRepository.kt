package zoan.drtaniku.repository

import retrofit2.HttpException
import zoan.drtaniku.network.ApiService
import zoan.drtaniku.network.Device
import zoan.drtaniku.network.DeviceListResponse
import java.io.IOException

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
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: HttpException) {
            Result.failure(Exception("HTTP error: ${e.code()} - ${e.message()}"))
        } catch (e: Exception) {
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
}