package org.koitharu.kotatsu.core.network

/**
 * Represents the current network quality level.
 * Used for adaptive behavior decisions throughout the app.
 */
enum class NetworkQuality(
	val level: Int,
) {
	/** No network connection */
	OFFLINE(0),
	
	/** Very slow connection - minimize data usage */
	POOR(1),
	
	/** Limited bandwidth - reduce quality/preloading */
	MODERATE(2),
	
	/** Good connection - normal operation */
	GOOD(3),
	
	/** Excellent connection - enable aggressive preloading */
	EXCELLENT(4);

	val isConnected: Boolean
		get() = this != OFFLINE

	val allowsPreloading: Boolean
		get() = level >= GOOD.level

	val allowsHighQuality: Boolean
		get() = level >= MODERATE.level

	val allowsAggressivePreloading: Boolean
		get() = level >= EXCELLENT.level

	companion object {
		fun fromLevel(level: Int): NetworkQuality {
			return entries.find { it.level == level } ?: OFFLINE
		}
	}
}
