package org.koitharu.kotatsu.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.koitharu.kotatsu.core.db.entity.SourceHealthEntity

@Dao
abstract class SourceHealthDao {

	@Query("SELECT * FROM source_health WHERE source = :source")
	abstract suspend fun get(source: String): SourceHealthEntity?

	@Query("SELECT * FROM source_health WHERE source = :source")
	abstract fun observe(source: String): Flow<SourceHealthEntity?>

	@Query("SELECT * FROM source_health ORDER BY (CAST(success_count AS REAL) / NULLIF(success_count + failure_count, 0)) DESC")
	abstract suspend fun getAll(): List<SourceHealthEntity>

	@Query("SELECT * FROM source_health ORDER BY (CAST(success_count AS REAL) / NULLIF(success_count + failure_count, 0)) DESC")
	abstract fun observeAll(): Flow<List<SourceHealthEntity>>

	@Query("SELECT * FROM source_health WHERE source IN (:sources)")
	abstract suspend fun getForSources(sources: List<String>): List<SourceHealthEntity>

	@Query("SELECT * FROM source_health ORDER BY avg_response_time ASC LIMIT :limit")
	abstract suspend fun getFastestSources(limit: Int): List<SourceHealthEntity>

	@Query("SELECT * FROM source_health WHERE consecutive_failures >= :threshold")
	abstract suspend fun getUnhealthySources(threshold: Int = 3): List<SourceHealthEntity>

	@Query("""
		SELECT * FROM source_health 
		WHERE (success_count + failure_count) > 0 
		AND (CAST(success_count AS REAL) * 100.0 / (success_count + failure_count)) >= :minSuccessRate
		ORDER BY (CAST(success_count AS REAL) / (success_count + failure_count)) DESC
	""")
	abstract suspend fun getHealthySources(minSuccessRate: Float = 80f): List<SourceHealthEntity>

	@Query("SELECT * FROM source_health WHERE consecutive_failures >= :threshold ORDER BY consecutive_failures DESC")
	abstract suspend fun getFailingSources(threshold: Int = 3): List<SourceHealthEntity>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	abstract suspend fun upsert(entity: SourceHealthEntity)

	@Query("DELETE FROM source_health WHERE source = :source")
	abstract suspend fun delete(source: String)

	@Query("DELETE FROM source_health")
	abstract suspend fun deleteAll()

	/**
	 * Record a successful request for a source
	 */
	@Transaction
	open suspend fun recordSuccess(source: String, responseTimeMs: Long) {
		val existing = get(source)
		val now = System.currentTimeMillis()

		if (existing == null) {
			upsert(
				SourceHealthEntity(
					source = source,
					successCount = 1,
					failureCount = 0,
					avgResponseTime = responseTimeMs,
					minResponseTime = responseTimeMs,
					maxResponseTime = responseTimeMs,
					lastSuccessAt = now,
					consecutiveFailures = 0,
				)
			)
		} else {
			// Calculate rolling average (weighted toward recent)
			val totalRequests = existing.successCount + existing.failureCount + 1
			val newAvg = if (totalRequests <= 10) {
				// Simple average for first 10 requests
				((existing.avgResponseTime * (totalRequests - 1)) + responseTimeMs) / totalRequests
			} else {
				// Exponential moving average (weight recent more heavily)
				((existing.avgResponseTime * 9) + responseTimeMs) / 10
			}

			upsert(
				existing.copy(
					successCount = existing.successCount + 1,
					avgResponseTime = newAvg,
					minResponseTime = minOf(existing.minResponseTime, responseTimeMs),
					maxResponseTime = maxOf(existing.maxResponseTime, responseTimeMs),
					lastSuccessAt = now,
					consecutiveFailures = 0, // Reset on success
					lastError = null, // Clear error on success
				)
			)
		}
	}

	/**
	 * Record a failed request for a source
	 */
	@Transaction
	open suspend fun recordFailure(source: String, errorMessage: String?) {
		val existing = get(source)
		val now = System.currentTimeMillis()
		val truncatedError = errorMessage?.take(200)

		if (existing == null) {
			upsert(
				SourceHealthEntity(
					source = source,
					successCount = 0,
					failureCount = 1,
					lastFailureAt = now,
					lastError = truncatedError,
					consecutiveFailures = 1,
				)
			)
		} else {
			upsert(
				existing.copy(
					failureCount = existing.failureCount + 1,
					lastFailureAt = now,
					lastError = truncatedError,
					consecutiveFailures = existing.consecutiveFailures + 1,
				)
			)
		}
	}

	/**
	 * Reset statistics for a specific source
	 */
	@Transaction
	open suspend fun resetStats(source: String) {
		val existing = get(source) ?: return
		upsert(
			SourceHealthEntity(
				source = source,
				statsResetAt = System.currentTimeMillis(),
			)
		)
	}
}
