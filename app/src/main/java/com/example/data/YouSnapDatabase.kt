package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.YouSnapDao
import com.example.data.model.CommentEntity
import com.example.data.model.ProfileEntity
import com.example.data.model.ReelEntity
import com.example.data.model.SettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [ProfileEntity::class, SettingsEntity::class, ReelEntity::class, CommentEntity::class],
    version = 4,
    exportSchema = false
)
abstract class YouSnapDatabase : RoomDatabase() {

    abstract fun youSnapDao(): YouSnapDao

    companion object {
        @Volatile
        private var INSTANCE: YouSnapDatabase? = null

        fun getDatabase(context: Context): YouSnapDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YouSnapDatabase::class.java,
                    "yousnap_database"
                )
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.youSnapDao())
                    }
                }
            }

            private suspend fun populateDatabase(dao: YouSnapDao) {
                // Pre-populate Default Profile
                dao.insertProfile(ProfileEntity())

                // Pre-populate Default Settings
                dao.insertSettings(SettingsEntity())
            }
        }
    }
}
