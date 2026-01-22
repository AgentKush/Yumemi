package org.koitharu.kotatsu.core.parser

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles automatic mirror switching for manga sources when the primary domain fails.
 * Features:
 * - Persistent blacklist that survives app restarts
 * - Time-based expiration of blacklist entries (24 hours default)
 * - Manual blacklist clearing capability
 */
@Singleton
class MirrorSwitcher @Inject constructor(
	private val settings: AppSettings,
	@MangaHttpClient private val okHttpClient: OkHttpClient,
	@ApplicationContext private val context: Context,
) {

	private val blacklist = EnumSet.noneOf(MangaParserSource::class.java)
	private val blacklistTimestamps = mutableMapOf<MangaParserSource, Long>()
	private val mutex: Mutex = Mutex()
	private val prefs by lazy {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}

	init {
		loadBlacklistFromStorage()
	}

	val isEnabled: Boolean
		get() = settings.isMirrorSwitchingEnabled

	/**
	 * Get the list of currently blacklisted sources (excluding expired entries).
	 */
	val blacklistedSources: Set<MangaParserSource>
		get() {
			cleanupExpiredEntries()
			return blacklist.toSet()
		}

	suspend fun <T : Any> trySwitchMirror(repository: ParserMangaRepository, loader: suspend () -> T?): T? {
		val source = repository.source
		cleanupExpiredEntries()
		if (!isEnabled || source in blacklist) {
			return null
		}
		val availableMirrors = repository.domains
		val currentHost = repository.domain
		if (availableMirrors.size <= 1 || currentHost !in availableMirrors) {
			return null
		}
		mutex.withLock {
			if (source in blacklist) {
				return null
			}
			logd { "Looking for mirrors for ${source}..." }
			findRedirect(repository)?.let { mirror ->
				repository.domain = mirror
				runCatchingCancellable {
					loader()?.takeIfValid()
				}.getOrNull()?.let {
					logd { "Found redirect for $source: $mirror" }
					return it
				}
			}
			for (mirror in availableMirrors) {
				repository.domain = mirror
				runCatchingCancellable {
					loader()?.takeIfValid()
				}.getOrNull()?.let {
					logd { "Found mirror for $source: $mirror" }
					return it
				}
			}
			repository.domain = currentHost // rollback
			addToBlacklist(source)
			logd { "$source blacklisted" }
			return null
		}
	}

	suspend fun findRedirect(repository: ParserMangaRepository): String? {
		if (!isEnabled) {
			return null
		}
		val currentHost = repository.domain
		val newHost = okHttpClient.newCall(
			Request.Builder()
				.url("https://$currentHost")
				.head()
				.build(),
		).await().use {
			if (it.isSuccessful) {
				it.request.url.host
			} else {
				null
			}
		}
		return if (newHost != currentHost) {
			newHost
		} else {
			null
		}
	}

	/**
	 * Clear all blacklisted sources, allowing them to be retried.
	 * Useful when mirrors may have come back online.
	 */
	fun clearBlacklist() {
		blacklist.clear()
		blacklistTimestamps.clear()
		prefs.edit()
			.remove(KEY_BLACKLIST)
			.remove(KEY_BLACKLIST_TIMESTAMPS)
			.apply()
		logd { "Blacklist cleared" }
	}

	/**
	 * Remove a specific source from the blacklist.
	 */
	fun removeFromBlacklist(source: MangaParserSource) {
		if (blacklist.remove(source)) {
			blacklistTimestamps.remove(source)
			saveBlacklistToStorage()
			logd { "$source removed from blacklist" }
		}
	}

	private fun addToBlacklist(source: MangaParserSource) {
		blacklist.add(source)
		blacklistTimestamps[source] = System.currentTimeMillis()
		saveBlacklistToStorage()
	}

	private fun cleanupExpiredEntries() {
		val now = System.currentTimeMillis()
		val expiredSources = blacklistTimestamps.filter { (_, timestamp) ->
			now - timestamp > BLACKLIST_TTL_MS
		}.keys
		if (expiredSources.isNotEmpty()) {
			expiredSources.forEach { source ->
				blacklist.remove(source)
				blacklistTimestamps.remove(source)
				logd { "$source expired from blacklist" }
			}
			saveBlacklistToStorage()
		}
	}

	private fun saveBlacklistToStorage() {
		val sourceNames = blacklist.map { it.name }.toSet()
		val timestamps = blacklistTimestamps.map { (source, time) -> "${source.name}:$time" }.toSet()
		prefs.edit()
			.putStringSet(KEY_BLACKLIST, sourceNames)
			.putStringSet(KEY_BLACKLIST_TIMESTAMPS, timestamps)
			.apply()
	}

	private fun loadBlacklistFromStorage() {
		val sourceNames = prefs.getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet()
		val timestamps = prefs.getStringSet(KEY_BLACKLIST_TIMESTAMPS, emptySet()) ?: emptySet()

		// Parse timestamps map
		val timestampMap = timestamps.mapNotNull { entry ->
			val parts = entry.split(":")
			if (parts.size == 2) {
				try {
					parts[0] to parts[1].toLong()
				} catch (e: NumberFormatException) {
					null
				}
			} else null
		}.toMap()

		// Load sources with valid timestamps
		val now = System.currentTimeMillis()
		sourceNames.forEach { name ->
			try {
				val source = MangaParserSource.valueOf(name)
				val timestamp = timestampMap[name] ?: now
				// Only load if not expired
				if (now - timestamp <= BLACKLIST_TTL_MS) {
					blacklist.add(source)
					blacklistTimestamps[source] = timestamp
				}
			} catch (e: IllegalArgumentException) {
				// Source no longer exists, skip it
				logd { "Unknown source in blacklist: $name" }
			}
		}
		logd { "Loaded ${blacklist.size} sources from persistent blacklist" }
	}

	private fun <T : Any> T.takeIfValid() = takeIf {
		when (it) {
			is Collection<*> -> it.isNotEmpty()
			else -> true
		}
	}

	private companion object {

		const val TAG = "MirrorSwitcher"
		const val PREFS_NAME = "mirror_switcher"
		const val KEY_BLACKLIST = "blacklist"
		const val KEY_BLACKLIST_TIMESTAMPS = "blacklist_timestamps"
		
		/** Blacklist entries expire after 24 hours */
		val BLACKLIST_TTL_MS = TimeUnit.HOURS.toMillis(24)

		inline fun logd(message: () -> String) {
			if (BuildConfig.DEBUG) {
				Log.d(TAG, message())
			}
		}
	}
}
