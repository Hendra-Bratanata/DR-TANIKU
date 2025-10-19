# QR Code dengan SHA512 Encryption - DR Taniku

## 🎯 Overview

Implementasi QR code dengan SHA512 encryption untuk aplikasi DR Taniku yang memvalidasi device sensor melalui API `http://zoan.online/api/id`.

## 🔐 Fitur Keamanan

1. **SHA512 Encryption**: QR data dienkripsi dengan SHA512 untuk keamanan
2. **AES Encryption**: Payload data dienkripsi dengan AES-256
3. **API Validation**: Validasi device terdaftar di server
4. **Checksum**: Validasi integritas data QR code
5. **Backward Compatibility**: Support IMEI format lama

## 📱 Cara Kerja

### 1. Flow Login dengan QR Code

```
Splash Screen → QR Login Screen → Scan QR Code →
Decrypt & Validate → API Check →
[Registered] → Home Activity
[Unregistered] → Error Message
```

### 2. QR Code Format

#### **Format Terenkripsi (Recommended)**:
```
DRTANIKU:{checksum}:{encrypted_payload}
```

**Contoh**:
```
DRTANIKU:a1b2c3d4:U2FsdGVkX1+vE8y7K5mN3pQ2rT4w6X8zA1b3c5d7f9...
```

#### **Format IMEI (Backward Compatibility)**:
```
866646052471200
```

### 3. Struktur Data Enkripsi

```json
{
  "imei": "866646052471200",
  "info": "Portable NPK with Carbon series + GPS",
  "timestamp": 1697586300000,
  "app": "DR_TANIKU",
  "version": "1.0"
}
```

## 🔧 Implementation Details

### 1. SecurityUtils Class

**Fungsi Utama**:
- `generateSHA512(input: String)`: Generate SHA512 hash
- `generateQRCodeData()`: Buat QR data terenkripsi
- `parseQRCodeData()`: Parse QR code
- `decryptAndValidateQRData()`: Dekripsi dan validasi

### 2. API Integration

**Endpoint**: `http://zoan.online/api/id?api_key={API_KEY}`

**Response Format**:
```json
{
  "Data_Count": 182,
  "data": [
    {
      "IMEI": "866646052471200",
      "Lokasi": "mobile iot",
      "Alamat": "Portable NPK with Carbon series + GPS",
      "Status": "Registered"
    }
  ]
}
```

### 3. QR Code Generator

**Generate QR Code untuk Testing**:
```kotlin
// Generate QR terenkripsi
val qrData = SecurityUtils.generateQRCodeData(
    context,
    "866646052471200",
    "Portable NPK with GPS"
)

// Generate QR bitmap
val bitmap = QRCodeGenerator.generateQRCode(context, imei, info)
```

## 🧪 Testing

### 1. QR Code Terdaftar (Valid)

**IMEI Terdaftar**:
- `866612102571359`
- `866612102571360`
- `866646052470001`
- `866646052470088`
- `866646052471200`
- `866646052471324`

**Expected Result**: ✅ Login berhasil → Home Activity

### 2. QR Code Tidak Terdaftar (Invalid)

**IMEI Tidak Terdaftar**:
- `866646052470009`
- `123456789012345`
- `999999999999999`

**Expected Result**: ❌ "Sensor belum terdaftar"

### 3. Testing Commands

#### **Generate Sample QR Codes**:
```kotlin
val sampleQRCodes = QRCodeGenerator.generateSampleQRCodes(context)
sampleQRCodes.forEach { (imei, qrData) ->
    println("IMEI: $imei")
    println("QR Data: $qrData")
    println("---")
}
```

#### **Manual Testing**:
1. Generate QR code dengan IMEI terdaftar
2. Scan dengan aplikasi
3. Harus masuk ke Home Activity

#### **API Testing**:
```bash
curl "http://zoan.online/api/id?api_key=50bfbf93-76db-4cc9-9cc9-eaeb6d5a88b4"
```

## 📋 Device Status Check

### ✅ Registered Devices
```kotlin
val registeredDevices = listOf(
    "866612102571359" to "Portable NPK Carbon series",
    "866646052471200" to "Portable NPK with GPS",
    "866646052470001" to "Karawang Sensor Station"
)
```

### ❌ Unregistered Devices
```kotlin
val unregisteredDevices = listOf(
    "866646052470009" to "Test Device",
    "123456789012345" to "Invalid IMEI"
)
```

## 🔄 Error Handling

### 1. Network Error
```
"Gagal memvalidasi device: Network error"
```

### 2. QR Code Invalid
```
"QR code tidak valid atau terenkripsi salah"
```

### 3. Device Not Registered
```
"Sensor dengan IMEI {imei} belum terdaftar di sistem"
```

### 4. Format Tidak Dikenali
```
"Format QR code tidak dikenali"
```

## 🛡️ Security Features

1. **SHA512 Hashing**: Untuk checksum dan key derivation
2. **AES-256 Encryption**: Untuk payload encryption
3. **SecureRandom**: Untuk IV generation
4. **Timestamp Validation**: Untuk prevent replay attacks
5. **API Key**: Untuk server authentication

## 📱 User Experience

### Success Flow:
1. Scan QR code
2. "Memproses QR code..."
3. "Memvalidasi device..."
4. "Device terdaftar! Login berhasil..."
5. Navigate to Home Activity

### Error Flow:
1. Scan invalid QR code
2. "QR code tidak valid. Coba lagi."
3. Back to scanning state

### Device Not Registered Flow:
1. Scan unregistered QR code
2. "Sensor belum terdaftar"
3. Show detailed error message
4. Back to scanning state

## 🚀 Best Practices

1. **Always validate checksum** before decryption
2. **Use secure storage** for secret keys
3. **Implement timeout** for API calls
4. **Cache device info** for offline scenarios
5. **Log security events** for audit trail

## 🔍 Debugging

### Enable Logging:
```kotlin
// In QRLoginActivity
private fun debugQRCode(qrCode: String) {
    Log.d("QR_LOGIN", "QR Code: $qrCode")

    val qrData = SecurityUtils.parseQRCodeData(this, qrCode)
    Log.d("QR_LOGIN", "Parsed: $qrData")

    qrData?.let {
        val deviceInfo = SecurityUtils.decryptAndValidateQRData(it)
        Log.d("QR_LOGIN", "Decrypted: $deviceInfo")
    }
}
```

### Check Network Logs:
OkHttpClient logging interceptor akan menampilkan:
- Request URL
- Request headers
- Response body
- Response status

## 📊 Monitoring

### Success Metrics:
- QR scan success rate
- API response time
- Device validation success rate

### Error Metrics:
- Invalid QR code scans
- Network failures
- Decryption failures
- API errors

---

*Implementasi ini menyediakan sistem QR code yang aman dengan SHA512 encryption dan validasi API real-time untuk aplikasi DR Taniku.*