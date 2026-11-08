package com.circleftp.stream.player

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

data class ActiveDownloadInfo(
    val id: Long,
    val title: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progress: Float,
    val speedText: String,
    val remainingTimeText: String,
    val statusText: String,
    val isRunning: Boolean
)

object CircleDownloadManager {

    private const val PREFS_NAME = "circlestream_downloads"
    private const val KEY_ACTIVE_IDS = "active_download_ids"

    private val speedHistory = mutableMapOf<Long, Pair<Long, Long>>() // id -> (bytes, timestamp)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getActiveDownloadIds(context: Context): Set<Long> {
        val set = getPrefs(context).getStringSet(KEY_ACTIVE_IDS, emptySet()) ?: emptySet()
        return set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun addActiveDownloadId(context: Context, id: Long) {
        val current = getActiveDownloadIds(context).toMutableSet()
        current.add(id)
        getPrefs(context).edit().putStringSet(KEY_ACTIVE_IDS, current.map { it.toString() }.toSet()).apply()
    }

    fun removeActiveDownloadId(context: Context, id: Long) {
        val current = getActiveDownloadIds(context).toMutableSet()
        current.remove(id)
        speedHistory.remove(id)
        getPrefs(context).edit().putStringSet(KEY_ACTIVE_IDS, current.map { it.toString() }.toSet()).apply()
    }

    fun download(context: Context, title: String, rawStreamUrl: String): Long {
        val normalizedUrl = UrlNormalizer.normalize(rawStreamUrl)
        if (normalizedUrl.isBlank()) {
            Toast.makeText(context, "Invalid stream link for download", Toast.LENGTH_SHORT).show()
            return -1L
        }

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                Toast.makeText(context, "DownloadManager service unavailable", Toast.LENGTH_SHORT).show()
                return -1L
            }

            // Derive a clean, readable filename
            val rawFilename = normalizedUrl.substringAfterLast('/').substringBefore('?')
            val extension = if (rawFilename.contains('.')) {
                rawFilename.substringAfterLast('.')
            } else {
                "mkv"
            }

            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
            val finalFilename = if (sanitizedTitle.isNotBlank()) {
                if (sanitizedTitle.endsWith(".$extension", ignoreCase = true)) sanitizedTitle
                else "$sanitizedTitle.$extension"
            } else {
                rawFilename
            }

            val uri = Uri.parse(normalizedUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle(title.ifBlank { finalFilename })
                setDescription("Downloading from Circle FTP (BDIX)")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "CircleStream/$finalFilename")
            }

            val downloadId = downloadManager.enqueue(request)
            addActiveDownloadId(context, downloadId)
            Toast.makeText(context, "Download started: $finalFilename", Toast.LENGTH_SHORT).show()
            return downloadId
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    fun cancelDownload(context: Context, id: Long) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.remove(id)
            removeActiveDownloadId(context, id)
            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    /**
     * Polls active downloads every 1 second and calculates real-time download speed and ETA.
     */
    fun pollActiveDownloads(context: Context): Flow<List<ActiveDownloadInfo>> = flow {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            emit(emptyList())
            return@flow
        }

        while (true) {
            val ids = getActiveDownloadIds(context)
            if (ids.isEmpty()) {
                emit(emptyList())
                delay(1500)
                continue
            }

            val query = DownloadManager.Query().setFilterById(*ids.toLongArray())
            val results = mutableListOf<ActiveDownloadInfo>()
            val now = System.currentTimeMillis()

            try {
                val cursor = downloadManager.query(query)
                if (cursor != null) {
                    val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                    val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = if (titleCol != -1) cursor.getString(titleCol) ?: "Video" else "Video"
                        val bytesDownloaded = if (bytesCol != -1) cursor.getLong(bytesCol) else 0L
                        val totalBytes = if (totalCol != -1) cursor.getLong(totalCol) else -1L
                        val status = if (statusCol != -1) cursor.getInt(statusCol) else -1

                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            // Completed or failed -> remove from active list
                            removeActiveDownloadId(context, id)
                            continue
                        }

                        val progress = if (totalBytes > 0) {
                            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        // Speed and remaining time calculation
                        val lastRecord = speedHistory[id]
                        var speedBytesPerSec = 0L
                        if (lastRecord != null) {
                            val timeDeltaMs = now - lastRecord.second
                            val bytesDelta = bytesDownloaded - lastRecord.first
                            if (timeDeltaMs > 0 && bytesDelta >= 0) {
                                speedBytesPerSec = (bytesDelta * 1000L) / timeDeltaMs
                            }
                        }
                        speedHistory[id] = Pair(bytesDownloaded, now)

                        val speedText = formatSpeed(speedBytesPerSec)
                        val remainingText = if (totalBytes > 0 && speedBytesPerSec > 0) {
                            val remainingBytes = (totalBytes - bytesDownloaded).coerceAtLeast(0L)
                            val remainingSec = remainingBytes / speedBytesPerSec
                            formatEta(remainingSec)
                        } else if (totalBytes > 0) {
                            "Estimating time..."
                        } else {
                            "Connecting..."
                        }

                        val statusText = when (status) {
                            DownloadManager.STATUS_RUNNING -> "Downloading"
                            DownloadManager.STATUS_PAUSED -> "Paused (Waiting for Wi-Fi/Network)"
                            DownloadManager.STATUS_PENDING -> "Pending"
                            else -> "Downloading"
                        }

                        results.add(
                            ActiveDownloadInfo(
                                id = id,
                                title = title,
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes,
                                progress = progress,
                                speedText = speedText,
                                remainingTimeText = remainingText,
                                statusText = statusText,
                                isRunning = status == DownloadManager.STATUS_RUNNING
                            )
                        )
                    }
                    cursor.close()
                }
            } catch (_: Exception) {}

            emit(results)
            delay(1000)
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val mb = bytesPerSec.toDouble() / (1024 * 1024)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mb)
        } else {
            val kb = bytesPerSec.toDouble() / 1024
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "Few seconds left"
        if (seconds < 60) return "${seconds}s left"
        val minutes = seconds / 60
        val secs = seconds % 60
        if (minutes < 60) {
            return if (secs > 0) "${minutes}m ${secs}s left" else "${minutes}m left"
        }
        val hours = minutes / 60
        val remMin = minutes % 60
        return "${hours}h ${remMin}m left"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.2f GB", mb / 1024)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
