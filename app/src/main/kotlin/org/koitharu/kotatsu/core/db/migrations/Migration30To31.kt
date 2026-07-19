package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 30 to 31: drop indexes that are NOT declared on their Room entities.
 *
 * Migration27To28 / Migration28To29 created several indexes via raw SQL without declaring them on
 * the corresponding @Entity classes. Room validates the schema strictly after migrating, so any
 * database that contains those extra indexes fails with "Migration didn't properly handle: ...".
 * Dropping them makes every table match its entity definition again. MangaEntity's own indexes
 * (index_manga_source / index_manga_title / index_manga_public_url) ARE declared on the entity and
 * are intentionally kept.
 */
class Migration30To31 : Migration(30, 31) {

	override fun migrate(db: SupportSQLiteDatabase) {
		for (name in UNDECLARED_INDEX_NAMES) {
			db.execSQL("DROP INDEX IF EXISTS $name")
		}
	}
}

private val UNDECLARED_INDEX_NAMES = listOf(
	"index_history_deleted_at",
	"index_history_updated_at",
	"index_history_created_at",
	"index_history_percent",
	"index_history_deleted_updated",
	"index_tracks_chapters_new",
	"index_tracks_last_chapter_date",
	"index_tracks_last_check_time",
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
