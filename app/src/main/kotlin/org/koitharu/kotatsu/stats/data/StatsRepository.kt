package org.koitharu.kotatsu.stats.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import org.koitharu.kotatsu.stats.domain.StatsRecord
import java.util.Calendar
import java.util.NavigableMap
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class StatsRepository @Inject constructor(
	private val settings: AppSettings,
	private val db: MangaDatabase,
) {

	suspend fun getReadingStats(period: StatsPeriod, categories: Set<Long>): List<StatsRecord> {
		val fromDate = if (period == StatsPeriod.ALL) {
			0L
		} else {
			System.currentTimeMillis() - TimeUnit.DAYS.toMillis(period.days.toLong())
		}
		val stats = db.getStatsDao().getDurationStats(fromDate, null, categories)
		val result = ArrayList<StatsRecord>(stats.size)
		var other = StatsRecord(null, 0)
		val total = stats.values.sum()
		for ((mangaEntity, duration) in stats) {
			val manga = mangaEntity.toManga(emptySet(), null)
			val percent = duration.toDouble() / total
			if (percent < 0.05) {
				other = other.copy(duration = other.duration + duration)
			} else {
				result += StatsRecord(
					manga = manga,
					duration = duration,
				)
			}
		}
		if (other.duration != 0L) {
			result += other
		}
		return result
	}

	suspend fun getTimePerPage(mangaId: Long): Long = db.withTransaction {
		val dao = db.getStatsDao()
		val pages = dao.getReadPagesCount(mangaId)
		val time = if (pages >= 10) {
			dao.getAverageTimePerPage(mangaId)
		} else {
			dao.getAverageTimePerPage()
		}
		time
	}

	suspend fun getTotalPagesRead(mangaId: Long): Int {
		return db.getStatsDao().getReadPagesCount(mangaId)
	}

	suspend fun getMangaTimeline(mangaId: Long): NavigableMap<Long, Int> {
		val entities = db.getStatsDao().findAll(mangaId)
		val map = TreeMap<Long, Int>()
		for (e in entities) {
			map[e.startedAt] = e.pages
		}
		return map
	}

	suspend fun clearStats() {
		db.getStatsDao().clear()
	}

	suspend fun getReadingStreak(): ReadingStreak {
		val dao = db.getStatsDao()
		val startOfToday = startOfDay(System.currentTimeMillis())
		val todayMs = dao.getTotalDurationSince(startOfToday)
		val days = HashSet<Long>()
		for (t in dao.getStartTimesSince(startOfToday - TimeUnit.DAYS.toMillis(400))) {
			days.add(startOfDay(t))
		}
		var cursor = startOfToday
		if (cursor !in days) {
			cursor = previousDayStart(cursor)
			if (cursor !in days) {
				return ReadingStreak(0, todayMs, settings.statsDailyGoalMinutes)
			}
		}
		var streak = 0
		while (cursor in days) {
			streak++
			cursor = previousDayStart(cursor)
		}
		return ReadingStreak(streak, todayMs, settings.statsDailyGoalMinutes)
	}

	// Decrement by one calendar day (DST-safe) rather than subtracting a fixed 24h,
	// which would skip a day across a spring-forward boundary and under-count the streak.
	private fun previousDayStart(startOfDayTs: Long): Long {
		val cal = Calendar.getInstance()
		cal.timeInMillis = startOfDayTs
		cal.add(Calendar.DAY_OF_YEAR, -1)
		return startOfDay(cal.timeInMillis)
	}

	private fun startOfDay(ts: Long): Long {
		val cal = Calendar.getInstance()
		cal.timeInMillis = ts
		cal.set(Calendar.HOUR_OF_DAY, 0)
		cal.set(Calendar.MINUTE, 0)
		cal.set(Calendar.SECOND, 0)
		cal.set(Calendar.MILLISECOND, 0)
		return cal.timeInMillis
	}

	fun observeHasStats(mangaId: Long): Flow<Boolean> = settings.observeAsFlow(AppSettings.KEY_STATS_ENABLED) {
		isStatsEnabled
	}.flatMapLatest { isEnabled ->
		if (isEnabled) {
			db.getStatsDao().observeRowCount(mangaId).map { it > 0 }
		} else {
			flowOf(false)
		}
	}.distinctUntilChanged()
}
