package zoan.drtaniku

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TxResponseData(
    val timestamp: String,
    val slaveId: Int,
    val functionCode: Int,
    val startAddress: Int,
    val quantity: Int,
    val requestHex: String,
    val responseHex: String,
    val responseBytes: ByteArray,
    val responseTimeMs: Int,
    val status: String
) : Parcelable
