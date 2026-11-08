package com.circleftp.stream.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_later",
    indices = [
        Index(value = ["addedAt"]),
        Index(value = ["streamUrl"])
    ]
)
data class WatchLaterEntity(
    @PrimaryKey
    val mediaId: String,
    val title: String,
    val posterUrl: String? = null,
    val streamUrl: String,
    val isSeries: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

