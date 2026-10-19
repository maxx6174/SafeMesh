package com.safemesh.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        TransferRecordEntity::class,
        DuplicateCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SafeMeshDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun transferRecordDao(): TransferRecordDao
    abstract fun duplicateCacheDao(): DuplicateCacheDao

    companion object {
        @Volatile
        private var INSTANCE: SafeMeshDatabase? = null

        fun getDatabase(context: Context): SafeMeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafeMeshDatabase::class.java,
                    "safemesh_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
