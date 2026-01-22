package org.koitharu.kotatsu.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages DNS prefetching for manga sources.
 *
 * Automatically prefetches DNS for:
 * - All enabled manga sources when app starts
 * - Sources when they become enabled
 * - Common CDN domains used by multiple sources
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

		// Observe enabled sources and prefetch their domains
		sourcesRepository.observeEnabledSources()
			.onEach { sources ->
				if (networkState.isOnline()) {
					prefetchSourceDomains(sources.map { it.mangaSource })
				}
			}
			.launchIn(scope)

		// Initial prefetch
		scope.launch {
			val enabledSources = sourcesRepository.getEnabledSources()
			prefetchSourceDomains(enabledSources)
		}
	}

	/**
	 * Prefetch DNS for a specific source when the user opens it.
	 */
	fun prefetchForSource(source: MangaSource) {
		val domain = extractDomainFromSource(source) ?: return
		dnsPrefetcher.prefetch(domain)

		// Also prefetch common image CDN domains for this source
		val cdnDomains = getSourceCdnDomains(source)
		if (cdnDomains.isNotEmpty()) {
			dnsPrefetcher.prefetch(*cdnDomains.toTypedArray())
		}
	}

	/**
	 * Prefetch DNS for URLs that are about to be loaded.
	 */
	fun prefetchForUrls(vararg urls: String) {
		dnsPrefetcher.prefetchFromUrls(*urls)
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

	private fun prefetchSourceDomains(sources: List<MangaSource>) {
		val domains = sources.mapNotNull { extractDomainFromSource(it) }.toSet()
		if (domains.isNotEmpty()) {
			dnsPrefetcher.prefetch(*domains.toTypedArray())
		}
	}

	private fun extractDomainFromSource(source: MangaSource): String? {
		if (source !is MangaParserSource) return null

		// Extract domain from source's known domains
		return try {
			source.domains.firstOrNull()
		} catch (e: Exception) {
			null
		}
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
			else -> emptySet()
		}
	}
}
