package com.example.ui.utils

import com.example.data.model.ReelEntity
import com.example.ui.StoryModel
import com.example.ui.components.FullScreenPhotoData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

data class ViralMetadata(
    val isViral: Boolean = false,
    val viralDurationDays: Int = 0,
    val viralDurationHours: Int = 0,
    val viralExpiresAt: String? = null
)

object FeedRankingAlgorithm {

    private const val VIRAL_PRIORITY_BOOST = 1_000_000_000_000.0
    private val ISO_DATE_TIME_REGEX = Regex(
        """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:?\d{2})?$"""
    )
    private val DATE_ONLY_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    fun extractViralMetadata(doc: DocumentSnapshot): ViralMetadata {
        val isViral = doc.getBoolean("isViral") ?: false
        val days = (doc.get("viralDurationDays") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val hours = (doc.get("viralDurationHours") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val rawExpiry = when (val expiryValue = doc.get("viralExpiresAt")) {
            is String -> expiryValue.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            is Timestamp -> formatMillisAsIsoUtc(expiryValue.toDate().time)
            is Number -> formatMillisAsIsoUtc(expiryValue.toLong())
            else -> null
        }
        return ViralMetadata(
            isViral = isViral,
            viralDurationDays = days,
            viralDurationHours = hours,
            viralExpiresAt = rawExpiry
        )
    }

    fun extractCreatedAtMillis(doc: DocumentSnapshot, fallbackTimestamp: Long = 0L): Long {
        val createdAtTs = try {
            doc.getTimestamp("createdAt")?.toDate()?.time
        } catch (_: Throwable) {
            null
        }
        if (createdAtTs != null && createdAtTs > 0L) return createdAtTs

        val createdAtRaw = doc.get("createdAt")
        when (createdAtRaw) {
            is Number -> {
                val v = createdAtRaw.toLong()
                if (v > 0L) return v
            }
            is String -> {
                parseIsoDateTimeToMillis(createdAtRaw)?.let { if (it > 0L) return it }
            }
        }

        val ts = doc.getLong("timestamp") ?: doc.getString("id")?.toLongOrNull() ?: 0L
        return if (ts > 0L) ts else fallbackTimestamp
    }

    fun formatMillisAsIsoUtc(epochMs: Long): String {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            timeInMillis = epochMs
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            cal.get(GregorianCalendar.YEAR),
            cal.get(GregorianCalendar.MONTH) + 1,
            cal.get(GregorianCalendar.DAY_OF_MONTH),
            cal.get(GregorianCalendar.HOUR_OF_DAY),
            cal.get(GregorianCalendar.MINUTE),
            cal.get(GregorianCalendar.SECOND),
            cal.get(GregorianCalendar.MILLISECOND)
        )
    }

    fun parseIsoDateTimeToMillis(raw: String?): Long? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: return null

        trimmed.toLongOrNull()?.let { numeric ->
            return if (numeric in 1_000_000_000L..9_999_999_999L) numeric * 1000L else numeric
        }

        val match = ISO_DATE_TIME_REGEX.matchEntire(trimmed)
        if (match != null) {
            return try {
                val year = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val day = match.groupValues[3].toInt()
                val hour = match.groupValues[4].toInt()
                val minute = match.groupValues[5].toInt()
                val second = match.groupValues[6].toInt()
                val fractionStr = match.groupValues[7]
                val millis = if (fractionStr.isEmpty()) {
                    0
                } else {
                    fractionStr.padEnd(3, '0').take(3).toInt()
                }
                val tzStr = match.groupValues[8]
                val offsetMillis = parseTimezoneOffsetMillis(tzStr) ?: return null

                val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                    isLenient = false
                    clear()
                    set(year, month - 1, day, hour, minute, second)
                    set(GregorianCalendar.MILLISECOND, millis)
                }
                calendar.timeInMillis - offsetMillis
            } catch (_: Throwable) {
                null
            }
        }

        val dateMatch = DATE_ONLY_REGEX.matchEntire(trimmed)
        if (dateMatch != null) {
            return try {
                val year = dateMatch.groupValues[1].toInt()
                val month = dateMatch.groupValues[2].toInt()
                val day = dateMatch.groupValues[3].toInt()
                val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                    isLenient = false
                    clear()
                    set(year, month - 1, day, 0, 0, 0)
                    set(GregorianCalendar.MILLISECOND, 0)
                }
                calendar.timeInMillis
            } catch (_: Throwable) {
                null
            }
        }

        return null
    }

    private fun parseTimezoneOffsetMillis(tzStr: String): Long? {
        if (tzStr.isEmpty() || tzStr == "Z") return 0L
        val sign = when (tzStr[0]) {
            '+' -> 1L
            '-' -> -1L
            else -> return null
        }
        val clean = tzStr.substring(1).replace(":", "")
        if (clean.length != 4) return null
        val hours = clean.substring(0, 2).toIntOrNull() ?: return null
        val minutes = clean.substring(2, 4).toIntOrNull() ?: return null
        if (hours > 23 || minutes > 59) return null
        return sign * ((hours * 60L + minutes) * 60_000L)
    }

    fun isActiveViral(
        isViral: Boolean,
        viralExpiresAt: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isViral) return false
        val cleanExpiry = viralExpiresAt?.trim()
        if (cleanExpiry.isNullOrEmpty() || cleanExpiry.equals("null", ignoreCase = true)) {
            return true
        }
        val expiresAtMs = parseIsoDateTimeToMillis(cleanExpiry) ?: return false
        return expiresAtMs > nowMs
    }

    fun computeReelScore(reel: ReelEntity, nowMs: Long = System.currentTimeMillis()): Double {
        val createdAtMs = reel.createdAtMillis.takeIf { it > 0L } ?: reel.timestamp
        val engagementScore = (reel.viewsCount.coerceAtLeast(0) * 1.5) +
            (reel.likesCount.coerceAtLeast(0) * 5.0) +
            (reel.commentsCount.coerceAtLeast(0) * 8.0) +
            (reel.sharesCount.coerceAtLeast(0) * 12.0)
        val recencyScore = computeRecencyScore(createdAtMs, nowMs)
        val viralBoost = if (isActiveViral(reel.isViral, reel.viralExpiresAt, nowMs)) {
            VIRAL_PRIORITY_BOOST
        } else {
            0.0
        }
        return viralBoost + engagementScore + recencyScore
    }

    fun computePhotoScore(photo: FullScreenPhotoData, nowMs: Long = System.currentTimeMillis()): Double {
        val createdAtMs = photo.createdAtMillis.takeIf { it > 0L }
            ?: photo.id.toLongOrNull()
            ?: 0L
        val engagementScore = (photo.initialLikes.coerceAtLeast(0) * 5.0) +
            (photo.initialComments.coerceAtLeast(0) * 8.0) +
            (photo.sharesCount.coerceAtLeast(0) * 12.0)
        val recencyScore = computeRecencyScore(createdAtMs, nowMs)
        val viralBoost = if (isActiveViral(photo.isViral, photo.viralExpiresAt, nowMs)) {
            VIRAL_PRIORITY_BOOST
        } else {
            0.0
        }
        return viralBoost + engagementScore + recencyScore
    }

    fun computeStoryScore(story: StoryModel, nowMs: Long = System.currentTimeMillis()): Double {
        val createdAtMs = story.createdAtMillis.takeIf { it > 0L } ?: story.timestamp
        val engagementScore = (story.viewsCount.coerceAtLeast(0) * 1.5) +
            (story.likes.size * 5.0)
        val recencyScore = computeRecencyScore(createdAtMs, nowMs)
        val viralBoost = if (isActiveViral(story.isViral, story.viralExpiresAt, nowMs)) {
            VIRAL_PRIORITY_BOOST
        } else {
            0.0
        }
        return viralBoost + engagementScore + recencyScore
    }

    private fun computeRecencyScore(createdAtMs: Long, nowMs: Long): Double {
        if (createdAtMs <= 0L) return 0.0
        val ageHours = (nowMs - createdAtMs).coerceAtLeast(0L) / 3_600_000.0
        val freshnessScore = 10_000.0 / (1.0 + (ageHours / 12.0))
        val tieBreaker = createdAtMs.toDouble() / 1_000_000_000_000.0
        return freshnessScore + tieBreaker
    }

    fun rankReels(reels: List<ReelEntity>, nowMs: Long = System.currentTimeMillis()): List<ReelEntity> {
        return reels.sortedWith(
            compareByDescending<ReelEntity> { computeReelScore(it, nowMs) }
                .thenByDescending { it.createdAtMillis.takeIf { c -> c > 0L } ?: it.timestamp }
                .thenBy { it.id }
        )
    }

    fun rankPhotos(
        photos: List<FullScreenPhotoData>,
        nowMs: Long = System.currentTimeMillis()
    ): List<FullScreenPhotoData> {
        return photos.sortedWith(
            compareByDescending<FullScreenPhotoData> { computePhotoScore(it, nowMs) }
                .thenByDescending { it.createdAtMillis.takeIf { c -> c > 0L } ?: it.id.toLongOrNull() ?: 0L }
                .thenBy { it.id }
        )
    }

    fun rankStories(stories: List<StoryModel>, nowMs: Long = System.currentTimeMillis()): List<StoryModel> {
        return stories.sortedWith(
            compareByDescending<StoryModel> { computeStoryScore(it, nowMs) }
                .thenByDescending { it.createdAtMillis.takeIf { c -> c > 0L } ?: it.timestamp }
        )
    }

    fun findEarliestFutureExpiryMs(
        photos: List<FullScreenPhotoData>,
        reels: List<ReelEntity>,
        stories: List<StoryModel>,
        nowMs: Long = System.currentTimeMillis()
    ): Long? {
        var earliest: Long? = null

        fun consider(isViral: Boolean, viralExpiresAt: String?) {
            if (!isViral) return
            val expMs = parseIsoDateTimeToMillis(viralExpiresAt) ?: return
            if (expMs > nowMs && (earliest == null || expMs < earliest!!)) {
                earliest = expMs
            }
        }

        photos.forEach { consider(it.isViral, it.viralExpiresAt) }
        reels.forEach { consider(it.isViral, it.viralExpiresAt) }
        stories.forEach { consider(it.isViral, it.viralExpiresAt) }

        return earliest
    }
}
