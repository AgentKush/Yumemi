package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 30 to 31: reconcile indexes with the entity definitions.
 *
 * Migration27To28 / Migration28To29 created indexes via raw SQL. Room 2.7 validates the schema
 * strictly after migrating and rejects any table whose indexes differ from its @Entity:
 *  - MangaEntity, HistoryEntity and TrackEntity DO declare indexes -> those are (re)created.
 *  - every other table declares no indexes -> the extra ones are dropped.
 *  - HistoryEntity declares (deleted_at, updated_at) ASC, but the old migration created it DESC
 *    (index_history_deleted_updated) -> the DESC one is dropped and the ASC one created.
 *
 * All statements are IF NOT EXISTS / IF EXISTS, so this is idempotent and correct for a fresh
 * install, an old upgraded database, and a database left inconsistent by 9.4.12 / 9.4.13.
 */
class Migration30To31 : Migration(30, 31) {

	override fun migrate(db: SupportSQLiteDatabase) {
		// Ensure the indexes that MangaEntity / HistoryEntity / TrackEntity declare exist
		for (sql in ENTITY_INDEX_STATEMENTS) {
			db.execSQL(sql)
		}
		// Remove indexes that no entity declares (incl. the old DESC history composite)
		for (name in UNDECLARED_INDEX_NAMES) {
			db.execSQL("DROP INDEX IF EXISTS $name")
		}
	}
}

private val ENTITY_INDEX_STATEMENTS = listOf(
	"CREATE INDEX IF NOT EXISTS index_manga_source ON manga(source)",
	"CREATE INDEX IF NOT EXISTS index_manga_title ON manga(title)",
	"CREATE INDEX IF NOT EXISTS index_manga_public_url ON manga(public_url)",
	"CREATE INDEX IF NOT EXISTS index_history_deleted_at ON history(deleted_at)",
	"CREATE INDEX IF NOT EXISTS index_history_updated_at ON history(updated_at)",
	"CREATE INDEX IF NOT EXISTS index_history_created_at ON history(created_at)",
	"CREATE INDEX IF NOT EXISTS index_history_percent ON history(percent)",
	"CREATE INDEX IF NOT EXISTS index_history_deleted_at_updated_at ON history(deleted_at, updated_at)",
	"CREATE INDEX IF NOT EXISTS index_tracks_chapters_new ON tracks(chapters_new)",
	"CREATE INDEX IF NOT EXISTS index_tracks_last_chapter_date ON tracks(last_chapter_date)",
	"CREATE INDEX IF NOT EXISTS index_tracks_last_check_time ON tracks(last_check_time)",
)

private val UNDECLARED_INDEX_NAMES = listOf(
	"index_history_deleted_updated",
	"index_favourites_deleted_created",
	"index_bookmarks_manga_id",
	"index_scrobblings_manga_id",
	"index_local_index_manga_id",
	"index_suggestions_manga_id",
	"index_tags_title",
	"index_track_logs_manga_id",
	"index_track_logs_created_at",
	"index_stats_manga_id",
	"index_stats_started_at",
	"index_source_health_consecutive_failures",
)
