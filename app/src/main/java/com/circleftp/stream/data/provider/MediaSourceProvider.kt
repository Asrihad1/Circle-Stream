package com.circleftp.stream.data.provider

import com.circleftp.stream.data.provider.model.MediaDetailModel
import com.circleftp.stream.data.provider.model.MediaItemModel
import com.circleftp.stream.data.provider.model.ResolvedStreamModel

/**
 * Strict Unified Provider Interface for Circle Stream Multi-Source Media Aggregator.
 *
 * Streaming providers (e.g. Circle FTP, CTG Movies, FreeFlix, Fojik,
 * MLSBD, PRMovies, MovieStream, AnimeSuge, AutoEmbed, Cinegram, PVRPlay)
 * must implement this contract.
 */
interface MediaSourceProvider {
    val providerId: String // e.g., "circleftp", "ctgmovies"
    val providerName: String // e.g., "Circle FTP", "CTG Movies (BDIX)"
    val isBdixOnly: Boolean

    suspend fun getTrending(): List<MediaItemModel>
    suspend fun search(query: String): List<MediaItemModel>
    suspend fun getDetails(mediaId: String, mediaType: String): MediaDetailModel

    // Returns a list of direct playable URLs (.mp4, .m3u8), Headers (Referer), and Subtitles
    suspend fun getStreams(mediaId: String, season: Int = 0, episode: Int = 0): ResolvedStreamModel
}

