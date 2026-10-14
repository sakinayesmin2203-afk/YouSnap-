package com.example.data.repository

import com.example.data.dao.YouSnapDao
import com.example.data.model.CommentEntity
import com.example.data.model.ProfileEntity
import com.example.data.model.ReelEntity
import com.example.data.model.SettingsEntity
import kotlinx.coroutines.flow.Flow

class YouSnapRepository(private val dao: YouSnapDao) {

    // Profile
    val profileFlow: Flow<ProfileEntity?> = dao.getProfileFlow()

    suspend fun getProfileDirect(): ProfileEntity? = dao.getProfileDirect()

    suspend fun saveProfile(profile: ProfileEntity) {
        val existing = dao.getProfileDirect()
        if (existing == null) {
            dao.insertProfile(profile)
        } else {
            dao.updateProfile(profile.copy(id = 1))
        }
    }

    // Settings
    val settingsFlow: Flow<SettingsEntity?> = dao.getSettingsFlow()

    suspend fun getSettingsDirect(): SettingsEntity? = dao.getSettingsDirect()

    suspend fun saveSettings(settings: SettingsEntity) {
        val existing = dao.getSettingsDirect()
        if (existing == null) {
            dao.insertSettings(settings)
        } else {
            dao.updateSettings(settings.copy(id = 1))
        }
    }

    // Reels
    val reelsFlow: Flow<List<ReelEntity>> = dao.getReelsFlow()

    suspend fun insertReel(reel: ReelEntity) {
        dao.insertReel(reel)
    }

    suspend fun updateReel(reel: ReelEntity) {
        dao.updateReel(reel)
    }

    suspend fun deleteReelById(reelId: Int) {
        dao.deleteReelById(reelId)
    }

    suspend fun toggleLikeReel(reelId: Int) {
        // Since we are running in room, find the reel or let it be passed down
        // We'll update like status inside the viewmodel directly for better performance or retrieve first
    }

    suspend fun ensureInitialData() {
        // Clear all demo reels so ONLY real user uploaded videos are displayed
        dao.clearDemoReels()

        if (dao.getProfileDirect() == null) {
            dao.insertProfile(ProfileEntity())
        }
        if (dao.getSettingsDirect() == null) {
            dao.insertSettings(SettingsEntity())
        }
    }

    // Comments
    fun getCommentsForReel(reelId: Int): Flow<List<CommentEntity>> = dao.getCommentsForReelFlow(reelId)

    suspend fun postComment(comment: CommentEntity) {
        dao.insertComment(comment)
    }
}
