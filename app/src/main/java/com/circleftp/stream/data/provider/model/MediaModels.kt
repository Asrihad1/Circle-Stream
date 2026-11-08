package com.circleftp.stream.data.provider.model

import kotlinx.serialization.Serializable

/**
 * Common media type constants used across all aggregator streaming providers.
 */
object MediaType {
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val ANIME = "anime"
    const val LIVE_STREAM = "live_stream"
    const val LIVE = "live"
}

/**
 * Supported streaming manifest and direct video container formats.
 */
@Serializable
enum class StreamFormat {
    HLS,   // .m3u8
    MP4,   // progressive .mp4
    DASH,  // .mpd
    MKV,   // .mkv
    OTHER
}

/**
 * Lightweight media item representation for grids, carousels, and search results.
 */
@Serializable
data class MediaItemModel(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val mediaType: String = MediaType.MOVIE,
    val releaseYear: String? = null,
    val rating: String? = null,
    val quality: String? = null,
    val providerId: String,
    val isBdix: Boolean = false,
    val description: String? = null
) {
    val isSeries: Boolean
        get() = mediaType.equals(MediaType.SERIES, ignoreCase = true)
}

/**
 * Detailed metadata representation for a selected Movie or Series.
 */
@Serializable
data class MediaDetailModel(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val description: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val mediaType: String = MediaType.MOVIE,
    val releaseYear: String? = null,
    val quality: String? = null,
    val genres: List<String> = emptyList(),
    val rating: String? = null,
    val duration: String? = null,
    val status: String? = null,
    val providerId: String,
    val seasons: List<ProviderSeason> = emptyList(),
    val directStreamUrl: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val actors: List<String> = emptyList(),
    val director: String? = null
) {
    val isSeries: Boolean
        get() = mediaType.equals(MediaType.SERIES, ignoreCase = true) || seasons.isNotEmpty()
}

/**
 * Represents a television or anime season containing episodes.
 */
@Serializable
data class ProviderSeason(
    val seasonNumber: Int,
    val seasonName: String = "Season $seasonNumber",
    val episodes: List<ProviderEpisode> = emptyList()
)

/**
 * Represents a single streamable episode within a season.
 */
@Serializable
data class ProviderEpisode(
    val episodeNumber: Int,
    val seasonNumber: Int = 1,
    val title: String = "Episode $episodeNumber",
    val episodeId: String = episodeNumber.toString(),
    val thumbnail: String? = null,
    val releaseDate: String? = null,
    val directStreamUrl: String? = null
)

/**
 * Represents an individual video stream endpoint with quality, format, and HTTP headers.
 */
@Serializable
data class StreamSource(
    val url: String,
    val quality: String? = null,
    val format: StreamFormat = StreamFormat.HLS,
    val headers: Map<String, String> = emptyMap(),
    val serverName: String? = null
) {
    val isM3u8: Boolean
        get() = format == StreamFormat.HLS || url.contains(".m3u8", ignoreCase = true)

    val isMp4: Boolean
        get() = format == StreamFormat.MP4 || url.contains(".mp4", ignoreCase = true)
}

/**
 * Represents an external subtitle track (WebVTT, SRT).
 */
@Serializable
data class SubtitleTrack(
    val url: String,
    val language: String = "English",
    val label: String = language,
    val format: String = "vtt",
    val isDefault: Boolean = false
)

/**
 * Represents an alternate audio track (e.g. Dubbed audio).
 */
@Serializable
data class AudioTrack(
    val url: String? = null,
    val language: String = "English",
    val label: String = language
)

/**
 * Optional DRM configuration (ClearKey, Widevine) if required by upstream provider.
 */
@Serializable
data class DrmConfig(
    val type: String = "widevine",
    val licenseUrl: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Resolved streaming package containing direct playable stream URLs (.mp4, .m3u8),
 * required HTTP headers (Referer, Origin, User-Agent), and subtitles.
 */
@Serializable
data class ResolvedStreamModel(
    val streams: List<StreamSource> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val drm: DrmConfig? = null
) {
    /**
     * Primary playback URL (highest quality or first available server).
     */
    val primaryStreamUrl: String?
        get() = streams.firstOrNull()?.url

    /**
     * Merges global provider headers with stream-specific header overrides.
     */
    fun getEffectiveHeaders(stream: StreamSource): Map<String, String> {
        return headers + stream.headers
    }
}
