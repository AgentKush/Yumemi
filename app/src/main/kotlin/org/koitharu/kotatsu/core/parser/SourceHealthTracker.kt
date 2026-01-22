package org.koitharu.kotatsu.core.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.explore.data.SourceHealthRepository
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Tracks health metrics for manga sources.
 * Wraps network operations to automatically record success/failure and response times.
 */
@Singleton
class SourceHealthTracker @Inject constructor(
	private val healthRepository: SourceHealthRepository,
) {
	/**
	 * Execute an operation and track its health metrics.
	 * Records success with response time, or failure with error message.
	 * 
	 * @param source The manga source being accessed
	 * @param operation The suspend operation to execute
	 * @return The result of the operation
	 */
	suspend fun <T> trackOperation(
		source: MangaSource,
		operation: suspend () -> T
	): T {
		var responseTime = 0L
		val result = runCatchingCancellable {
			var operationResult: T
			responseTime = measureTimeMillis {
				operationResult = operation()
			}
			operationResult
		}
		
		// Record metrics in background
		withContext(Dispatchers.Default) {
			result.fold(
				onSuccess = {
					// Validate result - empty collections may indicate issues
					val isValidResult = when (it) {
						is Collection<*> -> it.isNotEmpty()
						is String -> it.isNotEmpty()
						else -> true
					}
					if (isValidResult) {
						healthRepository.recordSuccess(source, responseTime)
					} else {
						// Empty result might indicate a source issue
						healthRepository.recordFailure(source, EmptyResultException())
					}
				},
				onFailure = { error ->
					healthRepository.recordFailure(source, error)
				}
			)
		}
		
		return result.getOrThrow()
	}

	/**
	 * Execute an operation that returns a nullable result and track its health metrics.
	 * Null results are treated as successful if expected.
	 */
	suspend fun <T> trackNullableOperation(
		source: MangaSource,
		operation: suspend () -> T?
	): T? {
		var responseTime = 0L
		val result = runCatchingCancellable {
			var operationResult: T?
			responseTime = measureTimeMillis {
				operationResult = operation()
			}
			operationResult
		}
		
		withContext(Dispatchers.Default) {
			result.fold(
				onSuccess = {
					healthRepository.recordSuccess(source, responseTime)
				},
				onFailure = { error ->
					healthRepository.recordFailure(source, error)
				}
			)
		}
		
		return result.getOrThrow()
	}

	/**
	 * Record a manual success for operations tracked externally
	 */
	suspend fun recordSuccess(source: MangaSource, responseTimeMs: Long) {
		healthRepository.recordSuccess(source, responseTimeMs)
	}

	/**
	 * Record a manual failure for operations tracked externally
	 */
	suspend fun recordFailure(source: MangaSource, error: Throwable?) {
		healthRepository.recordFailure(source, error)
	}

	/**
	 * Check if a source is currently unreliable (many consecutive failures)
	 */
	suspend fun isSourceUnreliable(source: MangaSource): Boolean {
		return healthRepository.isSourceUnreliable(source)
	}

	/**
	 * Exception for empty results that shouldn't necessarily fail but indicate potential issues
	 */
	class EmptyResultException : Exception("Operation returned empty result")
}
