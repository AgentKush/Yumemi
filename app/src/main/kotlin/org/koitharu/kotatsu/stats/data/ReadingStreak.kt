package org.koitharu.kotatsu.stats.data

/**
 * Snapshot of the user's reading streak and today's progress toward the daily goal.
 */
data class ReadingStreak(
	val currentStreak: Int,
	val todayDurationMs: Long,
	val goalMinutes: Int,
)
