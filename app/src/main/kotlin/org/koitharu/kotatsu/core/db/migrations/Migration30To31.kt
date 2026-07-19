package org.koitharu.kotatsu.core.db.migrations

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 30 to 31: backfill query-optimization indexes.
 *
 * The indexes added in Migration27To28 / Migration28To29 were never declared on the Room
 * entities, so only databases that upgraded THROUGH those versions received them. Databases
 * created fresh at v28-30 never got them (createAllTables only builds entity-declared indexes).
 * This migration re-runs the exact same statements so every existing database ends up with them;
 * new installs get them via [OptimizationIndexCallback]. Every statement is idempotent
 * (IF NOT EXISTS) and a verbatim copy of the production-proven originals, and no entity/schema
 * shape changes, so Room's schema validation is unaffected.
 */
class Migration30To31 : Migration(30, 31) {

	override fun migrate(db: SupportSQLiteDatabase) {
		applyOptimizationIndexes(db)
	}
}

/** Applies the optimization indexes to freshly created (v31) databases. */
val OptimizationIndexCallback = object : RoomDatabase.Callback() {
	override fun onCreate(db: SupportSQLiteDatabase) {
		applyOptimizationIndexes(db)
	}
}

private fun applyOptimizationIndexes(db: SupportSQLiteDatabase) {
	for (statement in OPTIMIZATION_INDEX_STATEMENTS) {
		db.execSQL(statement)
	}
}

private val OPTIMIZATION_INDEX_STATEMENTS = listOf(
	"CREATE INDEX IF NOT EXISTS index_manga_source ON manga(source)",
	"CREATE INDEX IF NOT EXISTS index_manga_title ON manga(title)",
	"CREATE INDEX IF NOT EXISTS index_manga_public_url ON manga(public_url)",
	"CREATE INDEX IF NOT EXISTS index_history_deleted_at ON history(deleted_at)",
	"CREATE INDEX IF NOT EXISTS index_history_updated_at ON history(updated_at)",
	"CREATE INDEX IF NOT EXISTS index_history_created_at ON history(created_at)",
	"CREATE INDEX IF NOT EXISTS index_history_percent ON history(percent)",
	"CREATE INDEX IF NOT EXISTS index_history_deleted_updated ON history(deleted_at, updated_at DESC)",
	"CREATE INDEX IF NOT EXISTS index_tracks_chapters_new ON tracks(chapters_new)",
	"CREATE INDEX IF NOT EXISTS index_tracks_last_chapter_date ON tracks(last_chapter_date)",
	"CREATE INDEX IF NOT EXISTS index_tracks_last_check_time ON tracks(last_check_time)",
	"CREATE INDEX IF NOT EXISTS index_favourites_deleted_created ON favourites(deleted_at, created_at DESC)",
	"CREATE INDEX IF NOT EXISTS index_bookmarks_manga_id ON bookmarks(manga_id)",
	"CREATE INDEX IF NOT EXISTS index_scrobblings_manga_id ON scrobblings(manga_id)",
	"CREATE INDEX IF NOT EXISTS index_local_index_manga_id ON local_index(manga_id)",
	"CREATE INDEX IF NOT EXISTS index_suggestions_manga_id ON suggestions(manga_id)",
	"CREATE INDEX IF NOT EXISTS index_tags_title ON tags(title)",
	"CREATE INDEX IF NOT EXISTS index_track_logs_manga_id ON track_logs(manga_id)",
	"CREATE INDEX IF NOT EXISTS index_track_logs_created_at ON track_logs(created_at)",
	"CREATE INDEX IF NOT EXISTS index_stats_manga_id ON stats(manga_id)",
	"CREATE INDEX IF NOT EXISTS index_stats_started_at ON stats(started_at)",
	"CREATE INDEX IF NOT EXISTS index_source_health_consecutive_failures ON source_health(consecutive_failures)",
)
