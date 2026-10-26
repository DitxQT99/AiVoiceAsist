package com.voxmind.ai.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC") fun observeAll(): Flow<List<Recording>>
    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1") suspend fun getById(id: Long): Recording?
    @Insert suspend fun insert(item: Recording): Long
    @Update suspend fun update(item: Recording)
    @Query("DELETE FROM recordings WHERE id = :id") suspend fun deleteById(id: Long)
}
@Database(entities = [Recording::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "voxmind.db").build().also { instance = it }
        }
    }
}
