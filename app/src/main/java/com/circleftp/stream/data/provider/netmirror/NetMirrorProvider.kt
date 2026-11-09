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

            if (combined.isNotEmpty()) {
                combined.map { it.toMediaItemModel() }
            } else {
                Log.w(TAG, "TMDB API returned empty; activating curated offline catalog")
                STATIC_CURATED_MEDIA
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending: ${e.message}; using curated fallback", e)
            STATIC_CURATED_MEDIA
        }
    }

    override suspend fun search(query: String): List<MediaItemModel> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val results = tmdbClient.search(query)
            if (results.isNotEmpty()) {
                results.map { it.toMediaItemModel() }
            } else {
                // Resilient local match
                STATIC_CURATED_MEDIA.filter { it.title.contains(query.trim(), ignoreCase = true) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching '$query': ${e.message}", e)
            STATIC_CURATED_MEDIA.filter { it.title.contains(query.trim(), ignoreCase = true) }
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

        // Check if matched in static curated list
        val curated = STATIC_CURATED_MEDIA.find { it.id.equals(mediaId, ignoreCase = true) }
        if (curated != null) {
            val fallbackSeasons = if (curated.isSeries) {
                listOf(
                    ProviderSeason(
                        seasonNumber = 1,
                        seasonName = "Season 1",
                        episodes = (1..8).map { epNum ->
                            ProviderEpisode(
                                episodeNumber = epNum,
                                seasonNumber = 1,
                                title = "Episode $epNum",
                                episodeId = "${curated.id}_s1_e$epNum"
                            )
                        }
                    )
                )
            } else emptyList()

            return@withContext MediaDetailModel(
                id = curated.id,
                title = curated.title,
                description = curated.description ?: "High-speed streaming on NetMirror Global Cloud.",
                posterUrl = curated.posterUrl,
                backdropUrl = curated.bannerUrl ?: curated.posterUrl,
                mediaType = curated.mediaType,
                releaseYear = curated.releaseYear,
                rating = curated.rating,
                quality = curated.quality ?: "1080p HD",
                tmdbId = numericId.toString(),
                providerId = providerId,
                seasons = fallbackSeasons
            )
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

        /**
         * Curated High-Availability Blockbuster Media (Bollywood, Hollywood & Top Series).
         * Guarantees that NetMirror never renders empty, even in offline mode or during TMDB API outages.
         */
        val STATIC_CURATED_MEDIA = listOf(
            MediaItemModel(
                id = "movie_872585",
                title = "Oppenheimer",
                posterUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                bannerUrl = "https://image.tmdb.org/t/p/w1280/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2023",
                rating = "8.1",
                quality = "1080p HD",
                providerId = "netmirror",
                description = "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb."
            ),
            MediaItemModel(
                id = "movie_693134",
                title = "Dune: Part Two",
                posterUrl = "https://image.tmdb.org/t/p/w500/6izwz7rsy95ARzTR3poZ8H6c5pp.jpg",
                bannerUrl = "https://image.tmdb.org/t/p/w1280/xOMo8BRK7PfcJv9JCnx7s520Hgq.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2024",
                rating = "8.2",
                quality = "1080p HD",
                providerId = "netmirror",
                description = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family."
            ),
            MediaItemModel(
                id = "movie_872906",
                title = "Jawan",
                posterUrl = "https://image.tmdb.org/t/p/w500/jFt1gS4BGHlK8xt76Y81Alp4dbt.jpg",
                bannerUrl = "https://image.tmdb.org/t/p/w1280/2vFuG6bWGyQUzYS9d69E5l85nIz.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2023",
                rating = "7.1",
                quality = "Hindi / Bollywood",
                providerId = "netmirror",
                description = "An emotional journey of a prison warden driven by a personal vendetta to rectify the wrongs in society."
            ),
            MediaItemModel(
                id = "movie_781732",
                title = "Animal",
                posterUrl = "https://image.tmdb.org/t/p/w500/hr9rjR3J0xBBKmlJ4n3gHId9ccx.jpg",
                bannerUrl = "https://image.tmdb.org/t/p/w1280/tlvggkYpkn0a9G6B94w1Fq6iK3G.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2023",
                rating = "6.5",
                quality = "Hindi / Bollywood",
                providerId = "netmirror",
                description = "The hardened son of a powerful industrialist returns home after an assassination attempt on his father."
            ),
            MediaItemModel(
                id = "movie_533535",
                title = "Deadpool & Wolverine",
                posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2024",
                rating = "7.7",
                quality = "1080p HD",
                providerId = "netmirror",
                description = "A listless Wade Wilson joins forces with a reluctant Wolverine to save his universe."
            ),
            MediaItemModel(
                id = "movie_864692",
                title = "Pathaan",
                posterUrl = "https://image.tmdb.org/t/p/w500/arf00BkwvXo0CFKbaD9OpqdE4Nu.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2023",
                rating = "6.6",
                quality = "Hindi / Bollywood",
                providerId = "netmirror",
                description = "An Indian field operative races against time to prevent a rogue mercenary group from unleashing a deadly attack."
            ),
            MediaItemModel(
                id = "movie_157336",
                title = "Interstellar",
                posterUrl = "https://image.tmdb.org/t/p/w500/yQvGrMoipbRoddT0ZR8tPoR7NfX.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2014",
                rating = "8.4",
                quality = "1080p HD",
                providerId = "netmirror",
                description = "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival."
            ),
            MediaItemModel(
                id = "movie_27205",
                title = "Inception",
                posterUrl = "https://image.tmdb.org/t/p/w500/xlaY2zyzMfkhk0HSC5VUwzoZPU1.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2010",
                rating = "8.4",
                quality = "1080p HD",
                providerId = "netmirror",
                description = "A thief who steals corporate secrets through dream-sharing technology is given the inverse task of planting an idea."
            ),
            MediaItemModel(
                id = "movie_155",
                title = "The Dark Knight",
                posterUrl = "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2008",
                rating = "8.5",
                quality = "1080p HD",
                providerId = "netmirror",
                description = "Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice."
            ),
            MediaItemModel(
                id = "movie_299534",
                title = "Avengers: Endgame",
                posterUrl = "https://image.tmdb.org/t/p/w500/jtDv3lCnST0fZRvdYFwR5CVwK2x.jpg",
                mediaType = MediaType.MOVIE,
                releaseYear = "2019",
                rating = "8.3",
                quality = "1080p HD",
                providerId = "netmirror",
                description = "The remaining Avengers assemble once more in order to reverse Thanos' actions and restore balance to the universe."
            ),
            MediaItemModel(
                id = "tv_66732",
                title = "Stranger Things",
                posterUrl = "https://image.tmdb.org/t/p/w500/uOOtwVbSr4QDjAGIifLDwpb2Pdl.jpg",
                mediaType = MediaType.SERIES,
                releaseYear = "2016",
                rating = "8.6",
                quality = "Series",
                providerId = "netmirror",
                description = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments and terrifying supernatural forces."
            ),
            MediaItemModel(
                id = "tv_1396",
                title = "Breaking Bad",
                posterUrl = "https://image.tmdb.org/t/p/w500/anFx9aTOOYqgS3v7x3R84Kz67ly.jpg",
                mediaType = MediaType.SERIES,
                releaseYear = "2008",
                rating = "8.9",
                quality = "Series",
                providerId = "netmirror",
                description = "A chemistry teacher diagnosed with terminal lung cancer turns to manufacturing methamphetamine to secure his family's future."
            ),
            MediaItemModel(
                id = "tv_1399",
                title = "Game of Thrones",
                posterUrl = "https://image.tmdb.org/t/p/w500/1XS1oqL89opfnbLl8WnZY1O1uJx.jpg",
                mediaType = MediaType.SERIES,
                releaseYear = "2011",
                rating = "8.4",
                quality = "Series",
                providerId = "netmirror",
                description = "Nine noble families fight for control over the mythical lands of Westeros, while an ancient enemy returns."
            ),
            MediaItemModel(
                id = "tv_71446",
                title = "Money Heist",
                posterUrl = "https://image.tmdb.org/t/p/w500/reEMJA1uzscCbkpeRJeTT2bjqUp.jpg",
                mediaType = MediaType.SERIES,
                releaseYear = "2017",
                rating = "8.2",
                quality = "Series",
                providerId = "netmirror",
                description = "An unusual group of robbers attempt to carry out the most perfect robbery in Spanish history."
            ),
            MediaItemModel(
                id = "tv_119051",
                title = "Wednesday",
                posterUrl = "https://image.tmdb.org/t/p/w500/9PFonBhy4cQy7Jz20NpMygczOkv.jpg",
                mediaType = MediaType.SERIES,
                releaseYear = "2022",
                rating = "8.4",
                quality = "Series",
                providerId = "netmirror",
                description = "A mystery-infused supernatural mystery tracking Wednesday Addams' years as a student at Nevermore Academy."
            ),
            MediaItemModel(
                id = "tv_76479",
                title = "The Boys",
                posterUrl = "https://image.tmdb.org/t/p/w500/in1R2dDc421JxsoRWaIIAqVI2KE.jpg",
                mediaType = MediaType.SERIES,
                releaseYear = "2019",
                rating = "8.4",
                quality = "Series",
                providerId = "netmirror",
                description = "A fun and irreverent take on what happens when superheroes abuse their superpowers rather than use them for good."
            )
        )
    }
}
