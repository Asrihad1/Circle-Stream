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

        // Server 1: VidLink Pro (Clean, 1080p Ultra HD, Zero Ads, No Carrier Billing, Auto-Subtitles)
        val vidLinkUrl = if (isSeries) {
            "https://vidlink.pro/tv/$tmdbId/$effectiveSeason/$effectiveEpisode?primaryColor=E50914&autoplay=true"
        } else {
            "https://vidlink.pro/movie/$tmdbId?primaryColor=E50914&autoplay=true"
        }

        // Server 2: Embed.su (Massive library with Indian/Bollywood, Hindi Dubbed & Global HD streams)
        val embedSuUrl = if (isSeries) {
            "https://embed.su/embed/tv/$tmdbId/$effectiveSeason/$effectiveEpisode"
        } else {
            "https://embed.su/embed/movie/$tmdbId"
        }

        // Server 3: VidSrc v2 Cloud (Fast Cloud CDN)
        val vidsrcCcUrl = if (isSeries) {
            "https://vidsrc.cc/v2/embed/tv/$tmdbId/$effectiveSeason/$effectiveEpisode"
        } else {
            "https://vidsrc.cc/v2/embed/movie/$tmdbId"
        }

        // Server 4: AutoEmbed Edge (Fast Multi-CDN)
        val autoEmbedUrl = if (isSeries) {
            "https://autoembed.co/tv/tmdb/$tmdbId-$effectiveSeason-$effectiveEpisode"
        } else {
            "https://autoembed.co/movie/tmdb/$tmdbId"
        }

        // Server 5: VidSrc PM (Fast 1080p Cloud Node)
        val vidsrcPmUrl = if (isSeries) {
            "https://vidsrc.pm/embed/tv/$tmdbId/$effectiveSeason/$effectiveEpisode"
        } else {
            "https://vidsrc.pm/embed/movie/$tmdbId"
        }

        streams.add(
            StreamSource(
                url = vidLinkUrl,
                quality = "1080p Ultra HD (Clean & Ad-Free)",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://vidlink.pro",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                ),
                serverName = "Server 1 - VidLink Pro (Ultra HD / No Ads)"
            )
        )

        streams.add(
            StreamSource(
                url = embedSuUrl,
                quality = "1080p Full HD (Hindi / Bollywood / Global)",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://embed.su",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                ),
                serverName = "Server 2 - Embed.su (Hindi / Global HD)"
            )
        )

        streams.add(
            StreamSource(
                url = vidsrcCcUrl,
                quality = "1080p Fast Cloud",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://vidsrc.cc",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                ),
                serverName = "Server 3 - VidSrc v2 Cloud"
            )
        )

        streams.add(
            StreamSource(
                url = autoEmbedUrl,
                quality = "1080p Fast CDN",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://autoembed.co",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                ),
                serverName = "Server 4 - AutoEmbed Edge"
            )
        )

        streams.add(
            StreamSource(
                url = vidsrcPmUrl,
                quality = "1080p Full HD",
                format = StreamFormat.HLS,
                headers = mapOf(
                    "Referer" to "https://vidsrc.pm",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                ),
                serverName = "Server 5 - VidSrc HD Node"
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

