package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 27 to 28: Database Query Optimization
 *
 * Adds indexes to improve query performance for:
 * - Manga searches by source, title, author
 * - History queries with deleted_at filtering and updated_at ordering
 * - Track queries for new chapters and last chapter date
 * - Bookmark lookups by manga_id
 * - Scrobbling lookups by manga_id
 */
class Migration27To28 : Migration(27, 28) {

	override fun migrate(db: SupportSQLiteDatabase) {
		// Manga table indexes
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_source ON manga(source)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_title ON manga(title)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_public_url ON manga(public_url)")
		
		// History table indexes for common query patterns
		db.execSQL("CREATE INDEX IF NOT EXISTS index_history_deleted_at ON history(deleted_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_history_updated_at ON history(updated_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_history_created_at ON history(created_at)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_history_percent ON history(percent)")
		// Composite index for the most common history query pattern
		db.execSQL("CREATE INDEX IF NOT EXISTS index_history_deleted_updated ON history(deleted_at, updated_at DESC)")
		
		// Tracks table indexes for sorting and filtering
		db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_chapters_new ON tracks(chapters_new)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_last_chapter_date ON tracks(last_chapter_date)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_last_check_time ON tracks(last_check_time)")
		
		// Favourites additional indexes (composite for common queries)
		db.execSQL("CREATE INDEX IF NOT EXISTS index_favourites_deleted_created ON favourites(deleted_at, created_at DESC)")
		
		// Bookmarks index for manga lookups
		db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_manga_id ON bookmarks(manga_id)")
		
		// Scrobblings index for manga lookups
		db.execSQL("CREATE INDEX IF NOT EXISTS index_scrobblings_manga_id ON scrobblings(manga_id)")
		
		// Local index table optimization
		db.execSQL("CREATE INDEX IF NOT EXISTS index_local_index_manga_id ON local_index(manga_id)")
		
		// Suggestions index
		db.execSQL("CREATE INDEX IF NOT EXISTS index_suggestions_manga_id ON suggestions(manga_id)")
		
		// Tags index for title searches
		db.execSQL("CREATE INDEX IF NOT EXISTS index_tags_title ON tags(title)")
		
		// Track logs index for efficient lookups
		db.execSQL("CREATE INDEX IF NOT EXISTS index_track_logs_manga_id ON track_logs(manga_id)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_track_logs_created_at ON track_logs(created_at)")
		
		// Stats table indexes
		db.execSQL("CREATE INDEX IF NOT EXISTS index_stats_manga_id ON stats(manga_id)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_stats_started_at ON stats(started_at)")
		
		// Run ANALYZE to update query planner statistics
		db.execSQL("ANALYZE")
	}
}
