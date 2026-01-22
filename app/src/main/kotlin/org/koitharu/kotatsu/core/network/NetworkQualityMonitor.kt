package org.koitharu.kotatsu.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.util.ext.connectivityManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Monitors network quality and provides adaptive behavior recommendations.
 *
 * Features:
 * - Real-time network quality assessment
 * - Bandwidth estimation based on recent downloads
 * - Latency tracking
 * - Cellular network type detection (2G/3G/4G/5G)
 * - Observable network quality state
 */
@Singleton
class NetworkQualityMonitor @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val connectivityManager = context.connectivityManager

	private val _quality = MutableStateFlow(NetworkQuality.GOOD)
	val quality: StateFlow<NetworkQuality> = _quality.asStateFlow()

	private val _bandwidthKbps = MutableStateFlow(0)
	val bandwidthKbps: StateFlow<Int> = _bandwidthKbps.asStateFlow()

	private val _latencyMs = MutableStateFlow(0L)
	val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

	// Sliding window for bandwidth samples
	private val bandwidthSamples = mutableListOf<Int>()
	private val maxSamples = 10

	// For tracking download speeds
	private val bytesDownloaded = AtomicLong(0L)
	private val downloadStartTime = AtomicLong(0L)

	private var isMonitoring = false
	private val callback = NetworkCallbackImpl()

	init {
		startMonitoring()
	}

	/**
	 * Start monitoring network quality.
	 */
	@Synchronized
	fun startMonitoring() {
		if (isMonitoring) return
		isMonitoring = true

		val request = NetworkRequest.Builder()
			.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
			.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
			.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
			.addTransportType(NetworkCapabilities.TRANSPORT_VPN)
			.build()

		try {
			connectivityManager.registerNetworkCallback(request, callback)
		} catch (e: Exception) {
			logDebug { "Failed to register network callback: ${e.message}" }
		}

		// Initial assessment
		assessNetworkQuality()

		// Periodic latency checks
		scope.launch {
			while (isActive) {
				measureLatency()
				delay(LATENCY_CHECK_INTERVAL_MS)
			}
		}
	}

	/**
	 * Stop monitoring network quality.
	 */
	@Synchronized
	fun stopMonitoring() {
		if (!isMonitoring) return
		isMonitoring = false

		try {
			connectivityManager.unregisterNetworkCallback(callback)
		} catch (e: Exception) {
			logDebug { "Failed to unregister network callback: ${e.message}" }
		}
	}

	/**
	 * Report bytes downloaded to help estimate bandwidth.
	 * Call this when downloading images, pages, or other content.
	 */
	fun reportDownload(bytes: Long, durationMs: Long) {
		if (durationMs <= 0 || bytes <= 0) return

		val kbps = ((bytes * 8 * 1000) / (durationMs * 1024)).toInt()
		addBandwidthSample(kbps)

		logDebug { "Download reported: ${bytes}B in ${durationMs}ms = ${kbps}kbps" }
	}

	/**
	 * Start tracking a download.
	 */
	fun startDownloadTracking() {
		downloadStartTime.set(System.currentTimeMillis())
		bytesDownloaded.set(0)
	}

	/**
	 * Add bytes to the current download tracking.
	 */
	fun addDownloadedBytes(bytes: Long) {
		bytesDownloaded.addAndGet(bytes)
	}

	/**
	 * End tracking and report the download.
	 */
	fun endDownloadTracking() {
		val start = downloadStartTime.getAndSet(0)
		val bytes = bytesDownloaded.getAndSet(0)
		if (start > 0 && bytes > 0) {
			val duration = System.currentTimeMillis() - start
			reportDownload(bytes, duration)
		}
	}

	/**
	 * Get recommended concurrent connection limit based on network quality.
	 */
	fun getRecommendedConcurrency(): Int {
		return when (_quality.value) {
			NetworkQuality.EXCELLENT -> 6
			NetworkQuality.GOOD -> 4
			NetworkQuality.MODERATE -> 2
			NetworkQuality.POOR -> 1
			NetworkQuality.OFFLINE -> 0
		}
	}

	/**
	 * Get recommended timeout multiplier based on network quality.
	 */
	fun getTimeoutMultiplier(): Float {
		return when (_quality.value) {
			NetworkQuality.EXCELLENT -> 0.8f
			NetworkQuality.GOOD -> 1.0f
			NetworkQuality.MODERATE -> 1.5f
			NetworkQuality.POOR -> 2.5f
			NetworkQuality.OFFLINE -> 1.0f
		}
	}

	/**
	 * Check if preloading should be enabled.
	 */
	fun shouldPreload(): Boolean {
		return _quality.value.allowsPreloading
	}

	/**
	 * Check if high quality images should be used.
	 */
	fun shouldUseHighQuality(): Boolean {
		return _quality.value.allowsHighQuality
	}

	/**
	 * Get the number of pages to preload based on network quality.
	 */
	fun getPreloadPageCount(): Int {
		return when (_quality.value) {
			NetworkQuality.EXCELLENT -> 5
			NetworkQuality.GOOD -> 3
			NetworkQuality.MODERATE -> 1
			NetworkQuality.POOR -> 0
			NetworkQuality.OFFLINE -> 0
		}
	}

	private fun assessNetworkQuality() {
		val network = connectivityManager.activeNetwork
		if (network == null) {
			_quality.value = NetworkQuality.OFFLINE
			return
		}

		val capabilities = connectivityManager.getNetworkCapabilities(network)
		if (capabilities == null) {
			_quality.value = NetworkQuality.OFFLINE
			return
		}

		// Determine base quality from network type and capabilities
		val baseQuality = assessBaseQuality(capabilities)

		// Adjust based on measured bandwidth if available
		val avgBandwidth = getAverageBandwidth()
		val bandwidthQuality = when {
			avgBandwidth <= 0 -> baseQuality
			avgBandwidth < 256 -> NetworkQuality.POOR
			avgBandwidth < 1024 -> NetworkQuality.MODERATE
			avgBandwidth < 5120 -> NetworkQuality.GOOD
			else -> NetworkQuality.EXCELLENT
		}

		// Adjust based on latency if available
		val latency = _latencyMs.value
		val latencyQuality = when {
			latency <= 0 -> baseQuality
			latency > 1000 -> NetworkQuality.POOR
			latency > 500 -> NetworkQuality.MODERATE
			latency > 200 -> NetworkQuality.GOOD
			else -> NetworkQuality.EXCELLENT
		}

		// Use the lowest quality assessment
		val finalQuality = minOf(baseQuality, bandwidthQuality, latencyQuality, compareBy { it.level })
		_quality.value = finalQuality

		logDebug { "Network quality assessed: $finalQuality (base=$baseQuality, bw=$bandwidthQuality, lat=$latencyQuality)" }
	}

	private fun assessBaseQuality(capabilities: NetworkCapabilities): NetworkQuality {
		// Check for validated internet
		if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
			return NetworkQuality.OFFLINE
		}

		// Check transport type
		return when {
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
				assessWifiQuality(capabilities)
			}
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
				NetworkQuality.EXCELLENT
			}
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
				assessCellularQuality(capabilities)
			}
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
				// VPN quality depends on underlying network
				NetworkQuality.GOOD
			}
			else -> NetworkQuality.MODERATE
		}
	}

	private fun assessWifiQuality(capabilities: NetworkCapabilities): NetworkQuality {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val downstreamBandwidth = capabilities.linkDownstreamBandwidthKbps
			val upstreamBandwidth = capabilities.linkUpstreamBandwidthKbps

			return when {
				downstreamBandwidth >= 50000 -> NetworkQuality.EXCELLENT
				downstreamBandwidth >= 10000 -> NetworkQuality.GOOD
				downstreamBandwidth >= 1000 -> NetworkQuality.MODERATE
				downstreamBandwidth > 0 -> NetworkQuality.POOR
				else -> NetworkQuality.GOOD // Unknown, assume good
			}
		}
		return NetworkQuality.GOOD
	}

	private fun assessCellularQuality(capabilities: NetworkCapabilities): NetworkQuality {
		// Try to get bandwidth info from capabilities
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val downstreamBandwidth = capabilities.linkDownstreamBandwidthKbps

			return when {
				downstreamBandwidth >= 20000 -> NetworkQuality.EXCELLENT // 5G/LTE
				downstreamBandwidth >= 5000 -> NetworkQuality.GOOD // LTE
				downstreamBandwidth >= 1000 -> NetworkQuality.MODERATE // 3G
				downstreamBandwidth > 0 -> NetworkQuality.POOR // 2G
				else -> assessCellularByTelephony()
			}
		}
		return assessCellularByTelephony()
	}

	@SuppressLint("MissingPermission") // Handled by try-catch
	private fun assessCellularByTelephony(): NetworkQuality {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			return NetworkQuality.MODERATE
		}
		val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
			?: return NetworkQuality.MODERATE

		return try {
			when (telephonyManager.dataNetworkType) {
				TelephonyManager.NETWORK_TYPE_NR -> NetworkQuality.EXCELLENT // 5G
				TelephonyManager.NETWORK_TYPE_LTE -> NetworkQuality.GOOD
				TelephonyManager.NETWORK_TYPE_HSPAP,
				TelephonyManager.NETWORK_TYPE_HSPA,
				TelephonyManager.NETWORK_TYPE_HSDPA,
				TelephonyManager.NETWORK_TYPE_HSUPA -> NetworkQuality.MODERATE
				TelephonyManager.NETWORK_TYPE_UMTS,
				TelephonyManager.NETWORK_TYPE_EVDO_0,
				TelephonyManager.NETWORK_TYPE_EVDO_A,
				TelephonyManager.NETWORK_TYPE_EVDO_B -> NetworkQuality.MODERATE
				TelephonyManager.NETWORK_TYPE_EDGE,
				TelephonyManager.NETWORK_TYPE_GPRS,
				TelephonyManager.NETWORK_TYPE_CDMA,
				TelephonyManager.NETWORK_TYPE_1xRTT -> NetworkQuality.POOR
				else -> NetworkQuality.MODERATE
			}
		} catch (e: SecurityException) {
			NetworkQuality.MODERATE
		}
	}

	private fun addBandwidthSample(kbps: Int) {
		synchronized(bandwidthSamples) {
			bandwidthSamples.add(kbps)
			while (bandwidthSamples.size > maxSamples) {
				bandwidthSamples.removeAt(0)
			}
			_bandwidthKbps.value = getAverageBandwidth()
		}
		assessNetworkQuality()
	}

	private fun getAverageBandwidth(): Int {
		synchronized(bandwidthSamples) {
			if (bandwidthSamples.isEmpty()) return 0
			return bandwidthSamples.average().roundToInt()
		}
	}

	private suspend fun measureLatency() {
		if (!isMonitoring) return

		try {
			val startTime = System.currentTimeMillis()
			val url = URL(LATENCY_TEST_URL)
			val connection = url.openConnection() as HttpURLConnection
			connection.connectTimeout = 5000
			connection.readTimeout = 5000
			connection.requestMethod = "HEAD"

			try {
				connection.connect()
				val latency = System.currentTimeMillis() - startTime
				_latencyMs.value = latency
				logDebug { "Latency measured: ${latency}ms" }
				assessNetworkQuality()
			} finally {
				connection.disconnect()
			}
		} catch (e: IOException) {
			logDebug { "Latency measurement failed: ${e.message}" }
			// Keep the last known latency
		} catch (e: Exception) {
			logDebug { "Latency measurement error: ${e.message}" }
		}
	}

	private fun <T : Comparable<T>> minOf(a: T, b: T, c: T, comparator: Comparator<T>): T {
		var min = a
		if (comparator.compare(b, min) < 0) min = b
		if (comparator.compare(c, min) < 0) min = c
		return min
	}

	private inner class NetworkCallbackImpl : ConnectivityManager.NetworkCallback() {
		override fun onAvailable(network: Network) {
			assessNetworkQuality()
		}

		override fun onLost(network: Network) {
			_quality.value = NetworkQuality.OFFLINE
		}

		override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
			assessNetworkQuality()
		}

		override fun onUnavailable() {
			_quality.value = NetworkQuality.OFFLINE
		}
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	companion object {
		private const val TAG = "NetworkQualityMonitor"
		private const val LATENCY_CHECK_INTERVAL_MS = 60_000L // 1 minute
		private const val LATENCY_TEST_URL = "https://www.google.com/generate_204"
	}
}
