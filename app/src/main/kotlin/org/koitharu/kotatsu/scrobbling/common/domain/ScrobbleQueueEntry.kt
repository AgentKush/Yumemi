package org.koitharu.kotatsu.scrobbling.common.domain

import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService

/**
 * Represents a pending scrobble operation that failed due to network issues
 * and needs to be retried when connectivity is restored.
 */
data class ScrobbleQueueEntry(
	val scrobblerService: ScrobblerService,
	val mangaId: Long,
	val chapterId: Long,
	val timestamp: Long = System.currentTimeMillis(),
) {
	/**
	 * Serialize to string for storage.
	 * Format: "scrobblerId:mangaId:chapterId:timestamp"
	 */
	fun serialize(): String = "${scrobblerService.id}:$mangaId:$chapterId:$timestamp"

	companion object {
		/**
		 * Deserialize from string.
		 * @return ScrobbleQueueEntry or null if parsing fails
		 */
		fun deserialize(data: String): ScrobbleQueueEntry? {
			val parts = data.split(":")
			if (parts.size != 4) return null
			return try {
				val scrobblerId = parts[0].toInt()
				val service = ScrobblerService.entries.find { it.id == scrobblerId } ?: return null
				ScrobbleQueueEntry(
					scrobblerService = service,
					mangaId = parts[1].toLong(),
					chapterId = parts[2].toLong(),
					timestamp = parts[3].toLong(),
				)
			} catch (e: NumberFormatException) {
				null
			}
		}
	}
}
