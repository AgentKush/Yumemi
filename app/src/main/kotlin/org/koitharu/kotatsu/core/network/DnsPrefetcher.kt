package org.koitharu.kotatsu.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Dns
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.os.NetworkState
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DNS Prefetcher that proactively resolves domain names for faster first connections.
 *
 * Features:
 * - Caches DNS results with configurable TTL
 * - Prefetches domains for enabled manga sources
 * - Background refresh before entries expire
 * - Observable prefetch status for debugging
 * - Integrates with existing DoH/DNS infrastructure
 */
@Singleton
class DnsPrefetcher @Inject constructor(
	private val networkState: NetworkState,
) : Dns {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val cache = ConcurrentHashMap<String, DnsCacheEntry>()
	private val refreshMutex = Mutex()
	private val pendingPrefetches: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

	private var delegate: Dns = Dns.SYSTEM
	private var isRunning = false

	/**
	 * Common domains that should be prefetched.
	 * These include CDN providers and common manga hosting services.
	 */
	private val commonDomains = setOf(
		// CDN providers commonly used by manga sites
		"cdn.jsdelivr.net",
		"cdnjs.cloudflare.com",
		"fonts.googleapis.com",
		"fonts.gstatic.com",
		
		// Image hosting services
		"i.imgur.com",
		"imgur.com",
		
		// Common infrastructure
		"cloudflare.com",
		"fastly.net",
	)

	/**
	 * Set the delegate DNS resolver (e.g., DoHManager).
	 */
	fun setDelegate(dns: Dns) {
		delegate = dns
	}

	/**
	 * Start the prefetcher with a list of domains to prefetch.
	 */
	fun start(domains: Set<String> = emptySet()) {
		if (isRunning) return
		isRunning = true

		scope.launch {
			// Initial prefetch
			val allDomains = commonDomains + domains
			prefetchDomains(allDomains)

			// Background refresh loop
			while (isActive && isRunning) {
				delay(REFRESH_INTERVAL_MS)
				refreshExpiringSoon()
			}
		}
	}

	/**
	 * Stop the prefetcher.
	 */
	fun stop() {
		isRunning = false
	}

	/**
	 * Add domains to prefetch queue.
	 */
	fun prefetch(vararg domains: String) {
		if (!networkState.isOnline()) return

		scope.launch {
			for (domain in domains) {
				prefetchSingle(domain)
			}
		}
	}

	/**
	 * Add domains from URLs to prefetch queue.
	 */
	fun prefetchFromUrls(vararg urls: String) {
		val domains = urls.mapNotNull { extractDomain(it) }.toSet()
		if (domains.isNotEmpty()) {
			prefetch(*domains.toTypedArray())
		}
	}

	/**
	 * DNS lookup with cache support.
	 */
	override fun lookup(hostname: String): List<InetAddress> {
		// Check cache first
		val cached = cache[hostname]
		if (cached != null && !cached.isExpired()) {
			logDebug { "DNS cache hit: $hostname -> ${cached.addresses.size} addresses" }
			return cached.addresses
		}

		// Cache miss or expired, do actual lookup
		return try {
			val addresses = delegate.lookup(hostname)
			cache[hostname] = DnsCacheEntry(
				hostname = hostname,
				addresses = addresses,
				timestamp = System.currentTimeMillis(),
			)
			logDebug { "DNS lookup: $hostname -> ${addresses.size} addresses" }
			addresses
		} catch (e: UnknownHostException) {
			// Remove from cache on failure
			cache.remove(hostname)
			throw e
		}
	}

	/**
	 * Get current cache statistics.
	 */
	fun getCacheStats(): CacheStats {
		val now = System.currentTimeMillis()
		var validCount = 0
		var expiredCount = 0
		var totalAddresses = 0

		for (entry in cache.values) {
			if (entry.isExpired(now)) {
				expiredCount++
			} else {
				validCount++
				totalAddresses += entry.addresses.size
			}
		}

		return CacheStats(
			totalEntries = cache.size,
			validEntries = validCount,
			expiredEntries = expiredCount,
			totalAddresses = totalAddresses,
			pendingPrefetches = pendingPrefetches.size,
		)
	}

	/**
	 * Clear the DNS cache.
	 */
	fun clearCache() {
		cache.clear()
		logDebug { "DNS cache cleared" }
	}

	/**
	 * Check if a domain is cached and valid.
	 */
	fun isCached(hostname: String): Boolean {
		val entry = cache[hostname] ?: return false
		return !entry.isExpired()
	}

	private suspend fun prefetchDomains(domains: Set<String>) {
		if (!networkState.isOnline()) return

		for (domain in domains) {
			prefetchSingle(domain)
			// Small delay to avoid flooding
			delay(50)
		}
	}

	private suspend fun prefetchSingle(hostname: String) {
		// Skip if already cached and valid
		val cached = cache[hostname]
		if (cached != null && !cached.isExpiringSoon()) {
			return
		}

		// Skip if already pending
		if (!pendingPrefetches.add(hostname)) {
			return
		}

		try {
			val addresses = delegate.lookup(hostname)
			cache[hostname] = DnsCacheEntry(
				hostname = hostname,
				addresses = addresses,
				timestamp = System.currentTimeMillis(),
			)
			logDebug { "DNS prefetch: $hostname -> ${addresses.size} addresses" }
		} catch (e: UnknownHostException) {
			logDebug { "DNS prefetch failed: $hostname - ${e.message}" }
		} catch (e: Exception) {
			logDebug { "DNS prefetch error: $hostname - ${e.message}" }
		} finally {
			pendingPrefetches.remove(hostname)
		}
	}

	private suspend fun refreshExpiringSoon() {
		if (!networkState.isOnline()) return

		refreshMutex.withLock {
			val now = System.currentTimeMillis()
			val toRefresh = cache.values.filter { it.isExpiringSoon(now) }.map { it.hostname }

			for (hostname in toRefresh) {
				prefetchSingle(hostname)
				delay(100)
			}

			// Clean up expired entries
			cache.entries.removeIf { it.value.isExpired(now) }
		}
	}

	private fun extractDomain(url: String): String? {
		return try {
			val withoutProtocol = url
				.removePrefix("https://")
				.removePrefix("http://")
			val endIndex = withoutProtocol.indexOfAny(charArrayOf('/', ':', '?', '#'))
			if (endIndex > 0) {
				withoutProtocol.substring(0, endIndex)
			} else {
				withoutProtocol
			}
		} catch (e: Exception) {
			null
		}
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	/**
	 * Cache entry for a DNS lookup result.
	 */
	private data class DnsCacheEntry(
		val hostname: String,
		val addresses: List<InetAddress>,
		val timestamp: Long,
	) {
		fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
			return now - timestamp > TTL_MS
		}

		fun isExpiringSoon(now: Long = System.currentTimeMillis()): Boolean {
			return now - timestamp > TTL_MS - REFRESH_BEFORE_EXPIRY_MS
		}
	}

	/**
	 * Statistics about the DNS cache.
	 */
	data class CacheStats(
		val totalEntries: Int,
		val validEntries: Int,
		val expiredEntries: Int,
		val totalAddresses: Int,
		val pendingPrefetches: Int,
	)

	companion object {
		private const val TAG = "DnsPrefetcher"
		private const val TTL_MS = 5 * 60 * 1000L // 5 minutes
		private const val REFRESH_BEFORE_EXPIRY_MS = 60 * 1000L // Refresh 1 minute before expiry
		private const val REFRESH_INTERVAL_MS = 30 * 1000L // Check every 30 seconds
	}
}
