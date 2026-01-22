package org.koitharu.kotatsu.core.parser

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.SourceHealthEntity
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages source health metrics tracking.
 * Provides methods to record successes/failures and query health status.
 */
@Singleton
class SourceHealthManager @Inject constructor(
	private val database: MangaDatabase,
) {
	private val dao get() = database.getSourceHealthDao()

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
	 * @param error The error that occurred (optional)
	 */
	suspend fun recordFailure(source: MangaSource, error: Throwable?) {
		val errorMessage = error?.let { "${it.javaClass.simpleName}: ${it.message}" }
		dao.recordFailure(source.name, errorMessage)
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
	 * Get health metrics for all tracked sources
	 */
	suspend fun getAllHealth(): List<SourceHealthEntity> {
		return dao.getAll()
	}

	/**
	 * Observe health metrics for all tracked sources
	 */
	fun observeAllHealth(): Flow<List<SourceHealthEntity>> {
		return dao.observeAll()
	}

	/**
	 * Get health metrics for specific sources
	 */
	suspend fun getHealthForSources(sources: List<MangaSource>): Map<String, SourceHealthEntity> {
		return dao.getForSources(sources.map { it.name }).associateBy { it.source }
	}

	/**
	 * Get the fastest responding sources
	 */
	suspend fun getFastestSources(limit: Int = 10): List<SourceHealthEntity> {
		return dao.getFastestSources(limit)
	}

	/**
	 * Get sources that are currently unhealthy (consecutive failures >= threshold)
	 */
	suspend fun getUnhealthySources(threshold: Int = 3): List<SourceHealthEntity> {
		return dao.getUnhealthySources(threshold)
	}

	/**
	 * Check if a source is currently healthy
	 */
	suspend fun isSourceHealthy(source: MangaSource): Boolean {
		val health = getHealth(source) ?: return true // No data means we assume healthy
		return health.healthStatus == SourceHealthEntity.HealthStatus.HEALTHY ||
			health.healthStatus == SourceHealthEntity.HealthStatus.UNKNOWN
	}

	/**
	 * Get reliability score for a source (0-100)
	 */
	suspend fun getReliabilityScore(source: MangaSource): Float {
		return getHealth(source)?.reliabilityScore ?: 0f
	}

	/**
	 * Reset statistics for a specific source
	 */
	suspend fun resetStats(source: MangaSource) {
		dao.resetStats(source.name)
	}

	/**
	 * Clear all health data
	 */
	suspend fun clearAllData() {
		dao.deleteAll()
	}

	/**
	 * Observe sources sorted by reliability score
	 */
	fun observeSourcesByReliability(): Flow<List<SourceHealthEntity>> {
		return dao.observeAll().map { list ->
			list.sortedByDescending { it.reliabilityScore }
		}
	}

	/**
	 * Get a summary of source health across all tracked sources
	 */
	suspend fun getHealthSummary(): HealthSummary {
		val all = getAllHealth()
		return HealthSummary(
			totalTracked = all.size,
			healthy = all.count { it.healthStatus == SourceHealthEntity.HealthStatus.HEALTHY },
			degraded = all.count { it.healthStatus == SourceHealthEntity.HealthStatus.DEGRADED },
			poor = all.count { it.healthStatus == SourceHealthEntity.HealthStatus.POOR },
			critical = all.count { it.healthStatus == SourceHealthEntity.HealthStatus.CRITICAL },
			unknown = all.count { it.healthStatus == SourceHealthEntity.HealthStatus.UNKNOWN },
			avgReliabilityScore = if (all.isNotEmpty()) all.map { it.reliabilityScore }.average().toFloat() else 0f,
		)
	}

	data class HealthSummary(
		val totalTracked: Int,
		val healthy: Int,
		val degraded: Int,
		val poor: Int,
		val critical: Int,
		val unknown: Int,
		val avgReliabilityScore: Float,
	)
}
