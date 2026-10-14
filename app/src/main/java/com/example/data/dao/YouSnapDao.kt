package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CommentEntity
import com.example.data.model.ProfileEntity
import com.example.data.model.ReelEntity
import com.example.data.model.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YouSnapDao {

    // Profile Operations
    @Query("SELECT * FROM profile_table WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile_table WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)


    // Settings Operations
    @Query("SELECT * FROM settings_table WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings_table WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity)

    @Update
    suspend fun updateSettings(settings: SettingsEntity)


    // Reels Operations
    @Query("SELECT * FROM reels_table ORDER BY timestamp DESC")
    fun getReelsFlow(): Flow<List<ReelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReel(reel: ReelEntity)

    @Update
    suspend fun updateReel(reel: ReelEntity)

    @Query("SELECT COUNT(*) FROM reels_table")
    suspend fun getReelsCount(): Int

    @Query("DELETE FROM reels_table WHERE id = :reelId")
    suspend fun deleteReelById(reelId: Int)

    @Query("DELETE FROM reels_table WHERE creatorUsername IN ('alex_travels', 'yuki_tech', 'chloe_bakes', 'arun_explorer', 'elena_dance', 'karan_fitness', 'maya_pottery', 'zack_skates', 'guest_user') OR creatorName IN ('Alex Rivera', 'Yuki Tanaka', 'Chloe Bennett', 'Arun Patel', 'Elena Rostova', 'Karan Mehta', 'Maya Lin', 'Zack Taylor', 'Guest User') OR id <= 8")
    suspend fun clearDemoReels()


    // Comments Operations
    @Query("SELECT * FROM comments_table WHERE reelId = :reelId ORDER BY timestamp ASC")
    fun getCommentsForReelFlow(reelId: Int): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)
}
