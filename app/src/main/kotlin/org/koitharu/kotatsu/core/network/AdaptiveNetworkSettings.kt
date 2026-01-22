package org.koitharu.kotatsu.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides adaptive network settings based on current network quality.
 * Use this class to get dynamic settings that adjust to network conditions.
 */
@Singleton
class AdaptiveNetworkSettings @Inject constructor(
	private val networkQualityMonitor: NetworkQualityMonitor,
) {
	/**
	 * Current network quality level.
	 */
	val quality: Flow<NetworkQuality>
		get() = networkQualityMonitor.quality

	/**
	 * Current quality value (for synchronous access).
	 */
	val currentQuality: NetworkQuality
		get() = networkQualityMonitor.quality.value

	/**
	 * Observable settings that update when network quality changes.
	 */
	val adaptiveSettings: Flow<Settings> = networkQualityMonitor.quality.map { quality ->
		Settings(
			quality = quality,
			connectTimeoutMs = getConnectTimeout(quality),
			readTimeoutMs = getReadTimeout(quality),
			writeTimeoutMs = getWriteTimeout(quality),
			maxConcurrentDownloads = getMaxConcurrentDownloads(quality),
			preloadPageCount = getPreloadPageCount(quality),
			imageQualityReduction = getImageQualityReduction(quality),
			retryCount = getRetryCount(quality),
			retryDelayMs = getRetryDelay(quality),
			enableAggressiveCache = shouldEnableAggressiveCache(quality),
		)
	}

	/**
	 * Get current settings snapshot.
	 */
	fun getCurrentSettings(): Settings {
		val quality = currentQuality
		return Settings(
			quality = quality,
			connectTimeoutMs = getConnectTimeout(quality),
			readTimeoutMs = getReadTimeout(quality),
			writeTimeoutMs = getWriteTimeout(quality),
			maxConcurrentDownloads = getMaxConcurrentDownloads(quality),
			preloadPageCount = getPreloadPageCount(quality),
			imageQualityReduction = getImageQualityReduction(quality),
			retryCount = getRetryCount(quality),
			retryDelayMs = getRetryDelay(quality),
			enableAggressiveCache = shouldEnableAggressiveCache(quality),
		)
	}

	/**
	 * Report a successful download for bandwidth estimation.
	 */
	fun reportDownload(bytes: Long, durationMs: Long) {
		networkQualityMonitor.reportDownload(bytes, durationMs)
	}

	private fun getConnectTimeout(quality: NetworkQuality): Long {
		return when (quality) {
			NetworkQuality.EXCELLENT -> 10_000L
			NetworkQuality.GOOD -> 15_000L
			NetworkQuality.MODERATE -> 20_000L
			NetworkQuality.POOR -> 30_000L
			NetworkQuality.OFFLINE -> 15_000L
		}
	}

	private fun getReadTimeout(quality: NetworkQuality): Long {
		return when (quality) {
			NetworkQuality.EXCELLENT -> 30_000L
			NetworkQuality.GOOD -> 45_000L
			NetworkQuality.MODERATE -> 60_000L
			NetworkQuality.POOR -> 90_000L
			NetworkQuality.OFFLINE -> 60_000L
		}
	}

	private fun getWriteTimeout(quality: NetworkQuality): Long {
		return when (quality) {
			NetworkQuality.EXCELLENT -> 15_000L
			NetworkQuality.GOOD -> 20_000L
			NetworkQuality.MODERATE -> 25_000L
			NetworkQuality.POOR -> 40_000L
			NetworkQuality.OFFLINE -> 20_000L
		}
	}

	private fun getMaxConcurrentDownloads(quality: NetworkQuality): Int {
		return when (quality) {
			NetworkQuality.EXCELLENT -> 6
			NetworkQuality.GOOD -> 4
			NetworkQuality.MODERATE -> 2
			NetworkQuality.POOR -> 1
			NetworkQuality.OFFLINE -> 0
		}
	}

	private fun getPreloadPageCount(quality: NetworkQuality): Int {
		return when (quality) {
			NetworkQuality.EXCELLENT -> 5
			NetworkQuality.GOOD -> 3
			NetworkQuality.MODERATE -> 1
			NetworkQuality.POOR -> 0
			NetworkQuality.OFFLINE -> 0
		}
	}

	/**
	 * Returns image quality reduction factor (0-100).
	 * 0 = full quality, 100 = maximum reduction
	 */
	private fun getImageQualityReduction(quality: NetworkQuality): Int {
		return when (quality) {
			NetworkQuality.EXCELLENT -> 0
			NetworkQuality.GOOD -> 0
			NetworkQuality.MODERATE -> 20
			NetworkQuality.POOR -> 40
			NetworkQuality.OFFLINE -> 0
		}
	}

	private fun getRetryCount(quality: NetworkQuality): Int {
		return when (quality) {
			NetworkQuality.EXCELLENT -> 2
			NetworkQuality.GOOD -> 3
			NetworkQuality.MODERATE -> 4
			NetworkQuality.POOR -> 5
			NetworkQuality.OFFLINE -> 3
		}
	}

	private fun getRetryDelay(quality: NetworkQuality): Long {
		return when (quality) {
			NetworkQuality.EXCELLENT -> 500L
			NetworkQuality.GOOD -> 1_000L
			NetworkQuality.MODERATE -> 2_000L
			NetworkQuality.POOR -> 4_000L
			NetworkQuality.OFFLINE -> 1_000L
		}
	}

	private fun shouldEnableAggressiveCache(quality: NetworkQuality): Boolean {
		return when (quality) {
			NetworkQuality.EXCELLENT -> false
			NetworkQuality.GOOD -> false
			NetworkQuality.MODERATE -> true
			NetworkQuality.POOR -> true
			NetworkQuality.OFFLINE -> true
		}
	}

	/**
	 * Adaptive network settings snapshot.
	 */
	data class Settings(
		val quality: NetworkQuality,
		val connectTimeoutMs: Long,
		val readTimeoutMs: Long,
		val writeTimeoutMs: Long,
		val maxConcurrentDownloads: Int,
		val preloadPageCount: Int,
		val imageQualityReduction: Int,
		val retryCount: Int,
		val retryDelayMs: Long,
		val enableAggressiveCache: Boolean,
	) {
		/**
		 * Check if downloads should be allowed.
		 */
		val allowsDownloads: Boolean
			get() = quality.isConnected

		/**
		 * Check if preloading is recommended.
		 */
		val allowsPreloading: Boolean
			get() = quality.allowsPreloading

		/**
		 * Check if high quality images should be used.
		 */
		val allowsHighQuality: Boolean
			get() = quality.allowsHighQuality
	}
}
