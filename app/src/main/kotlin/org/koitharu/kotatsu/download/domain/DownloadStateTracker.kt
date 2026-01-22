package org.koitharu.kotatsu.download.domain

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks partial download state for resume capability.
 *
 * Stores:
 * - Chapter download progress (which pages are complete)
 * - Partial file information (path, expected size, current size)
 * - Download session metadata
 */
@Singleton
class DownloadStateTracker @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	private val prefs: SharedPreferences by lazy {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}

	private val mutex = Mutex()

	/**
	 * Record that a chapter download has started.
	 */
	suspend fun startChapter(
		mangaId: Long,
		chapterId: Long,
		totalPages: Int,
	) = mutex.withLock {
		withContext(Dispatchers.IO) {
			val key = getChapterKey(mangaId, chapterId)
			val state = ChapterDownloadState(
				chapterId = chapterId,
				totalPages = totalPages,
				completedPages = mutableSetOf(),
				startedAt = System.currentTimeMillis(),
			)
			prefs.edit().putString(key, state.toJson()).apply()
		}
	}

	/**
	 * Mark a page as completed.
	 */
	suspend fun markPageComplete(
		mangaId: Long,
		chapterId: Long,
		pageIndex: Int,
	) = mutex.withLock {
		withContext(Dispatchers.IO) {
			val key = getChapterKey(mangaId, chapterId)
			val json = prefs.getString(key, null) ?: return@withContext
			val state = ChapterDownloadState.fromJson(json)
			state.completedPages.add(pageIndex)
			prefs.edit().putString(key, state.toJson()).apply()
		}
	}

	/**
	 * Mark a chapter as fully completed.
	 */
	suspend fun completeChapter(
		mangaId: Long,
		chapterId: Long,
	) = mutex.withLock {
		withContext(Dispatchers.IO) {
			val key = getChapterKey(mangaId, chapterId)
			prefs.edit().remove(key).apply()

			// Add to completed chapters list
			val completedKey = getCompletedChaptersKey(mangaId)
			val completed = getCompletedChapterIds(mangaId).toMutableSet()
			completed.add(chapterId)
			prefs.edit().putString(completedKey, JSONArray(completed.toList()).toString()).apply()
		}
	}

	/**
	 * Get the download state for a chapter.
	 */
	suspend fun getChapterState(
		mangaId: Long,
		chapterId: Long,
	): ChapterDownloadState? = mutex.withLock {
		withContext(Dispatchers.IO) {
			val key = getChapterKey(mangaId, chapterId)
			val json = prefs.getString(key, null) ?: return@withContext null
			ChapterDownloadState.fromJson(json)
		}
	}

	/**
	 * Get completed pages for a chapter (for resume).
	 */
	suspend fun getCompletedPages(
		mangaId: Long,
		chapterId: Long,
	): Set<Int> {
		return getChapterState(mangaId, chapterId)?.completedPages ?: emptySet()
	}

	/**
	 * Get IDs of fully completed chapters.
	 */
	suspend fun getCompletedChapterIds(mangaId: Long): Set<Long> = mutex.withLock {
		withContext(Dispatchers.IO) {
			val key = getCompletedChaptersKey(mangaId)
			val json = prefs.getString(key, null) ?: return@withContext emptySet()
			try {
				val array = JSONArray(json)
				(0 until array.length()).map { array.getLong(it) }.toSet()
			} catch (e: Exception) {
				emptySet()
			}
		}
	}

	/**
	 * Track a partial file download.
	 */
	suspend fun trackPartialFile(
		url: String,
		file: File,
		expectedSize: Long,
	) = mutex.withLock {
		withContext(Dispatchers.IO) {
			val key = getPartialFileKey(url)
			val state = PartialFileState(
				url = url,
				filePath = file.absolutePath,
				expectedSize = expectedSize,
				downloadedSize = file.length(),
				lastModified = System.currentTimeMillis(),
			)
			prefs.edit().putString(key, state.toJson()).apply()
		}
	}

	/**
	 * Get partial file state for resume.
	 */
	suspend fun getPartialFile(url: String): PartialFileState? = mutex.withLock {
		withContext(Dispatchers.IO) {
			val key = getPartialFileKey(url)
			val json = prefs.getString(key, null) ?: return@withContext null
			val state = PartialFileState.fromJson(json)

			// Verify file still exists
			val file = File(state.filePath)
			if (!file.exists()) {
				prefs.edit().remove(key).apply()
				return@withContext null
			}

			// Update with current size
			state.copy(downloadedSize = file.length())
		}
	}

	/**
	 * Remove partial file tracking.
	 */
	suspend fun removePartialFile(url: String) = mutex.withLock {
		withContext(Dispatchers.IO) {
			val key = getPartialFileKey(url)
			prefs.edit().remove(key).apply()
		}
	}

	/**
	 * Clear all tracking data for a manga.
	 */
	suspend fun clearMangaState(mangaId: Long) = mutex.withLock {
		withContext(Dispatchers.IO) {
			val editor = prefs.edit()
			prefs.all.keys
				.filter { it.startsWith("chapter_${mangaId}_") || it == getCompletedChaptersKey(mangaId) }
				.forEach { editor.remove(it) }
			editor.apply()
		}
	}

	/**
	 * Clean up old tracking data (older than 7 days).
	 */
	suspend fun cleanupOldData() = mutex.withLock {
		withContext(Dispatchers.IO) {
			val cutoff = System.currentTimeMillis() - MAX_AGE_MS
			val editor = prefs.edit()
			var removed = 0

			prefs.all.forEach { (key, value) ->
				if (key.startsWith("chapter_") && value is String) {
					try {
						val state = ChapterDownloadState.fromJson(value)
						if (state.startedAt < cutoff) {
							editor.remove(key)
							removed++
						}
					} catch (e: Exception) {
						editor.remove(key)
					}
				} else if (key.startsWith("partial_") && value is String) {
					try {
						val state = PartialFileState.fromJson(value)
						if (state.lastModified < cutoff) {
							editor.remove(key)
							removed++
						}
					} catch (e: Exception) {
						editor.remove(key)
					}
				}
			}

			if (removed > 0) {
				editor.apply()
			}
		}
	}

	private fun getChapterKey(mangaId: Long, chapterId: Long) = "chapter_${mangaId}_$chapterId"
	private fun getCompletedChaptersKey(mangaId: Long) = "completed_$mangaId"
	private fun getPartialFileKey(url: String) = "partial_${url.hashCode()}"

	/**
	 * State of a chapter download in progress.
	 */
	data class ChapterDownloadState(
		val chapterId: Long,
		val totalPages: Int,
		val completedPages: MutableSet<Int>,
		val startedAt: Long,
	) {
		fun toJson(): String {
			return JSONObject().apply {
				put("chapterId", chapterId)
				put("totalPages", totalPages)
				put("completedPages", JSONArray(completedPages.toList()))
				put("startedAt", startedAt)
			}.toString()
		}

		companion object {
			fun fromJson(json: String): ChapterDownloadState {
				val obj = JSONObject(json)
				val pagesArray = obj.getJSONArray("completedPages")
				val pages = (0 until pagesArray.length()).map { pagesArray.getInt(it) }.toMutableSet()
				return ChapterDownloadState(
					chapterId = obj.getLong("chapterId"),
					totalPages = obj.getInt("totalPages"),
					completedPages = pages,
					startedAt = obj.optLong("startedAt", 0L),
				)
			}
		}
	}

	/**
	 * State of a partial file download.
	 */
	data class PartialFileState(
		val url: String,
		val filePath: String,
		val expectedSize: Long,
		val downloadedSize: Long,
		val lastModified: Long,
	) {
		val isComplete: Boolean
			get() = expectedSize > 0 && downloadedSize >= expectedSize

		val progress: Float
			get() = if (expectedSize > 0) downloadedSize.toFloat() / expectedSize else 0f

		fun toJson(): String {
			return JSONObject().apply {
				put("url", url)
				put("filePath", filePath)
				put("expectedSize", expectedSize)
				put("downloadedSize", downloadedSize)
				put("lastModified", lastModified)
			}.toString()
		}

		companion object {
			fun fromJson(json: String): PartialFileState {
				val obj = JSONObject(json)
				return PartialFileState(
					url = obj.getString("url"),
					filePath = obj.getString("filePath"),
					expectedSize = obj.getLong("expectedSize"),
					downloadedSize = obj.getLong("downloadedSize"),
					lastModified = obj.optLong("lastModified", 0L),
				)
			}
		}
	}

	companion object {
		private const val PREFS_NAME = "download_state_tracker"
		private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
	}
}
