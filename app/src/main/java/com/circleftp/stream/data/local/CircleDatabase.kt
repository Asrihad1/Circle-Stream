package com.circleftp.stream.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        WatchLaterEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CircleDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchLaterDao(): WatchLaterDao

    companion object {
        private const val DATABASE_NAME = "circlestream_local.db"

        @Volatile
        private var INSTANCE: CircleDatabase? = null

        fun getInstance(context: Context): CircleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CircleDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
