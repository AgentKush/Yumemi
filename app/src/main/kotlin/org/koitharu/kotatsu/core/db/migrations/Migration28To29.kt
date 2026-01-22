package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 28 to 29: Source Health Monitor
 *
 * Creates the source_health table to track reliability and performance metrics
 * for manga sources including success/failure rates, response times, and error history.
 */
class Migration28To29 : Migration(28, 29) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS source_health (
				source TEXT NOT NULL PRIMARY KEY,
				success_count INTEGER NOT NULL DEFAULT 0,
				failure_count INTEGER NOT NULL DEFAULT 0,
				avg_response_time INTEGER NOT NULL DEFAULT 0,
				min_response_time INTEGER NOT NULL DEFAULT 9223372036854775807,
				max_response_time INTEGER NOT NULL DEFAULT 0,
				last_success_at INTEGER NOT NULL DEFAULT 0,
				last_failure_at INTEGER NOT NULL DEFAULT 0,
				last_error TEXT,
				consecutive_failures INTEGER NOT NULL DEFAULT 0,
				stats_reset_at INTEGER NOT NULL DEFAULT 0
			)
			""".trimIndent()
		)
		
		// Add index for efficient queries on health status
		db.execSQL("CREATE INDEX IF NOT EXISTS index_source_health_consecutive_failures ON source_health(consecutive_failures)")
	}
}
