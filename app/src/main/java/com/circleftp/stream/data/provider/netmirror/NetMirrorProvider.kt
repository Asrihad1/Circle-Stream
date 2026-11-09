package com.circleftp.stream.data.provider.netmirror

import com.circleftp.stream.data.config.ConfigManager
import com.circleftp.stream.data.provider.MediaSourceProvider
import com.circleftp.stream.data.provider.model.MediaDetailModel
import com.circleftp.stream.data.provider.model.MediaItemModel
import com.circleftp.stream.data.provider.model.MediaType
import com.circleftp.stream.data.provider.model.ResolvedStreamModel
import com.circleftp.stream.data.provider.model.StreamFormat
import com.circleftp.stream.data.provider.model.StreamSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * NetMirror Global Cloud Media Streaming Provider.
 * Allows global internet users on any Wi-Fi or mobile network to search and stream movies/shows.
 */
class NetMirrorProvider(
    private val configManager: ConfigManager? = null
) : MediaSourceProvider {

    override val providerId: String = "netmirror"
    override val providerName: String = "NetMirror (Global)"
    override val isBdixOnly: Boolean = false

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(20, TimeUnit.SECONDS)
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }

    private fun getBaseUrl(): String {
        return configManager?.getDomain("netmirror", DEFAULT_NETMIRROR_URL) ?: DEFAULT_NETMIRROR_URL
    }

    override suspend fun getTrending(): List<MediaItemModel> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/api/trending"
            val response = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8)")
            }.bodyAsText()

            parseMediaList(response)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String): List<MediaItemModel> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "${getBaseUrl()}/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val response = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8)")
            }.bodyAsText()

            parseMediaList(response)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getDetails(mediaId: String, mediaType: String): MediaDetailModel = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/api/details?id=${java.net.URLEncoder.encode(mediaId, "UTF-8")}"
            val response = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8)")
            }.bodyAsText()

            val root = json.parseToJsonElement(response).jsonObject
            val title = root["title"]?.jsonPrimitive?.content ?: mediaId
            val desc = root["description"]?.jsonPrimitive?.content ?: "Stream available via NetMirror Global Edge."
            val poster = root["poster"]?.jsonPrimitive?.content
            val backdrop = root["backdrop"]?.jsonPrimitive?.content
            val quality = root["quality"]?.jsonPrimitive?.content ?: "1080p HD"

            MediaDetailModel(
                id = mediaId,
                title = title,
                description = desc,
                posterUrl = poster,
                backdropUrl = backdrop,
                quality = quality,
                mediaType = MediaType.MOVIE,
                providerId = providerId
            )
        } catch (_: Exception) {
            MediaDetailModel(
                id = mediaId,
                title = mediaId,
                description = "NetMirror Global Mirror Stream",
                mediaType = MediaType.MOVIE,
                providerId = providerId
            )
        }
    }

    override suspend fun getStreams(mediaId: String, season: Int, episode: Int): ResolvedStreamModel = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/api/resolve?id=${java.net.URLEncoder.encode(mediaId, "UTF-8")}&s=$season&e=$episode"
            val response = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8)")
            }.bodyAsText()

            val root = json.parseToJsonElement(response).jsonObject
            val streamList = mutableListOf<StreamSource>()
            
            root["streams"]?.jsonArray?.forEach { element ->
                val obj = element.jsonObject
                val streamUrl = obj["url"]?.jsonPrimitive?.content
                if (!streamUrl.isNullOrBlank()) {
                    val quality = obj["quality"]?.jsonPrimitive?.content ?: "1080p"
                    val format = if (streamUrl.contains(".m3u8", ignoreCase = true)) StreamFormat.HLS else StreamFormat.MP4
                    val headersMap = mutableMapOf<String, String>()
                    obj["headers"]?.jsonObject?.forEach { (k, v) ->
                        v.jsonPrimitive.content.let { headersMap[k] = it }
                    }
                    if (!headersMap.containsKey("Referer")) {
                        headersMap["Referer"] = getBaseUrl()
                    }
                    streamList.add(
                        StreamSource(
                            url = streamUrl,
                            quality = quality,
                            format = format,
                            headers = headersMap,
                            serverName = "NetMirror Cloud CDN"
                        )
                    )
                }
            }

            val directUrl = root["url"]?.jsonPrimitive?.content
            if (streamList.isEmpty() && !directUrl.isNullOrBlank()) {
                val format = if (directUrl.contains(".m3u8", ignoreCase = true)) StreamFormat.HLS else StreamFormat.MP4
                streamList.add(
                    StreamSource(
                        url = directUrl,
                        quality = "1080p HD",
                        format = format,
                        headers = mapOf("Referer" to getBaseUrl()),
                        serverName = "NetMirror Global Node"
                    )
                )
            }

            ResolvedStreamModel(
                streams = streamList,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8)",
                    "Referer" to getBaseUrl()
                )
            )
        } catch (_: Exception) {
            ResolvedStreamModel()
        }
    }

    private fun parseMediaList(jsonStr: String): List<MediaItemModel> {
        val list = mutableListOf<MediaItemModel>()
        try {
            val root = json.parseToJsonElement(jsonStr)
            val items = if (root is kotlinx.serialization.json.JsonArray) root else root.jsonObject["items"]?.jsonArray ?: return emptyList()
            for (element in items) {
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                val title = obj["title"]?.jsonPrimitive?.content ?: continue
                val poster = obj["poster"]?.jsonPrimitive?.content
                val banner = obj["backdrop"]?.jsonPrimitive?.content ?: poster
                val quality = obj["quality"]?.jsonPrimitive?.content ?: "1080p"

                list.add(
                    MediaItemModel(
                        id = id,
                        title = title,
                        posterUrl = poster,
                        bannerUrl = banner,
                        quality = quality,
                        providerId = providerId,
                        isBdix = false,
                        description = "Global Internet Cloud Stream"
                    )
                )
            }
        } catch (_: Exception) {
            // lenient fallback
        }
        return list
    }

    companion object {
        const val DEFAULT_NETMIRROR_URL = "https://netmirror.app"
    }
}

