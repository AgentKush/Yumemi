package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 30 to 31: query-optimization indexes.
 *
 * Adds the indexes newly declared on TagEntity, FavouriteEntity, ScrobblingEntity,
 * LocalMangaIndexEntity, TrackLogEntity, StatsEntity and SourceHealthEntity. The index names,
 * columns and order match Room's generated v31 schema (validated by MangaDatabaseTest.migrate30To31
 * on an emulator in CI). Also drops the two legacy DESC composite indexes from an earlier
 * optimization attempt, if a database happens to contain them.
 */
class Migration30To31 : Migration(30, 31) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("CREATE INDEX IF NOT EXISTS index_tags_title ON tags(title)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_favourites_deleted_at_created_at ON favourites(deleted_at, created_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_scrobblings_manga_id ON scrobblings(manga_id)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_local_index_manga_id ON local_index(manga_id)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_track_logs_created_at ON track_logs(created_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_stats_manga_id ON stats(manga_id)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_stats_started_at ON stats(started_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_source_health_consecutive_failures ON source_health(consecutive_failures)")
		db.execSQL("DROP INDEX IF EXISTS index_history_deleted_updated")
		db.execSQL("DROP INDEX IF EXISTS index_favourites_deleted_created")
	}
}
