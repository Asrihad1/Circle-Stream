package com.circleftp.stream.player

import com.circleftp.stream.data.local.buildCanonicalMediaId
import com.circleftp.stream.data.model.StreamableEpisode
import com.circleftp.stream.data.provider.model.SubtitleTrack

/**
 * In-memory playback session managing playlist queues, active episode indexing,
 * movie vs. series distinction, canonical mediaId, HTTP headers, subtitles,
 * and preventing URL encoding corruption in Jetpack Navigation.
 */
object ActivePlayerSession {
    var mediaId: String = ""
    var title: String = ""
    var streamUrl: String = ""
    var seriesTitle: String = ""
    var playlist: List<StreamableEpisode> = emptyList()
    var currentIndex: Int = -1
    var postId: Long = 0L
    var posterUrl: String? = null
    var isSeries: Boolean = false
    var seasonNumber: Int? = null
    var episodeNumber: Int? = null
    var parentSeriesId: String? = null
    var providerId: String = "circleftp"
    var headers: Map<String, String> = emptyMap()
    var subtitles: List<SubtitleTrack> = emptyList()
    var availableStreams: List<com.circleftp.stream.data.provider.model.StreamSource> = emptyList()

    fun set(
        title: String,
        streamUrl: String,
        playlist: List<StreamableEpisode> = emptyList(),
        index: Int = -1,
        seriesTitle: String = "",
        postId: Long = 0L,
        posterUrl: String? = null,
        isSeries: Boolean = false,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        parentSeriesId: String? = null,
        mediaId: String? = null,
        providerId: String = "circleftp",
        headers: Map<String, String> = emptyMap(),
        subtitles: List<SubtitleTrack> = emptyList(),
        availableStreams: List<com.circleftp.stream.data.provider.model.StreamSource> = emptyList()
    ) {
        this.title = title
        this.streamUrl = UrlNormalizer.normalize(streamUrl)
        this.playlist = playlist
        this.postId = postId
        this.posterUrl = posterUrl
        this.isSeries = isSeries
        this.providerId = providerId
        this.headers = headers
        this.subtitles = subtitles
        this.availableStreams = availableStreams

        if (isSeries) {
            if (index >= 0) {
                this.currentIndex = index
            } else if (this.playlist.isNotEmpty()) {
                val found = this.playlist.indexOfFirst {
                    UrlNormalizer.normalize(it.streamUrl) == this.streamUrl || it.title == title
                }
                this.currentIndex = if (found >= 0) found else 0
            } else {
                this.currentIndex = if (episodeNumber != null && episodeNumber > 0) episodeNumber - 1 else 0
            }
            this.episodeNumber = episodeNumber ?: (this.currentIndex + 1).takeIf { it > 0 }
            this.seasonNumber = seasonNumber
            this.parentSeriesId = parentSeriesId ?: postId.takeIf { it > 0L }?.toString()
            this.seriesTitle = seriesTitle.ifBlank { title }
        } else {
            // Strict Rule: Single Movies NEVER have episodeNumber, seasonNumber, or parentSeriesId
            this.currentIndex = -1
            this.episodeNumber = null
            this.seasonNumber = null
            this.parentSeriesId = null
            this.seriesTitle = title
        }

        this.mediaId = mediaId?.takeIf { it.isNotBlank() }
            ?: buildCanonicalMediaId(
                streamUrl = this.streamUrl,
                isSeries = this.isSeries,
                parentSeriesId = this.parentSeriesId
            )
    }

    fun hasNextEpisode(): Boolean {
        return isSeries && playlist.isNotEmpty() && currentIndex >= 0 && currentIndex < playlist.size - 1
    }

    fun hasPrevEpisode(): Boolean {
        return isSeries && playlist.isNotEmpty() && currentIndex > 0
    }

    fun getNextEpisode(): StreamableEpisode? {
        if (hasNextEpisode()) {
            return playlist[currentIndex + 1]
        }
        return null
    }

    fun getPrevEpisode(): StreamableEpisode? {
        if (hasPrevEpisode()) {
            return playlist[currentIndex - 1]
        }
        return null
    }

    fun switchToIndex(index: Int): StreamableEpisode? {
        if (index in playlist.indices) {
            currentIndex = index
            val ep = playlist[index]
            this.title = ep.title
            this.streamUrl = UrlNormalizer.normalize(ep.streamUrl)
            if (isSeries) {
                this.episodeNumber = index + 1
            } else {
                this.episodeNumber = null
                this.seasonNumber = null
            }
            this.mediaId = buildCanonicalMediaId(
                streamUrl = this.streamUrl,
                isSeries = this.isSeries,
                parentSeriesId = this.parentSeriesId
            )
            return ep
        }
        return null
    }

    fun clear() {
        mediaId = ""
        title = ""
        streamUrl = ""
        seriesTitle = ""
        playlist = emptyList()
        currentIndex = -1
        postId = 0L
        posterUrl = null
        isSeries = false
        seasonNumber = null
        episodeNumber = null
        parentSeriesId = null
        providerId = "circleftp"
        headers = emptyMap()
        subtitles = emptyList()
    }
}
