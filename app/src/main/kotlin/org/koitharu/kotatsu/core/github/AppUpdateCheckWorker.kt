package org.koitharu.kotatsu.core.github

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically checks for app updates.
 *
 * Features:
 * - Configurable check interval (default: daily)
 * - WiFi-only option for data-conscious users
 * - Respects user preferences for update channels
 * - Shows notification when update is available
 */
@HiltWorker
class AppUpdateCheckWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	private val updateRepository: AppUpdateRepository,
	private val updateNotifier: AppUpdateNotifier,
	private val settings: AppSettings,
) : CoroutineWorker(appContext, params) {

	override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
		try {
			// Check if auto-update check is enabled
			if (!settings.isAutoUpdateCheckEnabled) {
				return@withContext Result.success()
			}

			// Fetch available update
			val update = updateRepository.fetchUpdate()

			if (update != null) {
				// Check if this version was previously skipped
				if (!settings.isVersionSkipped(update.name)) {
					updateNotifier.showUpdateNotification(update)
				}
			}

			// Update last check time
			settings.lastUpdateCheckTime = System.currentTimeMillis()

			Result.success()
		} catch (e: Exception) {
			if (runAttemptCount < MAX_RETRY_COUNT) {
				Result.retry()
			} else {
				Result.failure()
			}
		}
	}

	companion object {
		private const val WORK_NAME = "app_update_check"
		private const val MAX_RETRY_COUNT = 3

		/**
		 * Schedule periodic update checks.
		 */
		fun schedule(context: Context, settings: AppSettings) {
			val workManager = WorkManager.getInstance(context)

			if (!settings.isAutoUpdateCheckEnabled) {
				workManager.cancelUniqueWork(WORK_NAME)
				return
			}

			val intervalHours = settings.updateCheckIntervalHours.toLong()

			val constraints = Constraints.Builder()
				.setRequiredNetworkType(
					if (settings.isUpdateCheckWifiOnly) NetworkType.UNMETERED
					else NetworkType.CONNECTED
				)
				.setRequiresBatteryNotLow(true)
				.build()

			val request = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(
				intervalHours, TimeUnit.HOURS,
				intervalHours / 4, TimeUnit.HOURS, // Flex interval
			)
				.setConstraints(constraints)
				.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
				.build()

			workManager.enqueueUniquePeriodicWork(
				WORK_NAME,
				ExistingPeriodicWorkPolicy.UPDATE,
				request,
			)
		}

		/**
		 * Cancel scheduled update checks.
		 */
		fun cancel(context: Context) {
			WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
		}

		/**
		 * Run an immediate update check.
		 */
		fun checkNow(context: Context) {
			val request = androidx.work.OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
				.setConstraints(
					Constraints.Builder()
						.setRequiredNetworkType(NetworkType.CONNECTED)
						.build()
				)
				.build()
			WorkManager.getInstance(context).enqueue(request)
		}
	}
}
