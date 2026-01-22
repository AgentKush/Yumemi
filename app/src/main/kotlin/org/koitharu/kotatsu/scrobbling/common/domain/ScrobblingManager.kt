package org.koitharu.kotatsu.scrobbling.common.domain

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scrobbling operations with offline queue support.
 *
 * Features:
 * - Automatic queueing when offline or on network failure
 * - Processes queued scrobbles when network is available
 * - Works with all scrobbler services
 */
@Singleton
class ScrobblingManager @Inject constructor(
	@ApplicationContext private val context: Context,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	private val offlineQueue: ScrobbleOfflineQueue,
	private val networkState: NetworkState,
	private val db: MangaDatabase,
) {

	init {
		// Schedule queue processing on startup if there are pending scrobbles
		if (!offlineQueue.isEmpty) {
			ScrobbleQueueWorker.schedule(context)
		}
	}

	/**
	 * Scrobble a chapter read, queueing for later if offline or on failure.
	 *
	 * @param manga The manga being read
	 * @param chapterId The chapter that was read
	 * @return true if scrobbled immediately, false if queued for later
	 */
	suspend fun scrobble(manga: Manga, chapterId: Long): Boolean {
		val enabledScrobblers = scrobblers.filter { it.isEnabled }
		if (enabledScrobblers.isEmpty()) {
			return true // Nothing to do
		}

		var allSucceeded = true
		for (scrobbler in enabledScrobblers) {
			val success = tryScrobbleWithQueue(scrobbler, manga, chapterId)
			if (!success) allSucceeded = false
		}
		return allSucceeded
	}

	/**
	 * Process all queued scrobbles for a specific service.
	 *
	 * @return Number of successfully processed entries
	 */
	suspend fun processQueue(service: ScrobblerService): Int = withContext(Dispatchers.IO) {
		if (!networkState.value) {
			logDebug { "Skipping queue processing - offline" }
			return@withContext 0
		}

		val scrobbler = scrobblers.find { it.scrobblerService == service }
		if (scrobbler == null || !scrobbler.isEnabled) {
			logDebug { "Skipping queue processing - scrobbler not enabled: ${service.name}" }
			return@withContext 0
		}

		val entries = offlineQueue.getEntriesForService(service)
		if (entries.isEmpty()) {
			return@withContext 0
		}

		logDebug { "Processing ${entries.size} queued scrobbles for ${service.name}" }
		var successCount = 0

		for (entry in entries) {
			val result = runCatchingCancellable {
				// We need to get the manga from the database for scrobbling
				processQueueEntry(scrobbler, entry.mangaId, entry.chapterId)
			}

			if (result.isSuccess) {
				offlineQueue.remove(entry)
				successCount++
				logDebug { "Successfully processed queued scrobble: manga=${entry.mangaId}" }
			} else {
				val error = result.exceptionOrNull()
				if (error !is IOException) {
					// Non-network error, remove from queue to avoid infinite retries
					offlineQueue.remove(entry)
					logDebug { "Removed failed scrobble (non-network error): ${error?.message}" }
				} else {
					logDebug { "Will retry scrobble later: ${error.message}" }
				}
			}
		}

		return@withContext successCount
	}

	/**
	 * Process all queued scrobbles for all services.
	 *
	 * @return Total number of successfully processed entries
	 */
	suspend fun processAllQueues(): Int {
		var total = 0
		for (service in ScrobblerService.entries) {
			total += processQueue(service)
		}
		return total
	}

	/**
	 * Get the scrobbler for a specific service.
	 */
	fun getScrobbler(service: ScrobblerService): Scrobbler? {
		return scrobblers.find { it.scrobblerService == service }
	}

	private suspend fun tryScrobbleWithQueue(
		scrobbler: Scrobbler,
		manga: Manga,
		chapterId: Long,
	): Boolean {
		// If offline, queue immediately
		if (!networkState.value) {
			offlineQueue.enqueue(scrobbler.scrobblerService, manga.id, chapterId)
			ScrobbleQueueWorker.schedule(context)
			logDebug { "Offline - queued scrobble: ${scrobbler.scrobblerService.name} manga=${manga.id}" }
			return false
		}

		// Try to scrobble
		val result = runCatchingCancellable {
			scrobbler.scrobble(manga, chapterId)
		}

		return if (result.isSuccess) {
			true
		} else {
			val error = result.exceptionOrNull()
			// Queue on network errors
			if (error is IOException) {
				offlineQueue.enqueue(scrobbler.scrobblerService, manga.id, chapterId)
				ScrobbleQueueWorker.schedule(context)
				logDebug { "Network error - queued scrobble: ${error.message}" }
			} else {
				// Log non-network errors but don't queue
				logDebug { "Scrobble failed (not queuing): ${error?.message}" }
			}
			false
		}
	}

	/**
	 * Process a queued scrobble entry by loading manga from database and calling scrobble.
	 */
	private suspend fun processQueueEntry(scrobbler: Scrobbler, mangaId: Long, chapterId: Long) {
		// Verify scrobbling entity exists
		db.getScrobblingDao().find(scrobbler.scrobblerService.id, mangaId)
			?: throw IllegalStateException("No scrobbling entity found for manga $mangaId")

		// Get manga from database
		val mangaWithTags = db.getMangaDao().find(mangaId)
			?: throw IllegalStateException("Manga $mangaId not found in database")

		// Convert to domain model and scrobble
		val manga = mangaWithTags.toManga()
		scrobbler.scrobble(manga, chapterId)
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	companion object {
		private const val TAG = "ScrobblingManager"
	}
}
