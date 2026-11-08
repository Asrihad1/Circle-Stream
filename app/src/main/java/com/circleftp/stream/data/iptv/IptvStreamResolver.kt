package com.circleftp.stream.data.iptv

import android.util.Log
import com.circleftp.stream.data.provider.model.ResolvedStreamModel
import com.circleftp.stream.data.provider.model.StreamFormat
import com.circleftp.stream.data.provider.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * High-performance resolver for IPTV IDN Flussonic streams.
 * Intercepts play.php tokens, bypasses website ads, and constructs
 * direct master HLS playback URLs.
 */
object IptvStreamResolver {
    private const val TAG = "IptvStreamResolver"
    private const val BASE_PORTAL = "http://iptvidn.com"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val IFRAME_REGEX = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val TOKEN_REGEX = Regex("""token=([^&"']+)""", RegexOption.IGNORE_CASE)

    /**
     * Resolves a live channel stream directly into a playable HLS package.
     */
    suspend fun resolveStream(channelId: String): ResolvedStreamModel? = withContext(Dispatchers.IO) {
        runCatching {
            val playUrl = "$BASE_PORTAL/play.php?stream=$channelId"
            val request = Request.Builder()
                .url(playUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_PORTAL/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to load play.php for channel $channelId: HTTP ${response.code}")
                return@runCatching null
            }

            val html = response.body?.string() ?: return@runCatching null
            val iframeMatch = IFRAME_REGEX.find(html) ?: run {
                Log.w(TAG, "No player iframe found in play.php for channel $channelId")
                return@runCatching null
            }

            val iframeSrc = iframeMatch.groupValues[1]
            val tokenMatch = TOKEN_REGEX.find(iframeSrc) ?: run {
                Log.w(TAG, "No token found in iframe for channel $channelId: $iframeSrc")
                return@runCatching null
            }

            val token = tokenMatch.groupValues[1]
            val baseStream = iframeSrc.substringBefore("/embed.html")
            val streamUrl = "$baseStream/index.m3u8?token=$token&remote=no_check_ip"

            val channel = IptvCatalog.getChannelById(channelId)
            val qualityLabel = channel?.resolution?.takeIf { it.isNotBlank() } ?: "1080p Full HD"

            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to iframeSrc
            )

            ResolvedStreamModel(
                streams = listOf(
                    StreamSource(
                        url = streamUrl,
                        quality = qualityLabel,
                        format = StreamFormat.HLS,
                        headers = headers,
                        serverName = "Flussonic Live (BDIX)"
                    )
                ),
                headers = headers
            )
        }.onFailure { e ->
            Log.e(TAG, "Stream resolution error for $channelId: ${e.message}", e)
        }.getOrNull()
    }
}

