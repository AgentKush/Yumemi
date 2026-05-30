package org.koitharu.kotatsu.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.koitharu.kotatsu.core.db.entity.MangaNoteEntity

@Dao
interface MangaNotesDao {

	@Query("SELECT * FROM manga_notes WHERE manga_id = :mangaId")
	suspend fun find(mangaId: Long): MangaNoteEntity?

	@Query("SELECT * FROM manga_notes WHERE manga_id = :mangaId")
	fun observe(mangaId: Long): Flow<MangaNoteEntity?>

	@Upsert
	suspend fun upsert(entity: MangaNoteEntity)

	@Query("DELETE FROM manga_notes WHERE manga_id = :mangaId")
	suspend fun delete(mangaId: Long)
}
