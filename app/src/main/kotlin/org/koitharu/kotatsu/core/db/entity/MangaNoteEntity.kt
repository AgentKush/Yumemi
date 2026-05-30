package org.koitharu.kotatsu.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user's private note and personal rating for a manga.
 */
@Entity(tableName = "manga_notes")
data class MangaNoteEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "manga_id")
	val mangaId: Long,

	@ColumnInfo(name = "note")
	val note: String = "",

	/** Personal rating, 0..5 (0 = unrated) */
	@ColumnInfo(name = "rating")
	val rating: Float = 0f,

	@ColumnInfo(name = "updated_at")
	val updatedAt: Long = 0L,
)
