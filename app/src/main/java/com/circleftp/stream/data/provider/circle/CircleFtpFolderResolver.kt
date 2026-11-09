package com.circleftp.stream.data.provider.circle

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * High-speed resolver for BDIX FTP folder/directory URLs.
 *
 * Some movies and series on Circle FTP have content paths pointing to an h5ai/Apache
 * directory index (ending with `/`) rather than the direct video file.
 * This resolver inspects directory pages, extracts the primary media file (.mkv, .mp4, .ts),
 * and caches results in memory for instantaneous subsequent lookups.
 */
object CircleFtpFolderResolver {

    private const val TAG = "CircleFolderResolver"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val cache = ConcurrentHashMap<String, String>()

    private val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".ts", ".m3u8", ".mov", ".flv")

    private val HREF_PATTERN = Pattern.compile(
        "href=[\"']([^\"']+\\.(?:mkv|mp4|avi|ts|m3u8|mov|flv))[\"']",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Checks if a URL points directly to a known video file format.
     */
    fun hasVideoExtension(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.substringBefore('?').lowercase()
        return VIDEO_EXTENSIONS.any { clean.endsWith(it) }
    }

    /**
     * Resolves a raw FTP URL to its direct media file.
     * If the URL is already a video file, it returns immediately without network overhead.
     */
    suspend fun resolve(rawUrl: String?): String = withContext(Dispatchers.IO) {
        if (rawUrl.isNullOrBlank()) return@withContext ""
        val trimmed = rawUrl.trim()

        // Fast path: Already a video file
        if (hasVideoExtension(trimmed) && !trimmed.endsWith("/")) {
            return@withContext trimmed
        }

        // Cache lookup
        cache[trimmed]?.let { cached ->
            return@withContext cached
        }

        // Directory URL: Fetch HTML index and find video file
        try {
            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val matcher = HREF_PATTERN.matcher(body)
                    val candidates = mutableListOf<String>()

                    while (matcher.find()) {
                        val matched = matcher.group(1) ?: continue
                        // Filter out samples, previews, and trailers if main file is available
                        if (!matched.contains("sample", ignoreCase = true)) {
                            candidates.add(matched)
                        }
                    }

                    if (candidates.isNotEmpty()) {
                        // Pick candidate (prefer largest/cleanest file name)
                        val chosen = candidates.maxByOrNull { it.length } ?: candidates.first()
                        val finalUrl = buildAbsoluteUrl(trimmed, chosen)
                        Log.i(TAG, "Resolved folder '$trimmed' -> '$finalUrl'")
                        cache[trimmed] = finalUrl
                        return@withContext finalUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Directory resolution failed for $trimmed: ${e.message}")
        }

        // Fallback: return original URL
        trimmed
    }

    private fun buildAbsoluteUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return try {
            if (relativeOrAbsolute.startsWith("http://", ignoreCase = true) ||
                relativeOrAbsolute.startsWith("https://", ignoreCase = true)) {
                relativeOrAbsolute
            } else {
                val baseUri = URI(baseUrl)
                baseUri.resolve(relativeOrAbsolute).toString()
            }
        } catch (_: Exception) {
            if (relativeOrAbsolute.startsWith("/")) {
                val schemeHost = baseUrl.substringBefore("/", "").ifBlank {
                    baseUrl.substringBefore("://") + "://" + baseUrl.substringAfter("://").substringBefore("/")
                }
                "$schemeHost$relativeOrAbsolute"
            } else {
                if (baseUrl.endsWith("/")) "$baseUrl$relativeOrAbsolute" else "$baseUrl/$relativeOrAbsolute"
            }
        }
    }
}

