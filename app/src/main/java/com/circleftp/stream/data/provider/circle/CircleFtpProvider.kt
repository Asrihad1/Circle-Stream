package com.circleftp.stream.data.provider.circle

import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.config.ConfigManager
import com.circleftp.stream.data.model.PostSummary
import com.circleftp.stream.data.model.ResolvedPostDetail
import com.circleftp.stream.data.provider.MediaSourceProvider
import com.circleftp.stream.data.provider.model.MediaDetailModel
import com.circleftp.stream.data.provider.model.MediaItemModel
import com.circleftp.stream.data.provider.model.MediaType
import com.circleftp.stream.data.provider.model.ProviderEpisode
import com.circleftp.stream.data.provider.model.ProviderSeason
import com.circleftp.stream.data.provider.model.ResolvedStreamModel
import com.circleftp.stream.data.provider.model.StreamFormat
import com.circleftp.stream.data.provider.model.StreamSource

/**
 * Concrete reference implementation of [MediaSourceProvider] for Circle FTP (Local BDIX).
 * Integrates dynamic domain resolution from [ConfigManager] and returns direct HTTP .mp4/.mkv streams.
 */
class CircleFtpProvider(
    private val configManager: ConfigManager? = null,
    client: CircleFtpApiClient? = null
) : MediaSourceProvider {

    override val providerId: String = "circleftp"
    override val providerName: String = "Circle FTP"
    override val isBdixOnly: Boolean = true

    private fun getBaseUrl(): String {
        val domain = configManager?.getDomain("circleftp", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        return if (domain.endsWith("/api")) domain else "$domain/api"
    }

    private val apiClient: CircleFtpApiClient = client ?: CircleFtpApiClient(getBaseUrl())

    override suspend fun getTrending(): List<MediaItemModel> {
        val homeResponse = apiClient.getHomePage()
        val combined = (homeResponse.latestPost + homeResponse.mostVisitedPosts).distinctBy { it.id }
        return combined.map { it.toMediaItemModel(providerId, isBdixOnly) }
    }

    override suspend fun search(query: String): List<MediaItemModel> {
        val posts = apiClient.searchPosts(query)
        return posts.map { it.toMediaItemModel(providerId, isBdixOnly) }
    }

    private fun parsePostId(mediaId: String): Long {
        val clean = mediaId
            .removePrefix("circleftp_")
            .removePrefix("circle_ftp_")
            .removePrefix("movie_")
            .removePrefix("series_")
            .substringBefore("_")
        return clean.toLongOrNull() ?: 0L
    }

    override suspend fun getDetails(mediaId: String, mediaType: String): MediaDetailModel {
        val postId = parsePostId(mediaId)
        val detail = apiClient.getPostDetail(postId)
            ?: return MediaDetailModel(
                id = mediaId,
                title = "Unavailable",
                providerId = providerId
            )
        return detail.toMediaDetailModel(providerId)
    }

    override suspend fun getStreams(mediaId: String, season: Int, episode: Int): ResolvedStreamModel {
        val postId = parsePostId(mediaId)
        val detail = apiClient.getPostDetail(postId) ?: return ResolvedStreamModel()

        val streamUrl: String? = when {
            detail.isSeries && season > 0 && episode > 0 -> {
                val targetSeason = detail.seasons.getOrNull(season - 1)
                targetSeason?.episodes?.getOrNull(episode - 1)?.streamUrl
            }
            detail.isSeries && detail.seasons.isNotEmpty() -> {
                val firstSeason = detail.seasons.firstOrNull()
                val firstEp = firstSeason?.episodes?.firstOrNull()
                firstEp?.streamUrl
            }
            detail.multiFiles.isNotEmpty() -> {
                val epIndex = if (episode > 0) (episode - 1).coerceIn(0, detail.multiFiles.size - 1) else 0
                detail.multiFiles.getOrNull(epIndex)?.streamUrl
            }
            else -> detail.singleStreamUrl
        }

        if (streamUrl.isNullOrBlank()) {
            return ResolvedStreamModel()
        }

        val format = when {
            streamUrl.contains(".m3u8", ignoreCase = true) -> StreamFormat.HLS
            streamUrl.contains(".mkv", ignoreCase = true) -> StreamFormat.MKV
            else -> StreamFormat.MP4
        }

        return ResolvedStreamModel(
            streams = listOf(
                StreamSource(
                    url = streamUrl,
                    quality = detail.quality ?: "BDIX High-Speed",
                    format = format,
                    serverName = "Circle FTP BDIX Node"
                )
            )
        )
    }

    private fun PostSummary.toMediaItemModel(provider: String, isBdix: Boolean): MediaItemModel {
        val type = if (type.equals("series", ignoreCase = true)) MediaType.SERIES else MediaType.MOVIE
        return MediaItemModel(
            id = id.toString(),
            title = displayTitle,
            posterUrl = getPosterUrl().takeIf { it.isNotBlank() },
            bannerUrl = getBackdropUrl().takeIf { it.isNotBlank() },
            mediaType = type,
            releaseYear = year,
            quality = quality ?: "HD",
            providerId = provider,
            isBdix = isBdix
        )
    }

    private fun ResolvedPostDetail.toMediaDetailModel(provider: String): MediaDetailModel {
        val mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE

        val mappedSeasons = seasons.mapIndexed { sIndex, s ->
            ProviderSeason(
                seasonNumber = sIndex + 1,
                seasonName = s.seasonName,
                episodes = s.episodes.mapIndexed { epIndex, ep ->
                    ProviderEpisode(
                        episodeNumber = epIndex + 1,
                        seasonNumber = sIndex + 1,
                        title = ep.title,
                        episodeId = "${id}_s${sIndex + 1}_ep${epIndex + 1}",
                        directStreamUrl = ep.streamUrl
                    )
                }
            )
        }

        return MediaDetailModel(
            id = id.toString(),
            title = title,
            posterUrl = posterUrl.takeIf { it.isNotBlank() },
            backdropUrl = backdropUrl.takeIf { it.isNotBlank() },
            mediaType = mediaType,
            releaseYear = year,
            quality = quality,
            providerId = provider,
            seasons = mappedSeasons,
            directStreamUrl = singleStreamUrl
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://new.circleftp.net:5000/api"
    }
}
