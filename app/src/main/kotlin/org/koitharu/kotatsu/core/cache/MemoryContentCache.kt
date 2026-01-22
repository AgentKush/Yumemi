package org.koitharu.kotatsu.core.cache

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.isLowRamDevice
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for manga content with configurable TTL.
 *
 * Cache TTL settings can be configured in AppSettings:
 * - Details cache: default 5 minutes (configurable 1-60 min)
 * - Pages cache: default 10 minutes (configurable 1-120 min)
 * - Related manga cache: default 10 minutes (configurable 1-120 min)
 *
 * Note: Changes to TTL settings require app restart to take effect.
 */
@Singleton
class MemoryContentCache @Inject constructor(
	application: Application,
	settings: AppSettings,
) : ComponentCallbacks2 {

	private val isLowRam = application.isLowRamDevice()

	private val detailsCache = ExpiringLruCache<SafeDeferred<Manga>>(
		maxSize = if (isLowRam) 1 else 4,
		lifetime = settings.cacheDetailsTtlMinutes.toLong(),
		timeUnit = TimeUnit.MINUTES,
	)

	private val pagesCache = ExpiringLruCache<SafeDeferred<List<MangaPage>>>(
		maxSize = if (isLowRam) 1 else 4,
		lifetime = settings.cachePagesTtlMinutes.toLong(),
		timeUnit = TimeUnit.MINUTES,
	)

	private val relatedMangaCache = ExpiringLruCache<SafeDeferred<List<Manga>>>(
		maxSize = if (isLowRam) 1 else 3,
		lifetime = settings.cacheRelatedTtlMinutes.toLong(),
		timeUnit = TimeUnit.MINUTES,
	)

	init {
		application.registerComponentCallbacks(this)
	}

	suspend fun getDetails(source: MangaSource, url: String): Manga? {
		return detailsCache[Key(source, url)]?.awaitOrNull()
	}

	fun putDetails(source: MangaSource, url: String, details: SafeDeferred<Manga>) {
		detailsCache[Key(source, url)] = details
	}

	suspend fun getPages(source: MangaSource, url: String): List<MangaPage>? {
		return pagesCache[Key(source, url)]?.awaitOrNull()
	}

	fun putPages(source: MangaSource, url: String, pages: SafeDeferred<List<MangaPage>>) {
		pagesCache[Key(source, url)] = pages
	}

	suspend fun getRelatedManga(source: MangaSource, url: String): List<Manga>? {
		return relatedMangaCache[Key(source, url)]?.awaitOrNull()
	}

	fun putRelatedManga(source: MangaSource, url: String, related: SafeDeferred<List<Manga>>) {
		relatedMangaCache[Key(source, url)] = related
	}

	fun clear(source: MangaSource) {
		clearCache(detailsCache, source)
		clearCache(pagesCache, source)
		clearCache(relatedMangaCache, source)
	}

	override fun onConfigurationChanged(newConfig: Configuration) = Unit

	override fun onLowMemory() = Unit

	override fun onTrimMemory(level: Int) {
		trimCache(detailsCache, level)
		trimCache(pagesCache, level)
		trimCache(relatedMangaCache, level)
	}

	private fun trimCache(cache: ExpiringLruCache<*>, level: Int) {
		when (level) {
			ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
			ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
			ComponentCallbacks2.TRIM_MEMORY_MODERATE -> cache.clear()

			ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
			ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
			ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> cache.trimToSize(1)

			else -> cache.trimToSize(cache.maxSize / 2)
		}
	}

	private fun clearCache(cache: ExpiringLruCache<*>, source: MangaSource) {
		cache.removeAll(source)
	}

	data class Key(
		val source: MangaSource,
		val url: String,
	)
}
