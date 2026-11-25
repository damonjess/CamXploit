package com.spyboy.camxploit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavedCamera::class, SentinelDetection::class], version = 4, exportSchema = false)
abstract class CameraDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao
    abstract fun sentinelDao(): SentinelDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN isFeed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN pageUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN sourceLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN thumbnailUrl TEXT")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN thumbnailPath TEXT")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN verification TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN contentType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN lastVerified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_cameras ADD COLUMN candidatesJson TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: CameraDatabase? = null

        fun getDatabase(context: Context): CameraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CameraDatabase::class.java,
                    "camera_database"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
