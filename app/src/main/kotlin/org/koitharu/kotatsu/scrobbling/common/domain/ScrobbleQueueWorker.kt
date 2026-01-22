package org.koitharu.kotatsu.scrobbling.common.domain

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import java.util.concurrent.TimeUnit

/**
 * Worker that processes queued scrobbles when network is available.
 * 
 * This worker is scheduled when:
 * - A scrobble fails due to network issues
 * - The app starts and there are queued scrobbles
 * - Network connectivity is restored
 */
@HiltWorker
class ScrobbleQueueWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted workerParams: WorkerParameters,
	private val scrobblingManager: ScrobblingManager,
	private val offlineQueue: ScrobbleOfflineQueue,
) : CoroutineWorker(context, workerParams) {

	override suspend fun doWork(): Result {
		if (offlineQueue.isEmpty) {
			logDebug { "Queue is empty, nothing to process" }
			return Result.success()
		}

		return try {
			val processed = scrobblingManager.processAllQueues()
			logDebug { "Processed $processed queued scrobbles" }

			if (offlineQueue.isEmpty) {
				Result.success()
			} else {
				// Still have items, retry later
				Result.retry()
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug()
			Result.retry()
		}
	}

	private inline fun logDebug(message: () -> String) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, message())
		}
	}

	companion object {
		private const val TAG = "ScrobbleQueueWorker"
		private const val WORK_NAME = "scrobble_queue_processor"

		/**
		 * Schedule the queue processor to run when network is available.
		 */
		fun schedule(context: Context) {
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build()

			val request = OneTimeWorkRequestBuilder<ScrobbleQueueWorker>()
				.setConstraints(constraints)
				.setBackoffCriteria(
					BackoffPolicy.EXPONENTIAL,
					15,
					TimeUnit.MINUTES,
				)
				.build()

			WorkManager.getInstance(context)
				.enqueueUniqueWork(
					WORK_NAME,
					ExistingWorkPolicy.REPLACE,
					request,
				)

			if (BuildConfig.DEBUG) {
				Log.d(TAG, "Scheduled queue processing")
			}
		}

		/**
		 * Cancel any pending queue processing.
		 */
		fun cancel(context: Context) {
			WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
		}
	}
}
