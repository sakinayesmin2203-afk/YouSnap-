package com.example

import com.example.ui.utils.ProfilePhotoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoUtilsTest {

    @Test
    fun normalizeRemotePhotoUrl_rejectsLocalUrisAndDefaults() {
        assertEquals("", ProfilePhotoUtils.normalizeRemotePhotoUrl(null))
        assertEquals("", ProfilePhotoUtils.normalizeRemotePhotoUrl(""))
        assertEquals("", ProfilePhotoUtils.normalizeRemotePhotoUrl("   "))
        assertEquals("", ProfilePhotoUtils.normalizeRemotePhotoUrl("avatar_default"))
        assertEquals("", ProfilePhotoUtils.normalizeRemotePhotoUrl("content://media/picker/0/com.android.providers.media.photopicker/media/42"))
        assertEquals("", ProfilePhotoUtils.normalizeRemotePhotoUrl("file:///storage/emulated/0/Pictures/avatar.jpg"))
        assertEquals("", ProfilePhotoUtils.normalizeRemotePhotoUrl("/data/user/0/com.yousnap.app/cache/avatar.jpg"))
    }

    @Test
    fun normalizeRemotePhotoUrl_acceptsHttpAndHttpsUrls() {
        val httpsUrl = "https://firebasestorage.googleapis.com/v0/b/yousnap-app.firebasestorage.app/o/profile_images%2Fuid123%2Favatar.jpg?alt=media"
        assertEquals(httpsUrl, ProfilePhotoUtils.normalizeRemotePhotoUrl("  $httpsUrl  "))
        assertEquals("https://lh3.googleusercontent.com/a/avatar", ProfilePhotoUtils.normalizeRemotePhotoUrl("https://lh3.googleusercontent.com/a/avatar"))
    }

    @Test
    fun isLocalImageReference_detectsLocalPathsAndIgnoresRemoteUrls() {
        assertTrue(ProfilePhotoUtils.isLocalImageReference("content://media/external/images/media/1"))
        assertTrue(ProfilePhotoUtils.isLocalImageReference("file:///data/user/0/app/cache/photo.jpg"))
        assertTrue(ProfilePhotoUtils.isLocalImageReference("/storage/emulated/0/DCIM/photo.jpg"))
        assertFalse(ProfilePhotoUtils.isLocalImageReference("https://firebasestorage.googleapis.com/avatar.jpg"))
        assertFalse(ProfilePhotoUtils.isLocalImageReference("avatar_default"))
        assertFalse(ProfilePhotoUtils.isLocalImageReference(""))
    }

    @Test
    fun resolveAvatarFromFirestoreFields_picksFirstValidRemoteUrl() {
        val googleUrl = "https://lh3.googleusercontent.com/a/user_photo"
        val resolved = ProfilePhotoUtils.resolveAvatarFromFirestoreFields(
            photoURL = "content://local/stale",
            profilePic = "avatar_default",
            avatar = null,
            authPhotoUrl = googleUrl
        )
        assertEquals(googleUrl, resolved)
    }

    @Test
    fun buildAvatarFieldsMap_populatesAllThreeFieldsIdentically() {
        val downloadUrl = "https://firebasestorage.googleapis.com/v0/b/yousnap-app/o/profile_images%2Fu1%2Favatar.jpg?alt=media"
        val map = ProfilePhotoUtils.buildAvatarFieldsMap(downloadUrl)
        assertEquals(downloadUrl, map["photoURL"])
        assertEquals(downloadUrl, map["profilePic"])
        assertEquals(downloadUrl, map["avatar"])

        val sanitizedMap = ProfilePhotoUtils.buildAvatarFieldsMap("content://local/image")
        assertEquals("", sanitizedMap["photoURL"])
        assertEquals("", sanitizedMap["profilePic"])
        assertEquals("", sanitizedMap["avatar"])
    }
}
