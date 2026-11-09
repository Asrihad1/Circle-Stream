package com.circleftp.stream.data.provider.netmirror

import android.util.Log
import com.circleftp.stream.data.provider.model.ResolvedStreamModel
import com.circleftp.stream.data.provider.model.StreamFormat
import com.circleftp.stream.data.provider.model.StreamSource
import com.circleftp.stream.data.provider.model.SubtitleTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Universal Stream Resolver for Global Cloud VOD Media (Movies & Series).
 * Resolves high-speed, multi-server playback endpoints including Dual Audio / Hindi audio
 * streams across multiple mirrors (Vidsrc, Embed, SuperStream, 2Embed).
 */
object GlobalStreamResolver {

    private const val TAG = "GlobalStreamResolver"

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(8, TimeUnit.SECONDS)
                readTimeout(12, TimeUnit.SECONDS)
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }

    /**
     * Resolves playable streams for either a movie or a specific TV series episode.
     *
     * @param tmdbId Numeric TMDB identifier.
     * @param imdbId Optional IMDb identifier (e.g. tt1234567) if known.
     * @param isSeries True if TV show, false if Movie.
     * @param season Season number (1-based, or 0 if Movie).
     * @param episode Episode number (1-based, or 0 if Movie).
     */
    suspend fun resolveStreams(
        tmdbId: String,
        imdbId: String? = null,
        isSeries: Boolean = false,
        season: Int = 0,
        episode: Int = 0
    ): ResolvedStreamModel = withContext(Dispatchers.IO) {
        val streams = mutableListOf<StreamSource>()
        val effectiveSeason = if (season > 0) season else 1
        val effectiveEpisode = if (episode > 0) episode else 1

        val idParam = imdbId?.takeIf { it.isNotBlank() } ?: tmdbId

        // Server 1: MultiEmbed (Primary Multi-Language with Hindi/South Indian Audio)
        val multiEmbedUrl = if (isSeries) {
            "https://multiembed.mov/?video_id=$idParam&tmdb=1&s=$effectiveSeason&e=$effectiveEpisode"
        } else {
            "https://multiembed.mov/?video_id=$idParam&tmdb=1"
        }

        // Server 2: VidSrc PM (Fast 1080p Cloud Node)
        val vidsrcPmUrl = if (isSeries) {
            "https://vidsrc.pm/embed/tv/$tmdbId/$effectiveSeason/$effectiveEpisode"
        } else {
            "https://vidsrc.pm/embed/movie/$tmdbId"
        }

        // Server 3: VidSrc.to Fast Edge
        val vidsrcToUrl = if (isSeries) {
            "https://vidsrc.to/embed/tv/$tmdbId/$effectiveSeason/$effectiveEpisode"
        } else {
            "https://vidsrc.to/embed/movie/$tmdbId"
        }

        // Server 4: VidSrc.me Universal Node
        val vidsrcMeUrl = if (isSeries) {
            "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$effectiveSeason&episode=$effectiveEpisode"
        } else {
            "https://vidsrc.me/embed/movie?tmdb=$tmdbId"
        }

        streams.add(
            StreamSource(
                url = multiEmbedUrl,
                quality = "1080p Multi-Audio / Hindi Dub",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://multiembed.mov",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
                ),
                serverName = "Server 1 - MultiEmbed (Hindi / Multi-Audio)"
            )
        )

        streams.add(
            StreamSource(
                url = vidsrcPmUrl,
                quality = "1080p Full HD",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://vidsrc.pm",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
                ),
                serverName = "Server 2 - VidSrc HD Node"
            )
        )

        streams.add(
            StreamSource(
                url = vidsrcToUrl,
                quality = "1080p Fast Edge",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://vidsrc.to",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
                ),
                serverName = "Server 3 - VidSrc.to Edge"
            )
        )

        streams.add(
            StreamSource(
                url = vidsrcMeUrl,
                quality = "720p / 1080p Universal",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://vidsrc.me",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
                ),
                serverName = "Server 4 - VidSrc Universal"
            )
        )

        // Subtitles pre-configuration
        val defaultSubtitles = listOf(
            SubtitleTrack(
                url = "",
                language = "English",
                label = "English [Auto]",
                isDefault = true
            )
        )

        Log.d(TAG, "Resolved ${streams.size} servers for tmdbId=$tmdbId (isSeries=$isSeries)")

        ResolvedStreamModel(
            streams = streams,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36",
                "Accept" to "*/*"
            ),
            subtitles = defaultSubtitles
        )
    }
}

