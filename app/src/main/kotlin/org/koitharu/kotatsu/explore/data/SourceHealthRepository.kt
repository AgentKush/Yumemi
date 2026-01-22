package org.koitharu.kotatsu.explore.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.dao.SourceHealthDao
import org.koitharu.kotatsu.core.db.entity.SourceHealthEntity
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for tracking and querying source health metrics.
 * Provides methods to record successes/failures and query source reliability.
 */
@Singleton
class SourceHealthRepository @Inject constructor(
	private val db: MangaDatabase,
) {
	private val dao: SourceHealthDao
		get() = db.getSourceHealthDao()

	/**
	 * Record a successful request for a source
	 * @param source The manga source
	 * @param responseTimeMs Response time in milliseconds
	 */
	suspend fun recordSuccess(source: MangaSource, responseTimeMs: Long) {
		dao.recordSuccess(source.name, responseTimeMs)
	}

	/**
	 * Record a failed request for a source
	 * @param source The manga source
	 * @param error The error that occurred
	 */
	suspend fun recordFailure(source: MangaSource, error: Throwable?) {
		dao.recordFailure(source.name, error?.message)
	}

	/**
	 * Get health metrics for a specific source
	 */
	suspend fun getHealth(source: MangaSource): SourceHealthEntity? {
		return dao.get(source.name)
	}

	/**
	 * Observe health metrics for a specific source
	 */
	fun observeHealth(source: MangaSource): Flow<SourceHealthEntity?> {
		return dao.observe(source.name)
	}

	/**
	 * Get all source health metrics
	 */
	suspend fun getAllHealth(): List<SourceHealthEntity> {
		return dao.getAll()
	}

	/**
	 * Observe all source health metrics
	 */
	fun observeAllHealth(): Flow<List<SourceHealthEntity>> {
		return dao.observeAll()
	}

	/**
	 * Get sources that meet a minimum success rate threshold
	 * @param minSuccessRate Minimum success rate percentage (0-100)
	 */
	suspend fun getHealthySources(minSuccessRate: Float = 80f): List<SourceHealthEntity> {
		return dao.getHealthySources(minSuccessRate)
	}

	/**
	 * Get sources that are currently failing (high consecutive failures)
	 * @param threshold Number of consecutive failures to consider as "failing"
	 */
	suspend fun getFailingSources(threshold: Int = 3): List<SourceHealthEntity> {
		return dao.getFailingSources(threshold)
	}

	/**
	 * Get sources sorted by reliability score (highest first)
	 */
	suspend fun getSourcesByReliability(): List<SourceHealthEntity> {
		return dao.getAll().sortedByDescending { it.reliabilityScore }
	}

	/**
	 * Get sources sorted by response time (fastest first)
	 */
	suspend fun getSourcesBySpeed(): List<SourceHealthEntity> {
		return dao.getAll()
			.filter { it.avgResponseTime > 0 }
			.sortedBy { it.avgResponseTime }
	}

	/**
	 * Observe source health with calculated summary stats
	 */
	fun observeHealthSummary(): Flow<HealthSummary> {
		return dao.observeAll().map { entities ->
			val total = entities.size
			val healthy = entities.count { it.healthStatus == SourceHealthEntity.HealthStatus.HEALTHY }
			val degraded = entities.count { it.healthStatus == SourceHealthEntity.HealthStatus.DEGRADED }
			val poor = entities.count { it.healthStatus == SourceHealthEntity.HealthStatus.POOR }
			val critical = entities.count { it.healthStatus == SourceHealthEntity.HealthStatus.CRITICAL }
			val unknown = entities.count { it.healthStatus == SourceHealthEntity.HealthStatus.UNKNOWN }
			val avgReliability = if (entities.isNotEmpty()) {
				entities.filter { it.successCount + it.failureCount > 0 }
					.map { it.reliabilityScore }
					.average()
					.toFloat()
			} else 0f
			
			HealthSummary(
				totalSources = total,
				healthyCount = healthy,
				degradedCount = degraded,
				poorCount = poor,
				criticalCount = critical,
				unknownCount = unknown,
				averageReliability = avgReliability,
			)
		}
	}

	/**
	 * Reset statistics for a specific source
	 */
	suspend fun resetStats(source: MangaSource) {
		dao.resetStats(source.name)
	}

	/**
	 * Clear all health statistics
	 */
	suspend fun clearAllStats() {
		dao.deleteAll()
	}

	/**
	 * Check if a source should be considered unreliable
	 * @param source The manga source to check
	 * @param failureThreshold Number of consecutive failures to consider unreliable
	 */
	suspend fun isSourceUnreliable(source: MangaSource, failureThreshold: Int = 5): Boolean {
		val health = dao.get(source.name) ?: return false
		return health.consecutiveFailures >= failureThreshold
	}

	/**
	 * Summary of health across all tracked sources
	 */
	data class HealthSummary(
		val totalSources: Int,
		val healthyCount: Int,
		val degradedCount: Int,
		val poorCount: Int,
		val criticalCount: Int,
		val unknownCount: Int,
		val averageReliability: Float,
	)
}
