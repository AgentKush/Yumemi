package org.koitharu.kotatsu.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that tracks download speed and reports it to NetworkQualityMonitor.
 * This allows the app to adapt to current network conditions.
 */
@Singleton
class BandwidthTrackingInterceptor @Inject constructor(
	private val networkQualityMonitor: NetworkQualityMonitor,
) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val startTime = System.currentTimeMillis()

		val response = chain.proceed(request)

		// Only track successful responses with body
		val body = response.body
		if (!response.isSuccessful || body == null) {
			return response
		}

		// Wrap the response body to track download progress
		val contentLength = body.contentLength()
		val trackingSource = TrackingSource(
			source = body.source(),
			contentLength = contentLength,
			startTime = startTime,
			onComplete = { bytes, durationMs ->
				if (bytes > MIN_BYTES_TO_TRACK && durationMs > MIN_DURATION_MS) {
					networkQualityMonitor.reportDownload(bytes, durationMs)
				}
			},
		)

		val trackingBody = trackingSource.buffer().asResponseBody(body.contentType(), contentLength)
		return response.newBuilder()
			.body(trackingBody)
			.build()
	}

	/**
	 * Source that tracks bytes read and reports bandwidth when complete.
	 */
	private class TrackingSource(
		source: Source,
		private val contentLength: Long,
		private val startTime: Long,
		private val onComplete: (bytes: Long, durationMs: Long) -> Unit,
	) : ForwardingSource(source) {

		private var totalBytesRead = 0L
		private var reported = false

		override fun read(sink: Buffer, byteCount: Long): Long {
			val bytesRead = super.read(sink, byteCount)

			if (bytesRead != -1L) {
				totalBytesRead += bytesRead
			}

			// Report when complete
			if (!reported && (bytesRead == -1L || (contentLength > 0 && totalBytesRead >= contentLength))) {
				reported = true
				val duration = System.currentTimeMillis() - startTime
				onComplete(totalBytesRead, duration)
			}

			return bytesRead
		}

		override fun close() {
			// Report on close if not already reported
			if (!reported && totalBytesRead > 0) {
				reported = true
				val duration = System.currentTimeMillis() - startTime
				onComplete(totalBytesRead, duration)
			}
			super.close()
		}
	}

	companion object {
		// Minimum thresholds for tracking to avoid noise from small requests
		private const val MIN_BYTES_TO_TRACK = 10_000L // 10KB
		private const val MIN_DURATION_MS = 50L // 50ms
	}
}
