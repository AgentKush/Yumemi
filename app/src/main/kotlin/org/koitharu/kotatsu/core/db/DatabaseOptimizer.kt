package org.koitharu.kotatsu.core.db

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database query optimizer that provides runtime optimizations and diagnostics.
 *
 * Features:
 * - Periodic ANALYZE to update query planner statistics
 * - VACUUM for database compaction (when database is idle)
 * - Query plan analysis for debugging slow queries
 * - Index usage statistics
 */
@Singleton
class DatabaseOptimizer @Inject constructor(
	@ApplicationContext private val context: Context,
	private val database: MangaDatabase,
) {

	/**
	 * Run ANALYZE to update SQLite's query planner statistics.
	 * Should be called periodically (e.g., once per app session or daily).
	 */
	suspend fun analyzeDatabase() = withContext(Dispatchers.IO) {
		try {
			database.openHelper.writableDatabase.execSQL("ANALYZE")
			logDebug { "Database ANALYZE completed successfully" }
		} catch (e: Exception) {
			Log.e(TAG, "Failed to analyze database", e)
		}
	}

	/**
	 * Run VACUUM to compact the database and reclaim unused space.
	 * Should be called infrequently (e.g., weekly or when database size is large).
	 * Note: This operation can be slow and locks the database.
	 */
	suspend fun vacuumDatabase() = withContext(Dispatchers.IO) {
		try {
			database.openHelper.writableDatabase.execSQL("VACUUM")
			logDebug { "Database VACUUM completed successfully" }
		} catch (e: Exception) {
			Log.e(TAG, "Failed to vacuum database", e)
		}
	}

	/**
	 * Get the current database file size in bytes.
	 */
	fun getDatabaseSize(): Long {
		return try {
			context.getDatabasePath("kotatsu-db").length()
		} catch (e: Exception) {
			-1L
		}
	}

	/**
	 * Get database statistics including page count, free pages, etc.
	 */
	suspend fun getDatabaseStats(): DatabaseStats = withContext(Dispatchers.IO) {
		try {
			val db = database.openHelper.readableDatabase
			
			val pageCount = queryPragmaInt(db, "page_count")
			val pageSize = queryPragmaInt(db, "page_size")
			val freeListCount = queryPragmaInt(db, "freelist_count")
			
			DatabaseStats(
				sizeBytes = getDatabaseSize(),
				pageCount = pageCount,
				pageSize = pageSize,
				freePages = freeListCount,
				usedPages = pageCount - freeListCount,
				fragmentationPercent = if (pageCount > 0) {
					(freeListCount.toFloat() / pageCount * 100).coerceIn(0f, 100f)
				} else 0f,
			)
		} catch (e: Exception) {
			Log.e(TAG, "Failed to get database stats", e)
			DatabaseStats()
		}
	}

	/**
	 * Explain a query plan for debugging purposes.
	 * Only available in debug builds.
	 */
	suspend fun explainQueryPlan(query: String): List<String> = withContext(Dispatchers.IO) {
		if (!BuildConfig.DEBUG) {
			return@withContext emptyList()
		}
		
		try {
			val db = database.openHelper.readableDatabase
			val results = mutableListOf<String>()
			
			db.query("EXPLAIN QUERY PLAN $query").use { cursor ->
				while (cursor.moveToNext()) {
					val detail = cursor.getString(cursor.getColumnIndexOrThrow("detail"))
					results.add(detail)
				}
			}
			
			results
		} catch (e: Exception) {
			Log.e(TAG, "Failed to explain query plan", e)
			emptyList()
		}
	}

	/**
	 * Get a list of all indexes in the database.
	 */
	suspend fun getIndexList(): List<IndexInfo> = withContext(Dispatchers.IO) {
		try {
			val db = database.openHelper.readableDatabase
			val indexes = mutableListOf<IndexInfo>()
			
			// Get all tables
			db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'").use { tablesCursor ->
				while (tablesCursor.moveToNext()) {
					val tableName = tablesCursor.getString(0)
					
					// Get indexes for each table
					db.query("PRAGMA index_list('$tableName')").use { indexCursor ->
						while (indexCursor.moveToNext()) {
							val indexName = indexCursor.getString(indexCursor.getColumnIndexOrThrow("name"))
							val isUnique = indexCursor.getInt(indexCursor.getColumnIndexOrThrow("unique")) == 1
							
							// Get columns in the index
							val columns = mutableListOf<String>()
							db.query("PRAGMA index_info('$indexName')").use { colCursor ->
								while (colCursor.moveToNext()) {
									columns.add(colCursor.getString(colCursor.getColumnIndexOrThrow("name")))
								}
							}
							
							indexes.add(IndexInfo(
								name = indexName,
								tableName = tableName,
								columns = columns,
								isUnique = isUnique,
							))
						}
					}
				}
			}
			
			indexes
		} catch (e: Exception) {
			Log.e(TAG, "Failed to get index list", e)
			emptyList()
		}
	}

	/**
	 * Check database integrity.
	 */
	suspend fun checkIntegrity(): Boolean = withContext(Dispatchers.IO) {
		try {
			val db = database.openHelper.readableDatabase
			db.query("PRAGMA integrity_check").use { cursor ->
				if (cursor.moveToFirst()) {
					val result = cursor.getString(0)
					return@withContext result == "ok"
				}
			}
			false
		} catch (e: Exception) {
			Log.e(TAG, "Failed to check database integrity", e)
			false
		}
	}

	/**
	 * Optimize database for better performance.
	 * Combines ANALYZE and optional VACUUM based on fragmentation.
	 */
	suspend fun optimize(forceVacuum: Boolean = false) = withContext(Dispatchers.IO) {
		// Always run ANALYZE
		analyzeDatabase()
		
		// Only VACUUM if fragmentation is high or forced
		val stats = getDatabaseStats()
		if (forceVacuum || stats.fragmentationPercent > VACUUM_THRESHOLD_PERCENT) {
			logDebug { "Running VACUUM due to ${stats.fragmentationPercent}% fragmentation" }
			vacuumDatabase()
		}
	}

	private fun queryPragmaInt(db: SupportSQLiteDatabase, pragma: String): Int {
		return db.query("PRAGMA $pragma").use { cursor ->
			if (cursor.moveToFirst()) cursor.getInt(0) else 0
		}
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	data class DatabaseStats(
		val sizeBytes: Long = 0,
		val pageCount: Int = 0,
		val pageSize: Int = 0,
		val freePages: Int = 0,
		val usedPages: Int = 0,
		val fragmentationPercent: Float = 0f,
	) {
		val sizeMb: Float
			get() = sizeBytes / (1024f * 1024f)
	}

	data class IndexInfo(
		val name: String,
		val tableName: String,
		val columns: List<String>,
		val isUnique: Boolean,
	)

	companion object {
		private const val TAG = "DatabaseOptimizer"
		private const val VACUUM_THRESHOLD_PERCENT = 20f
	}
}
