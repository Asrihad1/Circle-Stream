package com.circleftp.stream.data.provider.netmirror

import android.util.Log
import com.circleftp.stream.data.config.ConfigManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * Dedicated API Client for The Movie Database (TMDB).
 * Powers the NetMirror Global provider with full Hollywood & Bollywood metadata,
 * Hindi-dubbed cinema listings, live trending catalogs, season/episodes, and multi-search.
 */
class TmdbApiClient(
    private val configManager: ConfigManager? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(12, TimeUnit.SECONDS)
                readTimeout(20, TimeUnit.SECONDS)
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }

    private fun getApiKeys(): List<String> {
        val customKey = configManager?.getDomain("tmdb_api_key", "")
        return if (!customKey.isNullOrBlank()) {
            listOf(customKey, PRIMARY_TMDB_API_KEY, SECONDARY_TMDB_API_KEY)
        } else {
            listOf(PRIMARY_TMDB_API_KEY, SECONDARY_TMDB_API_KEY)
        }
    }

    private fun getBaseUrl(): String {
        return "https://api.themoviedb.org/3"
    }

    fun buildImageUrl(path: String?, size: String = "w500"): String? {
        if (path.isNullOrBlank()) return null
        val clean = path.removePrefix("/")
        return "https://image.tmdb.org/t/p/$size/$clean"
    }

    private suspend fun fetchJsonWithKeyFallback(urlBuilder: (String) -> String): String? {
        val keys = getApiKeys()
        for (key in keys) {
            try {
                val url = urlBuilder(key)
                val response = httpClient.get(url)
                val status = response.status.value
                if (status in 200..299) {
                    val body = response.bodyAsText()
                    if (body.contains("\"results\"") || body.contains("\"id\"")) {
                        return body
                    }
                }
                Log.w(TAG, "Key $key returned HTTP $status for $url")
            } catch (e: Exception) {
                Log.w(TAG, "Request failed with key: ${e.message}")
            }
        }
        return null
    }

    /**
     * Fetches globally trending movies and series.
     */
    suspend fun getTrending(): List<TmdbItemSummary> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TmdbItemSummary>()
        try {
            val body = fetchJsonWithKeyFallback { key ->
                "${getBaseUrl()}/trending/all/day?api_key=$key&language=en-US"
            }
            if (body != null) {
                list.addAll(parseItemSummaries(body))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch trending: ${e.message}")
        }
        list
    }

    /**
     * Fetches top trending Bollywood & South Hindi dubbed movies.
     */
    suspend fun getHindiTrending(): List<TmdbItemSummary> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TmdbItemSummary>()
        try {
            val body = fetchJsonWithKeyFallback { key ->
                "${getBaseUrl()}/discover/movie?api_key=$key&with_original_language=hi&sort_by=popularity.desc&page=1"
            }
            if (body != null) {
                list.addAll(parseItemSummaries(body, forcedType = "movie", tagBadge = "Hindi / Bollywood"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Hindi trending: ${e.message}")
        }
        list
    }

    /**
     * Multi-search supporting both titles and actors across Movies and TV Shows.
     */
    suspend fun search(query: String): List<TmdbItemSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val list = mutableListOf<TmdbItemSummary>()
        try {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val body = fetchJsonWithKeyFallback { key ->
                "${getBaseUrl()}/search/multi?api_key=$key&query=$encoded&include_adult=false&language=en-US"
            }
            if (body != null) {
                list.addAll(parseItemSummaries(body))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search failed for '$query': ${e.message}")
        }
        list
    }

    /**
     * Detailed metadata for a single Movie.
     */
    suspend fun getMovieDetails(id: Int): TmdbDetailResult? = withContext(Dispatchers.IO) {
        try {
            val body = fetchJsonWithKeyFallback { key ->
                "${getBaseUrl()}/movie/$id?api_key=$key&language=en-US&append_to_response=external_ids,credits"
            }
            if (body != null) {
                parseMovieDetail(body)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch movie details for id=$id: ${e.message}")
            null
        }
    }

    /**
     * Detailed metadata for a TV Series including seasons and episodes.
     */
    suspend fun getTvDetails(id: Int): TmdbDetailResult? = withContext(Dispatchers.IO) {
        try {
            val primaryKey = getApiKeys().first()
            val body = fetchJsonWithKeyFallback { key ->
                "${getBaseUrl()}/tv/$id?api_key=$key&language=en-US&append_to_response=external_ids,credits"
            }
            if (body != null) {
                parseTvDetail(body, id, primaryKey)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch TV details for id=$id: ${e.message}")
            null
        }
    }

    private suspend fun parseTvDetail(jsonStr: String, tvId: Int, apiKey: String): TmdbDetailResult? {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val name = root["name"]?.jsonPrimitive?.content ?: return null
        val overview = root["overview"]?.jsonPrimitive?.content.orEmpty()
        val posterPath = root["poster_path"]?.jsonPrimitive?.content
        val backdropPath = root["backdrop_path"]?.jsonPrimitive?.content
        val voteAvg = root["vote_average"]?.jsonPrimitive?.content
        val firstAirDate = root["first_air_date"]?.jsonPrimitive?.content
        val year = firstAirDate?.take(4)

        val genresList = mutableListOf<String>()
        root["genres"]?.jsonArray?.forEach { g ->
            g.jsonObject["name"]?.jsonPrimitive?.content?.let { genresList.add(it) }
        }

        val imdbId = root["external_ids"]?.jsonObject?.get("imdb_id")?.jsonPrimitive?.content

        val seasonsList = mutableListOf<TmdbSeasonSummary>()
        val seasonsArray = root["seasons"]?.jsonArray ?: JsonArray(emptyList())

        for (s in seasonsArray) {
            val sObj = s.jsonObject
            val seasonNum = sObj["season_number"]?.jsonPrimitive?.intOrNull ?: continue
            if (seasonNum <= 0) continue // Skip specials (Season 0)
            val seasonName = sObj["name"]?.jsonPrimitive?.content ?: "Season $seasonNum"
            val epCount = sObj["episode_count"]?.jsonPrimitive?.intOrNull ?: 1

            // Pre-populate episodes structure
            val epList = mutableListOf<TmdbEpisodeSummary>()
            // Attempt to load full season episodes list from TMDB
            try {
                val seasonUrl = "${getBaseUrl()}/tv/$tvId/season/$seasonNum?api_key=$apiKey&language=en-US"
                val seasonResp = httpClient.get(seasonUrl).bodyAsText()
                val seasonRoot = json.parseToJsonElement(seasonResp).jsonObject
                seasonRoot["episodes"]?.jsonArray?.forEach { epElem ->
                    val epObj = epElem.jsonObject
                    val epNum = epObj["episode_number"]?.jsonPrimitive?.intOrNull ?: 1
                    val epName = epObj["name"]?.jsonPrimitive?.content ?: "Episode $epNum"
                    val stillPath = epObj["still_path"]?.jsonPrimitive?.content
                    val airDate = epObj["air_date"]?.jsonPrimitive?.content
                    epList.add(
                        TmdbEpisodeSummary(
                            episodeNumber = epNum,
                            seasonNumber = seasonNum,
                            title = epName,
                            stillPath = buildImageUrl(stillPath),
                            releaseDate = airDate
                        )
                    )
                }
            } catch (_: Exception) {
                // Fallback: Generate empty episode placeholders
                for (i in 1..epCount) {
                    epList.add(
                        TmdbEpisodeSummary(
                            episodeNumber = i,
                            seasonNumber = seasonNum,
                            title = "Episode $i"
                        )
                    )
                }
            }

            seasonsList.add(
                TmdbSeasonSummary(
                    seasonNumber = seasonNum,
                    seasonName = seasonName,
                    episodes = epList
                )
            )
        }

        return TmdbDetailResult(
            id = tvId,
            title = name,
            overview = overview,
            posterUrl = buildImageUrl(posterPath),
            backdropUrl = buildImageUrl(backdropPath, "w1280"),
            isSeries = true,
            year = year,
            rating = voteAvg?.take(3),
            genres = genresList,
            imdbId = imdbId,
            seasons = seasonsList
        )
    }

    private fun parseMovieDetail(jsonStr: String): TmdbDetailResult? {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val id = root["id"]?.jsonPrimitive?.intOrNull ?: return null
        val title = root["title"]?.jsonPrimitive?.content ?: return null
        val overview = root["overview"]?.jsonPrimitive?.content.orEmpty()
        val posterPath = root["poster_path"]?.jsonPrimitive?.content
        val backdropPath = root["backdrop_path"]?.jsonPrimitive?.content
        val voteAvg = root["vote_average"]?.jsonPrimitive?.content
        val releaseDate = root["release_date"]?.jsonPrimitive?.content
        val year = releaseDate?.take(4)

        val genresList = mutableListOf<String>()
        root["genres"]?.jsonArray?.forEach { g ->
            g.jsonObject["name"]?.jsonPrimitive?.content?.let { genresList.add(it) }
        }

        val imdbId = root["external_ids"]?.jsonObject?.get("imdb_id")?.jsonPrimitive?.content

        return TmdbDetailResult(
            id = id,
            title = title,
            overview = overview,
            posterUrl = buildImageUrl(posterPath),
            backdropUrl = buildImageUrl(backdropPath, "w1280"),
            isSeries = false,
            year = year,
            rating = voteAvg?.take(3),
            genres = genresList,
            imdbId = imdbId,
            seasons = emptyList()
        )
    }

    private fun parseItemSummaries(
        jsonStr: String,
        forcedType: String? = null,
        tagBadge: String? = null
    ): List<TmdbItemSummary> {
        val list = mutableListOf<TmdbItemSummary>()
        try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val results = root["results"]?.jsonArray ?: return emptyList()

            for (elem in results) {
                if (elem !is JsonObject) continue
                val id = elem["id"]?.jsonPrimitive?.intOrNull ?: continue
                val mediaTypeRaw = elem["media_type"]?.jsonPrimitive?.content ?: forcedType ?: "movie"
                if (mediaTypeRaw.equals("person", ignoreCase = true)) continue

                val isSeries = mediaTypeRaw.equals("tv", ignoreCase = true) || mediaTypeRaw.equals("series", ignoreCase = true)
                val title = if (isSeries) {
                    elem["name"]?.jsonPrimitive?.content ?: elem["original_name"]?.jsonPrimitive?.content ?: continue
                } else {
                    elem["title"]?.jsonPrimitive?.content ?: elem["original_title"]?.jsonPrimitive?.content ?: continue
                }

                val posterPath = elem["poster_path"]?.jsonPrimitive?.content
                val backdropPath = elem["backdrop_path"]?.jsonPrimitive?.content
                val dateStr = if (isSeries) elem["first_air_date"]?.jsonPrimitive?.content else elem["release_date"]?.jsonPrimitive?.content
                val year = dateStr?.take(4)
                val rating = elem["vote_average"]?.jsonPrimitive?.content?.take(3)
                val origLang = elem["original_language"]?.jsonPrimitive?.content

                val badge = when {
                    tagBadge != null -> tagBadge
                    origLang == "hi" -> "Hindi / Bollywood"
                    isSeries -> "Series"
                    else -> "1080p HD"
                }

                list.add(
                    TmdbItemSummary(
                        id = id,
                        title = title,
                        posterUrl = buildImageUrl(posterPath),
                        backdropUrl = buildImageUrl(backdropPath, "w780"),
                        isSeries = isSeries,
                        year = year,
                        rating = rating,
                        quality = badge,
                        originalLanguage = origLang
                    )
                )
            }
        } catch (_: Exception) {
            // lenient
        }
        return list
    }

    companion object {
        private const val TAG = "TmdbApiClient"
        // High-availability TMDB API key pool
        const val PRIMARY_TMDB_API_KEY = "4e44d9029b1270a757cddc766a1bcb63"
        const val SECONDARY_TMDB_API_KEY = "15d2ea6d0dc1d476efbca3eba2b9bbfb"
    }
}

data class TmdbItemSummary(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val isSeries: Boolean,
    val year: String?,
    val rating: String?,
    val quality: String?,
    val originalLanguage: String? = null
)

data class TmdbDetailResult(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val isSeries: Boolean,
    val year: String?,
    val rating: String?,
    val genres: List<String>,
    val imdbId: String?,
    val seasons: List<TmdbSeasonSummary>
)

data class TmdbSeasonSummary(
    val seasonNumber: Int,
    val seasonName: String,
    val episodes: List<TmdbEpisodeSummary>
)

data class TmdbEpisodeSummary(
    val episodeNumber: Int,
    val seasonNumber: Int,
    val title: String,
    val stillPath: String? = null,
    val releaseDate: String? = null
)

