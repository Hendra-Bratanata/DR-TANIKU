package zoan.drtaniku.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * SessionManager - Manages user login sessions and device information persistence
 *
 * This singleton handles:
 * - Storing and retrieving login session data
 * - Managing device information after QR code login
 * - Session expiration (30 days)
 * - Complete session clearing for logout functionality
 *
 * Architecture: Singleton pattern with SharedPreferences storage
 * Session Duration: 30 days from login time
 * Storage Location: Android SharedPreferences (private mode)
 */
object SessionManager {
    // SharedPreferences configuration
    private const val PREFS_NAME = "session_prefs" // Storage file name

    // Session keys for SharedPreferences
    private const val KEY_IS_LOGGED_IN = "is_logged_in"       // Boolean: User login status
    private const val KEY_DEVICE_IMEI = "device_imei"         // String: Device IMEI number
    private const val KEY_DEVICE_LOCATION = "device_location" // String: Device location name
    private const val KEY_DEVICE_ADDRESS = "device_address"   // String: Device full address
    private const val KEY_DEVICE_STATUS = "device_status"     // String: Device registration status
    private const val KEY_LOGIN_TIME = "login_time"           // Long: Timestamp of login (milliseconds)

    /**
     * Gets SharedPreferences instance for session storage
     * Uses MODE_PRIVATE for security (only this app can access)
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves user login session and device information to SharedPreferences
     *
     * Process:
     * 1. Set login status to true
     * 2. Record current timestamp for session expiration
     * 3. Store device information if available
     * 4. Use apply() for async storage (non-blocking)
     *
     * Called from: QRLoginActivity after successful QR code validation
     * Trigger: Device successfully registered via API validation
     */
    fun saveLoginSession(context: Context, device: zoan.drtaniku.network.Device?) {
        val prefs = getSharedPreferences(context)
        val editor = prefs.edit()

        // Mark user as logged in
        editor.putBoolean(KEY_IS_LOGGED_IN, true)

        // Store login timestamp for expiration checking
        editor.putString(KEY_LOGIN_TIME, System.currentTimeMillis().toString())

        // Store device information if available
        device?.let {
            editor.putString(KEY_DEVICE_IMEI, it.IMEI)
            editor.putString(KEY_DEVICE_LOCATION, it.Lokasi)
            editor.putString(KEY_DEVICE_ADDRESS, it.Alamat)
            editor.putString(KEY_DEVICE_STATUS, it.Status)
        }

        // Apply changes asynchronously
        editor.apply()
    }

    /**
     * Checks if user is currently logged in
     *
     * Logic: Reads the login status flag from SharedPreferences
     * Returns: true if user has active session, false otherwise
     *
     * Used by: SplashActivity for session validation
     * Used by: HomeActivity for access control
     */
    fun isLoggedIn(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Retrieves stored device information from current session
     *
     * Process:
     * 1. Get IMEI from SharedPreferences
     * 2. If IMEI exists, reconstruct Device object
     * 3. Return null if no IMEI (no device stored)
     *
     * Returns: Device object with session data or null
     *
     * Used by: SplashActivity to pass device info to HomeActivity
     */
    fun getDeviceInfo(context: Context): zoan.drtaniku.network.Device? {
        val prefs = getSharedPreferences(context)

        val imei = prefs.getString(KEY_DEVICE_IMEI, null)
        return if (imei != null) {
            // Reconstruct Device object from stored session data
            zoan.drtaniku.network.Device(
                IMEI = imei,
                Lokasi = prefs.getString(KEY_DEVICE_LOCATION, "") ?: "",
                Alamat = prefs.getString(KEY_DEVICE_ADDRESS, "") ?: "",
                Status = prefs.getString(KEY_DEVICE_STATUS, "") ?: ""
            )
        } else {
            null // No device in session
        }
    }

    /**
     * Retrieves login timestamp from session
     *
     * Returns: Login time in milliseconds since epoch
     * Default: 0 if no login time stored
     *
     * Used by: isSessionExpired() for age calculation
     */
    fun getLoginTime(context: Context): Long {
        val prefs = getSharedPreferences(context)
        val timeString = prefs.getString(KEY_LOGIN_TIME, "0")
        return timeString?.toLongOrNull() ?: 0L
    }

    /**
     * Logs out user and clears all session data
     *
     * Process:
     * 1. Call forceClearAllData() for comprehensive session clearing
     * 2. Log logout action for debugging
     *
     * Called from: HomeActivity navigation drawer logout
     * Result: Complete session termination
     */
    fun logout(context: Context) {
        // Clear all session data
        clearAllSessionData(context)
    }

    /**
     * Checks if current session has expired based on timestamp
     *
     * Logic:
     * 1. Get login time from session
     * 2. Calculate age: (current time - login time)
     * 3. Compare against 30 days threshold
     *
     * Session Duration: 30 days (30 * 24 * 60 * 60 * 1000 milliseconds)
     * Returns: true if session is older than 30 days, false otherwise
     *
     * Used by: SplashActivity for automatic session expiration handling
     */
    fun isSessionExpired(context: Context): Boolean {
        val loginTime = getLoginTime(context)
        val currentTime = System.currentTimeMillis()
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000

        return loginTime > 0 && (currentTime - loginTime) > thirtyDaysInMillis
    }

    /**
     * Automatically logs out user if session has expired
     *
     * Process:
     * 1. Check session expiration
     * 2. If expired, call logout()
     * 3. Return expiration status
     *
     * Returns: true if session was expired and logged out, false otherwise
     *
     * Used by: SplashActivity for transparent session expiration
     */
    fun autoLogoutIfExpired(context: Context): Boolean {
        if (isSessionExpired(context)) {
            logout(context)
            return true
        }
        return false
    }

  
    // Clear all session data
    private fun clearAllSessionData(context: Context) {
        val prefs = getSharedPreferences(context)
        prefs.edit().clear().commit()
    }
}