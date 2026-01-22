package org.koitharu.kotatsu.core.network.imageproxy

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.BuildConfig
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a persistent blacklist of hosts that have blocked proxy requests.
 * Blacklisted hosts are stored in SharedPreferences with timestamps for TTL expiration.
 *
 * Features:
 * - Persistent storage across app restarts
 * - Time-based expiration (default: 6 hours)
 * - Thread-safe operations
 * - Manual clear capability
 */
@Singleton
class ProxyBlacklistManager @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val blacklist = mutableSetOf<String>()
	private val timestamps = mutableMapOf<String, Long>()
	private val lock = Any()

	private val prefs by lazy {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}

	init {
		loadFromStorage()
	}

	/**
	 * Check if a host is blacklisted (and not expired).
	 */
	fun isBlacklisted(host: String): Boolean {
		synchronized(lock) {
			cleanupExpired()
			return host in blacklist
		}
	}

	/**
	 * Add a host to the blacklist.
	 */
	fun addToBlacklist(host: String) {
		synchronized(lock) {
			if (blacklist.add(host)) {
				timestamps[host] = System.currentTimeMillis()
				saveToStorage()
				logDebug { "Added to proxy blacklist: $host" }
			}
		}
	}

	/**
	 * Remove a specific host from the blacklist.
	 */
	fun removeFromBlacklist(host: String) {
		synchronized(lock) {
			if (blacklist.remove(host)) {
				timestamps.remove(host)
				saveToStorage()
				logDebug { "Removed from proxy blacklist: $host" }
			}
		}
	}

	/**
	 * Clear all blacklisted hosts.
	 */
	fun clearBlacklist() {
		synchronized(lock) {
			blacklist.clear()
			timestamps.clear()
			prefs.edit()
				.remove(KEY_HOSTS)
				.remove(KEY_TIMESTAMPS)
				.apply()
			logDebug { "Proxy blacklist cleared" }
		}
	}

	/**
	 * Get the current blacklist size.
	 */
	val size: Int
		get() = synchronized(lock) {
			cleanupExpired()
			blacklist.size
		}

	private fun cleanupExpired() {
		val now = System.currentTimeMillis()
		val expired = timestamps.filter { (_, timestamp) ->
			now - timestamp > BLACKLIST_TTL_MS
		}.keys

		if (expired.isNotEmpty()) {
			expired.forEach { host ->
				blacklist.remove(host)
				timestamps.remove(host)
				logDebug { "Expired from proxy blacklist: $host" }
			}
			saveToStorage()
		}
	}

	private fun saveToStorage() {
		val hostSet = blacklist.toSet()
		val timestampSet = timestamps.map { (host, time) -> "$host:$time" }.toSet()
		prefs.edit()
			.putStringSet(KEY_HOSTS, hostSet)
			.putStringSet(KEY_TIMESTAMPS, timestampSet)
			.apply()
	}

	private fun loadFromStorage() {
		val hosts = prefs.getStringSet(KEY_HOSTS, emptySet()) ?: emptySet()
		val timestampEntries = prefs.getStringSet(KEY_TIMESTAMPS, emptySet()) ?: emptySet()

		// Parse timestamps
		val timestampMap = timestampEntries.mapNotNull { entry ->
			val parts = entry.split(":")
			if (parts.size >= 2) {
				val host = parts.dropLast(1).joinToString(":") // Handle hosts with colons (IPv6)
				val time = parts.last().toLongOrNull()
				if (time != null) host to time else null
			} else null
		}.toMap()

		// Load only non-expired entries
		val now = System.currentTimeMillis()
		hosts.forEach { host ->
			val timestamp = timestampMap[host] ?: now
			if (now - timestamp <= BLACKLIST_TTL_MS) {
				blacklist.add(host)
				timestamps[host] = timestamp
			}
		}

		logDebug { "Loaded ${blacklist.size} hosts from proxy blacklist" }
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	companion object {
		private const val TAG = "ProxyBlacklistManager"
		private const val PREFS_NAME = "image_proxy_blacklist"
		private const val KEY_HOSTS = "hosts"
		private const val KEY_TIMESTAMPS = "timestamps"

		/** Blacklist entries expire after 6 hours */
		val BLACKLIST_TTL_MS = TimeUnit.HOURS.toMillis(6)
	}
}
