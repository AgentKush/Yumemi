package org.koitharu.kotatsu.download.domain

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.core.util.ext.ensureSuccess
import org.koitharu.kotatsu.core.util.ext.writeAllCancellable
import org.koitharu.kotatsu.parsers.util.requireBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles resumable downloads with HTTP Range header support.
 *
 * Features:
 * - Detects server support for range requests (Accept-Ranges header)
 * - Resumes partial downloads from last byte position
 * - Falls back to full download if resume not supported
 * - Validates Content-Range responses
 * - Tracks download progress for resume capability
 */
@Singleton
class ResumableDownloader @Inject constructor(
	@MangaHttpClient private val okHttp: OkHttpClient,
) {

	/**
	 * Download a file with resume support.
	 *
	 * @param request The original OkHttp request
	 * @param targetFile The file to download to (may be partial)
	 * @param progressCallback Optional callback for download progress
	 * @return The completed file
	 */
	suspend fun download(
		request: Request,
		targetFile: File,
		progressCallback: ((downloaded: Long, total: Long) -> Unit)? = null,
	): File = withContext(Dispatchers.IO) {
		val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

		// If file exists and has content, try to resume
		if (existingBytes > 0) {
			logDebug { "Attempting resume from byte $existingBytes for ${request.url}" }

			// First, check if server supports range requests
			val supportsRange = checkRangeSupport(request)

			if (supportsRange) {
				try {
					return@withContext resumeDownload(request, targetFile, existingBytes, progressCallback)
				} catch (e: RangeNotSatisfiableException) {
					logDebug { "Range not satisfiable, file may be complete or corrupted: ${e.message}" }
					// File might be complete or corrupted, verify size
					val contentLength = getContentLength(request)
					if (contentLength > 0 && existingBytes >= contentLength) {
						logDebug { "File appears complete ($existingBytes >= $contentLength)" }
						progressCallback?.invoke(existingBytes, existingBytes)
						return@withContext targetFile
					}
					// File is corrupted, restart download
					targetFile.delete()
				} catch (e: ResumeNotSupportedException) {
					logDebug { "Resume not supported: ${e.message}" }
					targetFile.delete()
				}
			} else {
				logDebug { "Server doesn't support range requests, starting fresh" }
				targetFile.delete()
			}
		}

		// Full download (no resume)
		fullDownload(request, targetFile, progressCallback)
	}

	/**
	 * Check if the server supports HTTP Range requests.
	 */
	private suspend fun checkRangeSupport(request: Request): Boolean {
		return try {
			val headRequest = request.newBuilder()
				.head()
				.build()

			val response = okHttp.newCall(headRequest).execute()
			response.use {
				val acceptRanges = it.header("Accept-Ranges")
				val supportsRange = acceptRanges?.equals("bytes", ignoreCase = true) == true

				// Also check if Content-Length is present (needed for progress)
				val contentLength = it.header("Content-Length")?.toLongOrNull() ?: 0L

				logDebug { "Range support check: Accept-Ranges=$acceptRanges, Content-Length=$contentLength" }
				supportsRange && contentLength > 0
			}
		} catch (e: Exception) {
			logDebug { "Failed to check range support: ${e.message}" }
			false
		}
	}

	/**
	 * Get the content length for a request.
	 */
	private suspend fun getContentLength(request: Request): Long {
		return try {
			val headRequest = request.newBuilder()
				.head()
				.build()

			okHttp.newCall(headRequest).execute().use { response ->
				response.header("Content-Length")?.toLongOrNull() ?: -1L
			}
		} catch (e: Exception) {
			-1L
		}
	}

	/**
	 * Resume a partial download.
	 */
	private suspend fun resumeDownload(
		request: Request,
		targetFile: File,
		existingBytes: Long,
		progressCallback: ((downloaded: Long, total: Long) -> Unit)?,
	): File {
		val rangeRequest = request.newBuilder()
			.header("Range", "bytes=$existingBytes-")
			.build()

		val response = okHttp.newCall(rangeRequest).execute()

		return response.use { resp ->
			when (resp.code) {
				206 -> {
					// Partial content - resume successful
					val contentRange = resp.header("Content-Range")
					val totalSize = parseContentRangeTotal(contentRange) ?: run {
						// Fallback: use Content-Length + existing bytes
						val contentLength = resp.body?.contentLength() ?: 0L
						existingBytes + contentLength
					}

					logDebug { "Resuming download: existing=$existingBytes, total=$totalSize" }

					resp.requireBody().use { body ->
						// Append to existing file
						targetFile.sink(append = true).buffer().use { sink ->
							var downloaded = existingBytes
							val source = body.source()
							val buffer = okio.Buffer()

							while (true) {
								val read = source.read(buffer, BUFFER_SIZE)
								if (read == -1L) break

								sink.write(buffer, read)
								downloaded += read
								progressCallback?.invoke(downloaded, totalSize)
							}
						}
					}
					targetFile
				}
				416 -> {
					// Range Not Satisfiable
					throw RangeNotSatisfiableException("Server returned 416: Range not satisfiable")
				}
				200 -> {
					// Server ignored range request, sent full file
					throw ResumeNotSupportedException("Server returned 200 instead of 206")
				}
				else -> {
					resp.ensureSuccess()
					throw IOException("Unexpected response code: ${resp.code}")
				}
			}
		}
	}

	/**
	 * Perform a full download (no resume).
	 */
	private suspend fun fullDownload(
		request: Request,
		targetFile: File,
		progressCallback: ((downloaded: Long, total: Long) -> Unit)?,
	): File {
		val response = okHttp.newCall(request).execute()

		return response.use { resp ->
			resp.ensureSuccess()

			resp.requireBody().use { body ->
				val totalSize = body.contentLength().takeIf { it > 0 } ?: -1L

				targetFile.sink(append = false).buffer().use { sink ->
					if (progressCallback != null && totalSize > 0) {
						// Download with progress tracking
						var downloaded = 0L
						val source = body.source()
						val buffer = okio.Buffer()

						while (true) {
							val read = source.read(buffer, BUFFER_SIZE)
							if (read == -1L) break

							sink.write(buffer, read)
							downloaded += read
							progressCallback.invoke(downloaded, totalSize)
						}
					} else {
						// Simple download without progress
						sink.writeAllCancellable(body.source())
					}
				}
			}
			targetFile
		}
	}

	/**
	 * Parse the total size from Content-Range header.
	 * Format: bytes start-end/total
	 * Example: bytes 1000-1999/5000
	 */
	private fun parseContentRangeTotal(contentRange: String?): Long? {
		if (contentRange == null) return null

		return try {
			val parts = contentRange.split("/")
			if (parts.size == 2) {
				val total = parts[1].trim()
				if (total != "*") {
					total.toLongOrNull()
				} else {
					null
				}
			} else {
				null
			}
		} catch (e: Exception) {
			null
		}
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	class RangeNotSatisfiableException(message: String) : IOException(message)
	class ResumeNotSupportedException(message: String) : IOException(message)

	companion object {
		private const val TAG = "ResumableDownloader"
		private const val BUFFER_SIZE = 8192L
	}
}
