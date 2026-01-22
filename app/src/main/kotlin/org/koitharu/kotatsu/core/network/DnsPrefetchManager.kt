package org.koitharu.kotatsu.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages DNS prefetching for manga sources.
 *
 * Automatically prefetches DNS for:
 * - Common CDN domains used by multiple sources
 * - Source-specific domains when sources are accessed
 */
@Singleton
class DnsPrefetchManager @Inject constructor(
	private val dnsPrefetcher: DnsPrefetcher,
	private val sourcesRepository: MangaSourcesRepository,
	private val networkState: NetworkState,
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var isInitialized = false

	/**
	 * Initialize DNS prefetching.
	 * Should be called during app startup.
	 */
	fun initialize() {
		if (isInitialized) return
		isInitialized = true

		// Start prefetcher with common domains
		dnsPrefetcher.start()
	}

	/**
	 * Prefetch DNS for a specific source when the user opens it.
	 */
	fun prefetchForSource(source: MangaSource) {
		if (!networkState.isOnline()) return

		// Prefetch common image CDN domains for this source
		val cdnDomains = getSourceCdnDomains(source)
		if (cdnDomains.isNotEmpty()) {
			scope.launch {
				dnsPrefetcher.prefetch(*cdnDomains.toTypedArray())
			}
		}
	}

	/**
	 * Prefetch DNS for URLs that are about to be loaded.
	 */
	fun prefetchForUrls(vararg urls: String) {
		if (!networkState.isOnline()) return
		scope.launch {
			dnsPrefetcher.prefetchFromUrls(*urls)
		}
	}

	/**
	 * Get cache statistics for debugging.
	 */
	fun getCacheStats(): DnsPrefetcher.CacheStats {
		return dnsPrefetcher.getCacheStats()
	}

	/**
	 * Clear the DNS cache.
	 */
	fun clearCache() {
		dnsPrefetcher.clearCache()
	}

	private fun getSourceCdnDomains(source: MangaSource): Set<String> {
		// Common CDN domains used by manga sites
		// This can be extended based on known source patterns
		return when {
			source.name.contains("mangadex", ignoreCase = true) -> setOf(
				"uploads.mangadex.org",
				"api.mangadex.org",
			)
			source.name.contains("webtoon", ignoreCase = true) -> setOf(
				"webtoon-phinf.pstatic.net",
			)
			source.name.contains("mangakakalot", ignoreCase = true) -> setOf(
				"cm.blazefast.co",
				"avt.mkklcdnv6temp.com",
			)
			source.name.contains("mangasee", ignoreCase = true) -> setOf(
				"temp.compsci88.com",
			)
			else -> emptySet()
		}
	}
}
