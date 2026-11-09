package com.circleftp.stream.data.provider.netmirror

import android.util.Log
import com.circleftp.stream.data.config.ConfigManager
import com.circleftp.stream.data.provider.MediaSourceProvider
import com.circleftp.stream.data.provider.model.MediaDetailModel
import com.circleftp.stream.data.provider.model.MediaItemModel
import com.circleftp.stream.data.provider.model.MediaType
import com.circleftp.stream.data.provider.model.ProviderEpisode
import com.circleftp.stream.data.provider.model.ProviderSeason
import com.circleftp.stream.data.provider.model.ResolvedStreamModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NetMirror Global Cloud Media Streaming Provider.
 * Powered by TMDB (The Movie Database) for complete Hollywood, Bollywood, and Hindi-dubbed
 * movies & TV shows metadata with high-resolution posters, seasons/episodes, and live multi-search.
 *
 * Fully accessible on any Wi-Fi or mobile data network worldwide without BDIX restrictions.
 */
class NetMirrorProvider(
    private val configManager: ConfigManager? = null
) : MediaSourceProvider {

    override val providerId: String = "netmirror"
    override val providerName: String = "NetMirror (Global)"
    override val isBdixOnly: Boolean = false

    private val tmdbClient = TmdbApiClient(configManager)

    override suspend fun getTrending(): List<MediaItemModel> = withContext(Dispatchers.IO) {
        try {
            // Combine general trending with popular Bollywood/Hindi cinema so users see both
            val globalTrending = tmdbClient.getTrending()
            val hindiTrending = tmdbClient.getHindiTrending()

            // Merge and deduplicate by id
            val combined = (hindiTrending.take(10) + globalTrending)
                .distinctBy { it.id }

            combined.map { it.toMediaItemModel() }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun search(query: String): List<MediaItemModel> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val results = tmdbClient.search(query)
            results.map { it.toMediaItemModel() }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching '$query': ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getDetails(mediaId: String, mediaType: String): MediaDetailModel = withContext(Dispatchers.IO) {
        val parsed = parseIdAndType(mediaId, mediaType)
        val numericId = parsed.first
        val isSeries = parsed.second

        try {
            if (isSeries) {
                val tvDetail = tmdbClient.getTvDetails(numericId)
                if (tvDetail != null) {
                    val mappedSeasons = tvDetail.seasons.map { s ->
                        ProviderSeason(
                            seasonNumber = s.seasonNumber,
                            seasonName = s.seasonName,
                            episodes = s.episodes.map { ep ->
                                ProviderEpisode(
                                    episodeNumber = ep.episodeNumber,
                                    seasonNumber = s.seasonNumber,
                                    title = ep.title,
                                    episodeId = "tv_${tvDetail.id}_s${s.seasonNumber}_e${ep.episodeNumber}",
                                    thumbnail = ep.stillPath,
                                    releaseDate = ep.releaseDate
                                )
                            }
                        )
                    }

                    return@withContext MediaDetailModel(
                        id = "tv_$numericId",
                        title = tvDetail.title,
                        description = tvDetail.overview.ifBlank { "Full TV series available on NetMirror Global Cloud." },
                        posterUrl = tvDetail.posterUrl,
                        backdropUrl = tvDetail.backdropUrl,
                        mediaType = MediaType.SERIES,
                        releaseYear = tvDetail.year,
                        rating = tvDetail.rating,
                        quality = "1080p HD",
                        genres = tvDetail.genres,
                        imdbId = tvDetail.imdbId,
                        tmdbId = tvDetail.id.toString(),
                        providerId = providerId,
                        seasons = mappedSeasons
                    )
                }
            } else {
                val movieDetail = tmdbClient.getMovieDetails(numericId)
                if (movieDetail != null) {
                    return@withContext MediaDetailModel(
                        id = "movie_$numericId",
                        title = movieDetail.title,
                        description = movieDetail.overview.ifBlank { "Full movie available on NetMirror Global Cloud." },
                        posterUrl = movieDetail.posterUrl,
                        backdropUrl = movieDetail.backdropUrl,
                        mediaType = MediaType.MOVIE,
                        releaseYear = movieDetail.year,
                        rating = movieDetail.rating,
                        quality = "1080p HD",
                        genres = movieDetail.genres,
                        imdbId = movieDetail.imdbId,
                        tmdbId = movieDetail.id.toString(),
                        providerId = providerId
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting details for $mediaId: ${e.message}", e)
        }

        // Resilient fallback
        MediaDetailModel(
            id = mediaId,
            title = "Title Unavailable",
            description = "Detailed metadata currently unavailable. Click Play to launch stream.",
            mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
            providerId = providerId
        )
    }

    override suspend fun getStreams(mediaId: String, season: Int, episode: Int): ResolvedStreamModel = withContext(Dispatchers.IO) {
        val parsed = parseIdAndType(mediaId, if (season > 0) MediaType.SERIES else MediaType.MOVIE)
        val numericId = parsed.first
        val isSeries = parsed.second || season > 0

        try {
            GlobalStreamResolver.resolveStreams(
                tmdbId = numericId.toString(),
                isSeries = isSeries,
                season = season,
                episode = episode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving streams for $mediaId: ${e.message}", e)
            ResolvedStreamModel()
        }
    }

    private fun parseIdAndType(rawId: String, hintType: String): Pair<Int, Boolean> {
        val clean = rawId.trim().lowercase()
        return when {
            clean.startsWith("tv_") -> {
                val num = clean.removePrefix("tv_").substringBefore("_").toIntOrNull() ?: 0
                Pair(num, true)
            }
            clean.startsWith("series_") -> {
                val num = clean.removePrefix("series_").substringBefore("_").toIntOrNull() ?: 0
                Pair(num, true)
            }
            clean.startsWith("movie_") -> {
                val num = clean.removePrefix("movie_").substringBefore("_").toIntOrNull() ?: 0
                Pair(num, false)
            }
            else -> {
                val num = clean.substringBefore("_").toIntOrNull() ?: 0
                val isSeries = hintType.equals("series", ignoreCase = true) || hintType.equals("tv", ignoreCase = true)
                Pair(num, isSeries)
            }
        }
    }

    private fun TmdbItemSummary.toMediaItemModel(): MediaItemModel {
        val prefix = if (isSeries) "tv_" else "movie_"
        return MediaItemModel(
            id = "$prefix$id",
            title = title,
            posterUrl = posterUrl,
            bannerUrl = backdropUrl,
            mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
            releaseYear = year,
            rating = rating,
            quality = quality ?: if (isSeries) "Series" else "1080p",
            providerId = providerId,
            isBdix = false,
            description = if (originalLanguage == "hi") "Bollywood / Hindi Audio" else "Global Internet Cloud Stream"
        )
    }

    companion object {
        private const val TAG = "NetMirrorProvider"
    }
}
