package com.circleftp.stream.data.download

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadedVideo(
    val file: File,
    val title: String,
    val formattedSize: String,
    val formattedDate: String,
    val path: String
)

object DownloadRepository {

    fun getDownloadedVideos(context: Context): List<DownloadedVideo> {
        val results = mutableListOf<DownloadedVideo>()
        val dirsToCheck = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CircleStream"),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        val seenPaths = mutableSetOf<String>()
        val mediaExtensions = setOf("mp4", "mkv", "webm", "avi", "ts", "mov")

        for (dir in dirsToCheck) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (f in files) {
                if (f.isFile && f.extension.lowercase() in mediaExtensions) {
                    if (seenPaths.add(f.absolutePath)) {
                        val cleanTitle = f.nameWithoutExtension
                            .replace("_", " ")
                            .replace(".", " ")
                            .trim()

                        results.add(
                            DownloadedVideo(
                                file = f,
                                title = cleanTitle,
                                formattedSize = formatFileSize(f.length()),
                                formattedDate = formatDate(f.lastModified()),
                                path = f.absolutePath
                            )
                        )
                    }
                }
            }
        }

        return results.sortedByDescending { it.file.lastModified() }
    }

    fun deleteVideo(video: DownloadedVideo): Boolean {
        return try {
            video.file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.2f GB", mb / 1024)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (_: Exception) {
            ""
        }
    }
}

