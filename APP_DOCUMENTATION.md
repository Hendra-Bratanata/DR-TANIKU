# DR Taniku - Android Application Documentation

## Overview

DR Taniku is an Android application for monitoring agricultural sensor data through USB-connected devices. The app provides real-time monitoring of soil parameters, environmental conditions, and device management capabilities.

## Table of Contents

1. [Application Architecture](#application-architecture)
2. [Features](#features)
3. [User Flow](#user-flow)
4. [Technical Implementation](#technical-implementation)
5. [API Integration](#api-integration)
6. [Data Storage](#data-storage)
7. [Security Considerations](#security-considerations)
8. [Component Breakdown](#component-breakdown)
9. [Build & Deployment](#build--deployment)
10. [Troubleshooting](#troubleshooting)

---

## Application Architecture

### **Architecture Pattern**
- **MVVM (Model-View-ViewModel)** with Repository pattern
- **Clean Architecture** principles for separation of concerns
- **Kotlin Coroutines** for asynchronous operations
- **SharedPreferences** for session persistence

### **Key Components**

#### **1. Session Management (`SessionManager.kt`)**
- **Purpose**: Manages user login sessions and device information
- **Storage**: Android SharedPreferences (private mode)
- **Duration**: 30 days session expiration
- **Functions**:
  - `saveLoginSession()`: Stores device info after successful QR login
  - `isLoggedIn()`: Checks current login status
  - `getDeviceInfo()`: Retrieves stored device information
  - `logout()`: Clears all session data
  - `isSessionExpired()`: Checks 30-day expiration

#### **2. QR Code Authentication (`QRLoginActivity.kt`)**
- **Purpose**: Handles QR code scanning for device authentication
- **Dependencies**: CameraX, ML Kit Barcode Scanning, Retrofit
- **Flow**:
  1. Camera initialization and QR scanning
  2. API validation against zoan.online
  3. Session creation on success
  4. Navigation to main dashboard

#### **3. Main Dashboard (`HomeActivity.kt`)**
- **Purpose**: Real-time sensor monitoring and device control
- **Data Sources**: USB Serial Device, Android Sensors, GPS
- **Refresh Rate**: 5 seconds interval
- **Features**: Sensor cards, environment monitoring, USB communication

#### **4. Splash Screen (`SplashActivity.kt`)**
- **Purpose**: App initialization and session validation
- **Animations**: Progressive loading with percentage display
- **Logic**: Check existing session → Auto-login or show QR login

---

## Features

### **Core Features**

#### 1. **QR Code Authentication**
- **Technology**: CameraX + ML Kit Barcode Scanning
- **Validation**: Real-time API check against device database
- **Feedback**: Success/failure messages with status updates
- **Error Handling**: Empty QR code, network errors, invalid devices

#### 2. **Persistent Session Management**
- **Duration**: 30 days from last login
- **Storage**: Encrypted SharedPreferences
- **Auto-login**: Direct access to dashboard if session valid
- **Security**: Automatic logout on session expiration

#### 3. **Real-time Sensor Monitoring**
- **Parameters**: Temperature, Humidity, pH, N, P, K
- **Update Frequency**: Every 5 seconds
- **Visual Feedback**: Card-based layout with status indicators
- **Data Source**: USB Modbus communication

#### 4. **Environmental Monitoring**
- **GPS**: Location coordinates with high precision
- **Altitude**: Elevation data from GPS
- **Light**: Ambient light levels in Lux
- **Compass**: Magnetic north direction with arrow indicator

#### 5. **USB Device Communication**
- **Protocol**: Modbus RTU over USB Serial
- **Auto-detection**: Scans for compatible devices when app active
- **Connection Management**: Automatic reconnect when device plugged in
- **Error Handling**: Connection timeouts, device disconnection

#### 6. **Navigation & User Interface**
- **Navigation Drawer**: Hamburger menu with logout functionality
- **Toast Messages**: Non-intrusive feedback with 3-second cooldown
- **Responsive Design**: Optimized for various screen sizes
- **Dark Mode**: Tech-focused green color scheme

---

## User Flow

### **Authentication Flow**
```
1. App Launch → Splash Screen
2. Session Check →
   ├─ Valid Session → Home Dashboard
   └─ No Session → QR Login Screen
3. QR Scan → API Validation
4. Device Check →
   ├─ Registered → Save Session → Home Dashboard
   └─ Not Registered → Error Message → Retry
```

### **Dashboard Interaction**
```
Home Dashboard → Real-time Data Display
                ├─ USB Connection Status
                ├─ Sensor Data Cards
                ├─ Environmental Information
                └─ Navigation Menu
                    ├─ Home (Current)
                    ├─ About Device
                    └─ Logout → Clear Session → QR Login
```

### **USB Device Management**
```
Device Detection → USB Permission → Serial Connection → Modbus Communication
                    ↓                           ↓                      ↓
            User Permission Request      Data Stream          Sensor Data
                    ↓                           ↓                      ↓
            Connection Established     Parse Response        Update UI
```

---

## Technical Implementation

### **Dependencies**

#### **Camera & QR Code**
```kotlin
implementation "androidx.camera:camera-camera2:1.3.0"
implementation "androidx.camera:camera-lifecycle:1.3.0"
implementation "androidx.camera:camera-view:1.3.0"
implementation "com.google.mlkit:barcode-scanning:17.2.0"
```

#### **Network & API**
```kotlin
implementation "com.squareup.retrofit2:retrofit:2.9.0"
implementation "com.squareup.retrofit2:converter-gson:2.9.0"
implementation "com.squareup.okhttp3:logging-interceptor:4.11.0"
```

#### **USB Communication**
```kotlin
implementation "com.github.mik3y:usb-serial-for-android:3.4.6"
implementation "com.google.android.material:material:1.10.0"
```

### **Data Models**

#### **Device Information**
```kotlin
data class Device(
    val IMEI: String,           // Device identifier
    val Lokasi: String,         // Location name
    val Alamat: String,         // Full address
    val Status: String          // Registration status
)
```

#### **Sensor Data**
```kotlin
data class SensorData(
    val timestamp: String,      // Reading time
    val suhu: Double,          // Temperature (°C)
    val humi: Double,          // Humidity (%)
    val ph: Double,            // pH level
    val n: Double,             // Nitrogen
    val p: Double,             // Phosphorus
    val k: Double              // Potassium
)
```

### **API Integration**

#### **Device Validation API**
- **Endpoint**: `http://zoan.online/api/id`
- **Method**: GET
- **Parameters**: `api_key=50bfbf93-76db-4cc9-9cc9-eaeb6d5a88b4`
- **Response**: JSON array of registered devices
- **Validation**: Check if QR code IMEI exists in database

#### **Network Configuration**
```xml
<!-- network_security_config.xml -->
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">zoan.online</domain>
</domain-config>
```

### **USB Communication Protocol**

#### **Modbus RTU Frame Structure**
```
[Device Address][Function Code][Data][CRC Low][CRC High]
Example: 01 03 00 00 00 06 C5 C8
```

#### **Register Mapping**
- **Temperature**: Register 40001 (°C × 10)
- **Humidity**: Register 40002 (% × 10)
- **pH**: Register 40003 (pH × 100)
- **Nitrogen**: Register 40004 (N)
- **Phosphorus**: Register 40005 (P)
- **Potassium**: Register 40006 (K)

#### **Communication Flow**
1. Send read request to device
2. Wait for response (1000ms timeout)
3. Parse Modbus frame
4. Validate CRC checksum
5. Extract sensor values
6. Update UI display

---

## Data Storage

### **SharedPreferences Structure**
```kotlin
// Session Data
"is_logged_in" → Boolean
"device_imei" → String
"device_location" → String
"device_address" → String
"device_status" → String
"login_time" → Long (timestamp)
```

### **Data Persistence**
- **Location**: `/data/data/zoan.drtaniku/shared_prefs/session_prefs.xml`
- **Mode**: Private (only this app can access)
- **Encryption**: Android's built-in SharedPreferences encryption
- **Backup**: Included in Android auto-backup

### **Data Lifecycle**
- **Creation**: After successful QR login
- **Access**: Every app launch and session check
- **Update**: On successful re-authentication
- **Deletion**: On logout or session expiration
- **Retention**: 30 days maximum

---

## Security Considerations

### **Authentication Security**
- **QR Code Validation**: Server-side device verification
- **Session Management**: Time-based expiration
- **Secure Storage**: Private SharedPreferences
- **Network Security**: HTTPS for API calls

### **Device Security**
- **USB Permissions**: Android system permission prompts
- **Serial Communication**: Encrypted data transfer
- **API Key Protection**: Hardcoded with limited scope
- **Device Validation**: IMEI-based verification

### **Data Protection**
- **Local Storage**: Encrypted Android storage
- **Network Communication**: HTTP with security config
- **Session Data**: No sensitive information stored
- **User Privacy**: No personal data collection

---

## Component Breakdown

### **Activities**

#### **1. SplashActivity.kt**
- **Purpose**: App initialization and session validation
- **Lifecycle**: 4 seconds with loading animation
- **Key Methods**:
  - `checkPersistentSession()`: Validates existing session
  - `startLoadingProgress()`: Manages loading animation
  - `navigateToHome()`: Direct navigation to dashboard

#### **2. QRLoginActivity.kt**
- **Purpose**: QR code scanning and device authentication
- **Camera**: CameraX with ML Kit integration
- **Key Methods**:
  - `startCamera()`: Initialize camera for QR scanning
  - `handleQRCodeResult()`: Process detected QR codes
  - `checkDeviceWithAPI()`: Validate device via API

#### **3. HomeActivity.kt**
- **Purpose**: Main dashboard with sensor monitoring
- **Data Sources**: USB device, sensors, GPS
- **Key Methods**:
  - `setupAutoRefresh()`: 5-second data refresh cycle
  - `connectToDevice()`: USB connection management
  - `updateSensorDisplay()`: Real-time UI updates

#### **4. AboutActivity.kt**
- **Purpose**: Device information display
- **Content**: Static app and device details

### **Managers & Utilities**

#### **SessionManager.kt**
- **Pattern**: Singleton
- **Purpose**: Centralized session management
- **Key Features**:
  - 30-day expiration
  - Device info persistence
  - Secure logout functionality

#### **Toast Management**
- **Cooldown**: 3 seconds between messages
- **Context**: Prevents duplicate notifications
- **Implementation**: Timestamp-based filtering

---

## Build & Deployment

### **Gradle Configuration**
```kotlin
// app/build.gradle.kts
android {
    compileSdk 34
    defaultConfig {
        applicationId "zoan.drtaniku"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
    }
}
```

### **Build Commands**
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install debug APK
./gradlew installDebug
```

### **Signing Configuration**
- **Debug**: Default debug keystore
- **Release**: Production keystore required
- **SHA-1**: Required for Google Play Console

### **Permissions Required**
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.USB_PERMISSION" />
```

---

## Troubleshooting

### **Common Issues & Solutions**

#### **1. Camera Permission Issues**
- **Problem**: Camera not starting
- **Solution**: Check AndroidManifest permissions, handle permission request callback
- **Code**: `onRequestPermissionsResult()` implementation

#### **2. USB Connection Problems**
- **Problem**: Device not detected
- **Solution**: Check USB device filter, verify device compatibility
- **Debug**: Use `adb devices` to confirm device connection

#### **3. API Connection Issues**
- **Problem**: Network errors when validating QR codes
- **Solution**: Check network security config, verify API endpoint
- **Debug**: Check `network_security_config.xml`

#### **4. Session Persistence Issues**
- **Problem**: Login not persisting after app restart
- **Solution**: Verify SharedPreferences storage, check session expiration logic
- **Debug**: Use `SessionManager.debugSessionStatus()`

#### **5. Toast Message Issues**
- **Problem**: Duplicate or missing toast messages
- **Solution**: Check cooldown mechanism, verify toast duration settings
- **Debug**: Monitor toast timestamps and cooldown logic

### **Debug Tools**

#### **Logging Tags**
- `SessionManager`: Session management operations
- `QR_SCAN`: QR code detection and validation
- `HomeActivity`: USB communication and sensor data
- `SplashActivity`: Session validation and app initialization

#### **ADB Commands**
```bash
# View app logs
adb logcat -s SessionManager QR_SCAN HomeActivity SplashActivity

# Clear app data
adb shell pm clear zoan.drtaniku

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Future Enhancements

### **Planned Features**
1. **Offline Mode**: Local database for sensor data when offline
2. **Data Export**: CSV/Excel export functionality
3. **Historical Data**: Graphs and trends for sensor readings
4. **Multiple Devices**: Support for simultaneous device connections
5. **Push Notifications**: Alerts for abnormal sensor values

### **Technical Improvements**
1. **Architecture Upgrade**: MVVM with Jetpack Compose
2. **Database Integration**: Room database for local storage
3. **Background Sync**: WorkManager for periodic data sync
4. **Security Enhancement**: Biometric authentication
5. **Performance Optimization**: Memory and CPU usage optimization

---

## Contact & Support

### **Development Team**
- **Application**: DR Taniku Agricultural Monitor
- **Package**: zoan.drtaniku
- **Version**: 1.0.0
- **Platform**: Android 5.0+ (API 21+)

### **API Documentation**
- **Base URL**: http://zoan.online
- **API Key**: 50bfbf93-76db-4cc9-9cc9-eaeb6d5a88b4
- **Endpoint**: /api/id
- **Method**: GET

### **Device Compatibility**
- **USB Standards**: USB 2.0, USB Serial (CDC, FTDI, CH340)
- **Baud Rate**: 9600 (configurable)
- **Data Bits**: 8
- **Stop Bits**: 1
- **Parity**: None

---

*Last Updated: October 19, 2025*
*Version: 1.0.0*