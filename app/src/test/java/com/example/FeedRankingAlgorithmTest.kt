package com.example

import com.example.data.model.ReelEntity
import com.example.ui.StoryModel
import com.example.ui.components.FullScreenPhotoData
import com.example.ui.components.HomeFeedEntry
import com.example.ui.components.buildHomeFeedWithAds
import com.example.ui.utils.FeedRankingAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRankingAlgorithmTest {

    @Test
    fun `parseIsoDateTimeToMillis parses UTC offsets and rejects invalid strings`() {
        val utcMs = FeedRankingAlgorithm.parseIsoDateTimeToMillis("2026-11-24T12:00:00.000Z")
        val istMs = FeedRankingAlgorithm.parseIsoDateTimeToMillis("2026-11-24T17:30:00+05:30")
        assertNotNull(utcMs)
        assertEquals(utcMs, istMs)
        assertNull(FeedRankingAlgorithm.parseIsoDateTimeToMillis(null))
        assertNull(FeedRankingAlgorithm.parseIsoDateTimeToMillis(""))
        assertNull(FeedRankingAlgorithm.parseIsoDateTimeToMillis("not-a-date"))
    }

    @Test
    fun `isActiveViral respects isViral flag and future viralExpiresAt`() {
        val nowMs = 1_800_000_000_000L
        val futureIso = FeedRankingAlgorithm.formatMillisAsIsoUtc(nowMs + 3_600_000L)
        val pastIso = FeedRankingAlgorithm.formatMillisAsIsoUtc(nowMs - 1_000L)

        assertTrue(FeedRankingAlgorithm.isActiveViral(true, futureIso, nowMs))
        assertFalse(FeedRankingAlgorithm.isActiveViral(false, futureIso, nowMs))
        assertFalse(FeedRankingAlgorithm.isActiveViral(true, pastIso, nowMs))
        assertTrue(FeedRankingAlgorithm.isActiveViral(true, null, nowMs))
        assertFalse(FeedRankingAlgorithm.isActiveViral(true, "invalid-iso", nowMs))
    }

    @Test
    fun `active viral reel ranks above high-engagement normal reel and drops when expired`() {
        val nowMs = 1_800_000_000_000L
        val futureIso = FeedRankingAlgorithm.formatMillisAsIsoUtc(nowMs + 60_000L)
        val pastIso = FeedRankingAlgorithm.formatMillisAsIsoUtc(nowMs - 1_000L)

        val popularNormal = ReelEntity(
            id = 1,
            creatorName = "Popular",
            creatorUsername = "popular",
            caption = "Normal popular",
            viewsCount = 20_000,
            likesCount = 1_500,
            timestamp = nowMs - 3_600_000L,
            createdAtMillis = nowMs - 3_600_000L,
            isViral = false
        )
        val boostedNewCreator = ReelEntity(
            id = 2,
            creatorName = "Boosted",
            creatorUsername = "boosted",
            caption = "Admin boosted",
            viewsCount = 5,
            likesCount = 1,
            timestamp = nowMs - 7_200_000L,
            createdAtMillis = nowMs - 7_200_000L,
            isViral = true,
            viralDurationDays = 1,
            viralExpiresAt = futureIso
        )

        val rankedWhileActive = FeedRankingAlgorithm.rankReels(listOf(popularNormal, boostedNewCreator), nowMs)
        assertEquals(boostedNewCreator.id, rankedWhileActive.first().id)

        val expiredBoost = boostedNewCreator.copy(viralExpiresAt = pastIso)
        val rankedAfterExpiry = FeedRankingAlgorithm.rankReels(listOf(popularNormal, expiredBoost), nowMs)
        assertEquals(popularNormal.id, rankedAfterExpiry.first().id)
    }

    @Test
    fun `home feed places active viral video above normal photos and reels`() {
        val nowMs = 1_800_000_000_000L
        val futureIso = FeedRankingAlgorithm.formatMillisAsIsoUtc(nowMs + 120_000L)

        val normalPhoto = FullScreenPhotoData(
            id = "100",
            title = "Photo",
            imageUrl = "https://example.com/a.jpg",
            creatorName = "Alice",
            creatorUsername = "alice",
            creatorAvatar = "",
            initialLikes = 500,
            initialComments = 20,
            createdAtMillis = nowMs - 10_000L
        )
        val viralReel = ReelEntity(
            id = 200,
            creatorName = "Bob",
            creatorUsername = "bob",
            caption = "Viral reel",
            viewsCount = 10,
            likesCount = 2,
            timestamp = nowMs - 50_000L,
            createdAtMillis = nowMs - 50_000L,
            isViral = true,
            viralExpiresAt = futureIso
        )

        val feed = buildHomeFeedWithAds(
            photos = listOf(normalPhoto),
            reels = listOf(viralReel),
            adInterval = 5,
            nowMs = nowMs
        )
        assertTrue(feed.first() is HomeFeedEntry.Video)
        assertEquals(200, (feed.first() as HomeFeedEntry.Video).reel.id)
    }

    @Test
    fun `active viral story ranks before normal stories`() {
        val nowMs = 1_800_000_000_000L
        val futureIso = FeedRankingAlgorithm.formatMillisAsIsoUtc(nowMs + 300_000L)

        val normalStory = StoryModel(
            storyId = "s1",
            ownerUsername = "alice",
            timestamp = nowMs - 1_000L,
            createdAtMillis = nowMs - 1_000L
        )
        val viralStory = StoryModel(
            storyId = "s2",
            ownerUsername = "bob",
            timestamp = nowMs - 100_000L,
            createdAtMillis = nowMs - 100_000L,
            isViral = true,
            viralExpiresAt = futureIso
        )

        val ranked = FeedRankingAlgorithm.rankStories(listOf(normalStory, viralStory), nowMs)
        assertEquals("bob", ranked.first().ownerUsername)
    }
}
