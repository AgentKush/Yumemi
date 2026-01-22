package org.koitharu.kotatsu.core.network

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * Interceptor that handles HTTP 429 (Too Many Requests) responses with exponential backoff.
 *
 * Features:
 * - Configurable max retry attempts
 * - Exponential backoff with jitter to prevent thundering herd
 * - Respects Retry-After header when present
 * - Falls back to calculated delay when header is missing
 *
 * @param maxRetries Maximum number of retry attempts (default: 3)
 * @param initialDelayMs Initial delay in milliseconds (default: 1000ms)
 * @param maxDelayMs Maximum delay cap in milliseconds (default: 30000ms)
 * @param backoffMultiplier Multiplier for exponential growth (default: 2.0)
 */
class RateLimitInterceptor(
	private val maxRetries: Int = DEFAULT_MAX_RETRIES,
	private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
	private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
	private val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		var response = chain.proceed(chain.request())
		var retryCount = 0

		while (response.code == 429 && retryCount < maxRetries) {
			val request = response.request
			val retryAfterMs = response.header(CommonHeaders.RETRY_AFTER)?.parseRetryAfter()
			response.closeQuietly()

			// Calculate delay: use Retry-After header if available, otherwise exponential backoff
			val calculatedDelay = calculateBackoffDelay(retryCount)
			val delayMs = retryAfterMs?.coerceAtMost(maxDelayMs) ?: calculatedDelay

			logDebug { "Rate limited (429) on ${request.url.host}, retry ${retryCount + 1}/$maxRetries after ${delayMs}ms" }

			// Wait before retrying
			runBlocking {
				delay(delayMs)
			}

			retryCount++
			response = chain.proceed(request)
		}

		// If still rate limited after all retries, throw exception
		if (response.code == 429) {
			val request = response.request
			val retryAfter = response.header(CommonHeaders.RETRY_AFTER)?.parseRetryAfter() ?: 0L
			response.closeQuietly()
			logDebug { "Rate limit exceeded after $maxRetries retries on ${request.url}" }
			throw TooManyRequestExceptions(
				url = request.url.toString(),
				retryAfter = retryAfter,
			)
		}

		return response
	}

	/**
	 * Calculate exponential backoff delay with jitter.
	 * Formula: min(maxDelay, initialDelay * (multiplier ^ retryCount)) + random jitter
	 */
	private fun calculateBackoffDelay(retryCount: Int): Long {
		val exponentialDelay = initialDelayMs * Math.pow(backoffMultiplier, retryCount.toDouble()).toLong()
		val cappedDelay = min(exponentialDelay, maxDelayMs)
		// Add jitter (±25%) to prevent thundering herd
		val jitter = (cappedDelay * 0.25 * (Random.nextDouble() - 0.5)).toLong()
		return (cappedDelay + jitter).coerceAtLeast(initialDelayMs)
	}

	private fun String.parseRetryAfter(): Long? {
		// Try parsing as seconds first
		toLongOrNull()?.let { seconds ->
			return TimeUnit.SECONDS.toMillis(seconds)
		}
		// Try parsing as HTTP date
		return runCatching {
			val dateTime = ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME)
			val delayMs = dateTime.toInstant().toEpochMilli() - System.currentTimeMillis()
			delayMs.coerceAtLeast(0L)
		}.getOrNull()
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	companion object {
		private const val TAG = "RateLimitInterceptor"
		private const val DEFAULT_MAX_RETRIES = 3
		private const val DEFAULT_INITIAL_DELAY_MS = 1000L
		private const val DEFAULT_MAX_DELAY_MS = 30_000L
		private const val DEFAULT_BACKOFF_MULTIPLIER = 2.0
	}
}
