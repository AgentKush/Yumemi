package org.koitharu.kotatsu.scrobbling.common.domain

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a persistent queue of failed scrobble operations for retry when network is available.
 *
 * Features:
 * - SharedPreferences persistence across app restarts
 * - Automatic expiration of old entries (default: 7 days)
 * - Thread-safe operations
 * - Observable queue size
 * - Deduplication (same manga+chapter combination)
 */
@Singleton
class ScrobbleOfflineQueue @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val _queueSize = MutableStateFlow(0)
	val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

	private val queue = mutableListOf<ScrobbleQueueEntry>()
	private val lock = Any()

	private val prefs by lazy {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}

	init {
		loadFromStorage()
	}

	/**
	 * Add a scrobble operation to the queue for later retry.
	 * Deduplicates entries with the same manga and chapter.
	 */
	fun enqueue(service: ScrobblerService, mangaId: Long, chapterId: Long) {
		synchronized(lock) {
			// Remove any existing entry for the same manga/chapter/service
			queue.removeAll { it.scrobblerService == service && it.mangaId == mangaId && it.chapterId == chapterId }

			val entry = ScrobbleQueueEntry(
				scrobblerService = service,
				mangaId = mangaId,
				chapterId = chapterId,
			)
			queue.add(entry)
			saveToStorage()
			_queueSize.value = queue.size
			logDebug { "Enqueued scrobble: ${service.name} manga=$mangaId chapter=$chapterId (queue size: ${queue.size})" }
		}
	}

	/**
	 * Get all pending entries for a specific scrobbler service.
	 */
	fun getEntriesForService(service: ScrobblerService): List<ScrobbleQueueEntry> {
		synchronized(lock) {
			cleanupExpired()
			return queue.filter { it.scrobblerService == service }.toList()
		}
	}

	/**
	 * Get all pending entries.
	 */
	fun getAllEntries(): List<ScrobbleQueueEntry> {
		synchronized(lock) {
			cleanupExpired()
			return queue.toList()
		}
	}

	/**
	 * Remove a specific entry from the queue (after successful processing).
	 */
	fun remove(entry: ScrobbleQueueEntry) {
		synchronized(lock) {
			if (queue.remove(entry)) {
				saveToStorage()
				_queueSize.value = queue.size
				logDebug { "Removed scrobble from queue: ${entry.scrobblerService.name} manga=${entry.mangaId}" }
			}
		}
	}

	/**
	 * Remove all entries for a specific manga (e.g., if manga is deleted).
	 */
	fun removeForManga(mangaId: Long) {
		synchronized(lock) {
			val removed = queue.removeAll { it.mangaId == mangaId }
			if (removed) {
				saveToStorage()
				_queueSize.value = queue.size
				logDebug { "Removed all scrobbles for manga=$mangaId" }
			}
		}
	}

	/**
	 * Clear all pending entries.
	 */
	fun clear() {
		synchronized(lock) {
			queue.clear()
			prefs.edit().remove(KEY_QUEUE).apply()
			_queueSize.value = 0
			logDebug { "Cleared scrobble queue" }
		}
	}

	/**
	 * Check if the queue is empty.
	 */
	val isEmpty: Boolean
		get() = synchronized(lock) { queue.isEmpty() }

	/**
	 * Get the current queue size.
	 */
	val size: Int
		get() = synchronized(lock) { queue.size }

	private fun cleanupExpired() {
		val now = System.currentTimeMillis()
		val expiredBefore = now - ENTRY_TTL_MS
		val removed = queue.removeAll { it.timestamp < expiredBefore }
		if (removed) {
			saveToStorage()
			_queueSize.value = queue.size
			logDebug { "Cleaned up expired scrobble entries" }
		}
	}

	private fun saveToStorage() {
		val serialized = queue.map { it.serialize() }.toSet()
		prefs.edit().putStringSet(KEY_QUEUE, serialized).apply()
	}

	private fun loadFromStorage() {
		val serialized = prefs.getStringSet(KEY_QUEUE, emptySet()) ?: emptySet()
		val now = System.currentTimeMillis()
		val expiredBefore = now - ENTRY_TTL_MS

		queue.clear()
		serialized.mapNotNull { ScrobbleQueueEntry.deserialize(it) }
			.filter { it.timestamp >= expiredBefore }
			.forEach { queue.add(it) }

		_queueSize.value = queue.size
		logDebug { "Loaded ${queue.size} entries from scrobble queue" }
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	companion object {
		private const val TAG = "ScrobbleOfflineQueue"
		private const val PREFS_NAME = "scrobble_offline_queue"
		private const val KEY_QUEUE = "queue"

		/** Entries expire after 7 days */
		val ENTRY_TTL_MS = TimeUnit.DAYS.toMillis(7)
	}
}
