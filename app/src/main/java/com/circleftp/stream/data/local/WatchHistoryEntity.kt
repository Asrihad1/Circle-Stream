package com.circleftp.stream.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.circleftp.stream.player.UrlNormalizer

fun buildCanonicalMediaId(
    streamUrl: String,
    isSeries: Boolean,
    parentSeriesId: String?
): String {
    val cleanUrl = UrlNormalizer.normalize(streamUrl).trim().lowercase()
    return if (isSeries) {
        val seriesKey = parentSeriesId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "0" }
            ?: cleanUrl
        "series_$seriesKey"
    } else {
        "movie_$cleanUrl"
    }
}

@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["lastUpdated"]),
        Index(value = ["sourceId"]),
        Index(value = ["streamUrl"]),
        Index(value = ["parentSeriesId"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey
    val mediaId: String,
    val sourceId: String = "CIRCLE_FTP",
    val title: String,
    val posterUrl: String? = null,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val isSeries: Boolean = false,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val parentSeriesId: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val progressPercent: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    companion object {
        fun buildCanonicalMediaId(
            streamUrl: String,
            isSeries: Boolean,
            parentSeriesId: String?
        ): String = com.circleftp.stream.data.local.buildCanonicalMediaId(
            streamUrl = streamUrl,
            isSeries = isSeries,
            parentSeriesId = parentSeriesId
        )
    }
}
