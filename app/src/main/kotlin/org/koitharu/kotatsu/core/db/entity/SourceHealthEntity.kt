package org.koitharu.kotatsu.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.koitharu.kotatsu.core.db.TABLE_SOURCE_HEALTH

/**
 * Entity for tracking source health metrics.
 * Records success/failure counts, response times, and reliability scores.
 */
@Entity(tableName = TABLE_SOURCE_HEALTH)
data class SourceHealthEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "source")
	val source: String,

	/** Total number of successful requests */
	@ColumnInfo(name = "success_count")
	val successCount: Long = 0L,

	/** Total number of failed requests */
	@ColumnInfo(name = "failure_count")
	val failureCount: Long = 0L,

	/** Average response time in milliseconds (rolling average) */
	@ColumnInfo(name = "avg_response_time")
	val avgResponseTime: Long = 0L,

	/** Minimum response time observed */
	@ColumnInfo(name = "min_response_time")
	val minResponseTime: Long = Long.MAX_VALUE,

	/** Maximum response time observed */
	@ColumnInfo(name = "max_response_time")
	val maxResponseTime: Long = 0L,

	/** Timestamp of last successful request */
	@ColumnInfo(name = "last_success_at")
	val lastSuccessAt: Long = 0L,

	/** Timestamp of last failed request */
	@ColumnInfo(name = "last_failure_at")
	val lastFailureAt: Long = 0L,

	/** Most recent error message (truncated) */
	@ColumnInfo(name = "last_error")
	val lastError: String? = null,

	/** Number of consecutive failures (reset on success) */
	@ColumnInfo(name = "consecutive_failures")
	val consecutiveFailures: Int = 0,

	/** Timestamp when stats were last reset */
	@ColumnInfo(name = "stats_reset_at")
	val statsResetAt: Long = System.currentTimeMillis(),
) {
	/**
	 * Calculate success rate as a percentage (0-100)
	 */
	val successRate: Float
		get() {
			val total = successCount + failureCount
			return if (total > 0) (successCount.toFloat() / total) * 100f else 0f
		}

	/**
	 * Calculate reliability score (0-100) based on success rate and response time
	 */
	val reliabilityScore: Float
		get() {
			val total = successCount + failureCount
			if (total == 0L) return 0f

			// Base score from success rate (0-70 points)
			val successScore = successRate * 0.7f

			// Response time score (0-30 points)
			// Target: < 1000ms = 30 points, > 5000ms = 0 points
			val responseScore = when {
				avgResponseTime <= 0 -> 15f // No data, neutral
				avgResponseTime < 1000 -> 30f
				avgResponseTime < 2000 -> 25f
				avgResponseTime < 3000 -> 20f
				avgResponseTime < 4000 -> 15f
				avgResponseTime < 5000 -> 10f
				else -> 5f
			}

			return successScore + responseScore
		}

	/**
	 * Determine health status based on reliability score
	 */
	val healthStatus: HealthStatus
		get() = when {
			successCount + failureCount == 0L -> HealthStatus.UNKNOWN
			consecutiveFailures >= 5 -> HealthStatus.CRITICAL
			reliabilityScore >= 80f -> HealthStatus.HEALTHY
			reliabilityScore >= 60f -> HealthStatus.DEGRADED
			reliabilityScore >= 40f -> HealthStatus.POOR
			else -> HealthStatus.CRITICAL
		}

	enum class HealthStatus {
		UNKNOWN,
		HEALTHY,
		DEGRADED,
		POOR,
		CRITICAL
	}
}
