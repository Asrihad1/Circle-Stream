package com.circleftp.stream.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class PostSummary(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    val image: String? = null,
    val imageSm: String? = null,
    val quality: String? = null,
    val watchTime: String? = null,
    val type: String? = null,
    val year: String? = null
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: name ?: "Untitled"

    fun getPosterUrl(): String {
        val img = imageSm ?: image
        return if (!img.isNullOrBlank()) {
            "http://new.circleftp.net:5000/uploads/$img"
        } else {
            ""
        }
    }

    fun getBackdropUrl(): String {
        val img = image ?: imageSm
        return if (!img.isNullOrBlank()) {
            "http://new.circleftp.net:5000/uploads/$img"
        } else {
            ""
        }
    }
}

@Serializable
data class CategoryPosts(
    val id: Long,
    val name: String? = null,
    val posts: List<PostSummary> = emptyList()
)

@Serializable
data class HomePageResponse(
    val latestPost: List<PostSummary> = emptyList(),
    val categoryPosts: List<CategoryPosts> = emptyList(),
    val mostVisitedPosts: List<PostSummary> = emptyList()
)

@Serializable
data class CategoryItem(
    val id: Long,
    val name: String,
    val type: String? = null,
    val parentId: Long? = null,
    val subCategory: List<CategoryItem> = emptyList()
)

@Serializable
data class CategoryPostsResponse(
    val posts: List<PostSummary> = emptyList()
)

@Serializable
data class PostDetailRaw(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    val type: String? = null,
    val image: String? = null,
    val imageSm: String? = null,
    val cover: String? = null,
    val quality: String? = null,
    val watchTime: String? = null,
    val year: String? = null,
    val content: JsonElement? = null,
    val categories: List<CategoryItem> = emptyList()
)

data class StreamableEpisode(
    val title: String,
    val streamUrl: String
)

data class StreamableSeason(
    val seasonName: String,
    val episodes: List<StreamableEpisode>
)

data class ResolvedPostDetail(
    val id: Long,
    val title: String,
    val type: String,
    val quality: String?,
    val year: String?,
    val posterUrl: String,
    val backdropUrl: String,
    val singleStreamUrl: String?,
    val seasons: List<StreamableSeason>,
    val multiFiles: List<StreamableEpisode>
) {
    val isSeries: Boolean get() = type == "series"
    val isSingleVideo: Boolean get() = type == "singleVideo"
}

object ContentParser {
    fun parse(raw: PostDetailRaw): ResolvedPostDetail {
        val poster = if (!raw.imageSm.isNullOrBlank()) "http://new.circleftp.net:5000/uploads/${raw.imageSm}"
        else if (!raw.image.isNullOrBlank()) "http://new.circleftp.net:5000/uploads/${raw.image}" else ""

        val backdrop = if (!raw.image.isNullOrBlank()) "http://new.circleftp.net:5000/uploads/${raw.image}"
        else poster

        var singleUrl: String? = null
        val seasonsList = mutableListOf<StreamableSeason>()
        val multiFilesList = mutableListOf<StreamableEpisode>()

        val content = raw.content
        if (content != null) {
            when {
                // String: singleVideo or singleFile
                content is kotlinx.serialization.json.JsonPrimitive && content.isString -> {
                    singleUrl = content.content
                }
                // Array: series (array of seasons) or multiVideo/multiFile (array of episodes)
                content is JsonArray -> {
                    if (raw.type == "series") {
                        for (seasonElem in content) {
                            if (seasonElem is JsonObject) {
                                val sName = seasonElem["seasonName"]?.jsonPrimitive?.content ?: "Season"
                                val epArray = seasonElem["episodes"]?.jsonArray ?: JsonArray(emptyList())
                                val epList = mutableListOf<StreamableEpisode>()
                                for (epElem in epArray) {
                                    if (epElem is JsonObject) {
                                        val epTitle = epElem["title"]?.jsonPrimitive?.content ?: "Episode"
                                        val epLink = epElem["link"]?.jsonPrimitive?.content ?: ""
                                        if (epLink.isNotBlank()) {
                                            epList.add(StreamableEpisode(epTitle, epLink))
                                        }
                                    }
                                }
                                seasonsList.add(StreamableSeason(sName, epList))
                            }
                        }
                    } else {
                        // multiVideo or multiFile
                        for (elem in content) {
                            if (elem is JsonObject) {
                                val title = elem["title"]?.jsonPrimitive?.content ?: "Part"
                                val link = elem["link"]?.jsonPrimitive?.content ?: ""
                                if (link.isNotBlank()) {
                                    multiFilesList.add(StreamableEpisode(title, link))
                                }
                            }
                        }
                    }
                }
            }
        }

        return ResolvedPostDetail(
            id = raw.id,
            title = raw.title ?: raw.name ?: "Untitled",
            type = raw.type ?: "singleVideo",
            quality = raw.quality,
            year = raw.year,
            posterUrl = poster,
            backdropUrl = backdrop,
            singleStreamUrl = singleUrl,
            seasons = seasonsList,
            multiFiles = multiFilesList
        )
    }
}

