package com.circleftp.stream.player

import android.net.TrafficStats
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Real-time network speed monitor calculating the download bitrate
 * consumed by the application via [TrafficStats.getUidRxBytes].
 */
object NetworkSpeedMonitor {
    private val _speedText = MutableStateFlow("0 KB/s")
    val speedText: StateFlow<String> = _speedText.asStateFlow()

    private val _bytesPerSecond = MutableStateFlow(0L)
    val bytesPerSecond: StateFlow<Long> = _bytesPerSecond.asStateFlow()

    private var lastRxBytes = 0L
    private var lastTimestamp = 0L

    fun updateSample() {
        val uid = Process.myUid()
        val currentRx = TrafficStats.getUidRxBytes(uid)
        val now = System.currentTimeMillis()

        if (currentRx != TrafficStats.UNSUPPORTED.toLong() && lastRxBytes > 0 && lastTimestamp > 0) {
            val deltaBytes = (currentRx - lastRxBytes).coerceAtLeast(0L)
            val deltaMs = (now - lastTimestamp).coerceAtLeast(1L)
            val bytesSec = (deltaBytes * 1000L) / deltaMs

            _bytesPerSecond.value = bytesSec
            _speedText.value = formatSpeed(bytesSec)
        }

        lastRxBytes = currentRx
        lastTimestamp = now
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(Locale.US, "%d KB/s", bytesPerSec / 1024)
            else -> String.format(Locale.US, "%d B/s", bytesPerSec.coerceAtLeast(0))
        }
    }
}

/**
 * Composable hook providing an auto-updating live network download speed string.
 */
@Composable
fun rememberNetworkSpeed(): State<String> {
    LaunchedEffect(Unit) {
        while (isActive) {
            NetworkSpeedMonitor.updateSample()
            delay(1000L)
        }
    }
    return NetworkSpeedMonitor.speedText.collectAsState()
}

