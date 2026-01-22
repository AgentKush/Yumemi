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

	@Query("SELECT * FROM source_health ORDER BY (success_count + failure_count) DESC")
	abstract suspend fun getAll(): List<SourceHealthEntity>

	@Query("SELECT * FROM source_health ORDER BY (success_count + failure_count) DESC")
	abstract fun observeAll(): Flow<List<SourceHealthEntity>>

	@Query("SELECT * FROM source_health WHERE (CAST(success_count AS REAL) / (success_count + failure_count)) * 100 >= :minSuccessRate ORDER BY avg_response_time ASC")
	abstract suspend fun getHealthySources(minSuccessRate: Float = 80f): List<SourceHealthEntity>

	@Query("SELECT * FROM source_health WHERE consecutive_failures >= :threshold")
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
			val totalRequests = existing.successCount + existing.failureCount + 1
			// Exponential moving average for response time (weight recent more heavily)
			val newAvg = if (existing.avgResponseTime > 0) {
				((existing.avgResponseTime * 0.7) + (responseTimeMs * 0.3)).toLong()
			} else {
				responseTimeMs
			}
			
			upsert(
				existing.copy(
					successCount = existing.successCount + 1,
					avgResponseTime = newAvg,
					minResponseTime = minOf(existing.minResponseTime, responseTimeMs),
					maxResponseTime = maxOf(existing.maxResponseTime, responseTimeMs),
					lastSuccessAt = now,
					consecutiveFailures = 0, // Reset on success
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
