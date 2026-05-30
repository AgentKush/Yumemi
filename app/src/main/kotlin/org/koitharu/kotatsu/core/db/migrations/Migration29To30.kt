package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 29 to 30: Personal notes and ratings
 *
 * Creates the manga_notes table that stores a private note and personal rating per manga.
 */
class Migration29To30 : Migration(29, 30) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS manga_notes (
				manga_id INTEGER NOT NULL PRIMARY KEY,
				note TEXT NOT NULL DEFAULT '',
				rating REAL NOT NULL DEFAULT 0,
				updated_at INTEGER NOT NULL DEFAULT 0
			)
			""".trimIndent()
		)
	}
}
