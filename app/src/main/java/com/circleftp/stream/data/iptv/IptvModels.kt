package com.circleftp.stream.data.iptv

import kotlinx.serialization.Serializable

/**
 * Data representation of a live television channel on the BDIX network.
 */
@Serializable
data class IptvChannel(
    val id: String,
    val name: String,
    val category: String,
    val resolution: String = "1080p",
    val isActive: Boolean = true,
    val serverNode: String = "103.89.248.26:8082"
)
