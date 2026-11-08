package com.circleftp.stream.data.provider.iptv

import com.circleftp.stream.data.config.ConfigManager
import com.circleftp.stream.data.iptv.IptvCatalog
import com.circleftp.stream.data.iptv.IptvChannel
import com.circleftp.stream.data.iptv.IptvStreamResolver
import com.circleftp.stream.data.provider.MediaSourceProvider
import com.circleftp.stream.data.provider.model.MediaDetailModel
import com.circleftp.stream.data.provider.model.MediaItemModel
import com.circleftp.stream.data.provider.model.MediaType
import com.circleftp.stream.data.provider.model.ResolvedStreamModel

/**
 * IPTV IDN BDIX Live TV Streaming Provider.
 * Streams live satellite TV channels via high-speed Flussonic edge nodes.
 */
class IptvIdnProvider(
    private val configManager: ConfigManager? = null
) : MediaSourceProvider {

    override val providerId: String = "iptvidn"
    override val providerName: String = "Live TV (BDIX)"
    override val isBdixOnly: Boolean = true

    override suspend fun getTrending(): List<MediaItemModel> {
        return IptvCatalog.ACTIVE_CHANNELS.map { it.toMediaItemModel() }
    }

    override suspend fun search(query: String): List<MediaItemModel> {
        return IptvCatalog.searchChannels(query, category = "All", includeInactive = false)
            .map { it.toMediaItemModel() }
    }

    override suspend fun getDetails(mediaId: String, mediaType: String): MediaDetailModel {
        val channel = IptvCatalog.getChannelById(mediaId) ?: IptvChannel(
            id = mediaId,
            name = mediaId,
            category = "Live TV",
            resolution = "1080p HD",
            isActive = true
        )

        return MediaDetailModel(
            id = channel.id,
            title = channel.name,
            description = "Live television broadcast via BDIX Flussonic Edge (${channel.serverNode}). Category: ${channel.category}.",
            mediaType = MediaType.LIVE_STREAM,
            genres = listOf(channel.category, "Live TV", "BDIX"),
            providerId = providerId
        )
    }

    override suspend fun getStreams(mediaId: String, season: Int, episode: Int): ResolvedStreamModel {
        return IptvStreamResolver.resolveStream(mediaId) ?: ResolvedStreamModel()
    }

    private fun IptvChannel.toMediaItemModel(): MediaItemModel {
        return MediaItemModel(
            id = id,
            title = name,
            mediaType = MediaType.LIVE_STREAM,
            quality = resolution,
            providerId = providerId,
            isBdix = true,
            description = category
        )
    }
}

