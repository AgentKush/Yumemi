package org.koitharu.kotatsu.core.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Periodically (daily) posts a reading reminder notification at the user-configured hour.
 */
@HiltWorker
class ReadingReminderWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	private val notifier: ReadingReminderNotifier,
	private val settings: AppSettings,
) : CoroutineWorker(appContext, params) {

	override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
		if (settings.isReadingReminderEnabled) {
			notifier.showReminder()
		}
		Result.success()
	}

	companion object {
		private const val WORK_NAME = "reading_reminder"

		fun schedule(context: Context, settings: AppSettings) {
			val workManager = WorkManager.getInstance(context)
			if (!settings.isReadingReminderEnabled) {
				workManager.cancelUniqueWork(WORK_NAME)
				return
			}
			val constraints = Constraints.Builder()
				.setRequiresBatteryNotLow(true)
				.build()
			val request = PeriodicWorkRequestBuilder<ReadingReminderWorker>(24, TimeUnit.HOURS)
				.setConstraints(constraints)
				.setInitialDelay(initialDelayMinutes(settings.readingReminderHour), TimeUnit.MINUTES)
				.build()
			workManager.enqueueUniquePeriodicWork(
				WORK_NAME,
				ExistingPeriodicWorkPolicy.UPDATE,
				request,
			)
		}

		fun cancel(context: Context) {
			WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
		}

		private fun initialDelayMinutes(hour: Int): Long {
			val now = Calendar.getInstance()
			val target = Calendar.getInstance().apply {
				set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}
			if (target.timeInMillis <= now.timeInMillis) {
				target.add(Calendar.DAY_OF_YEAR, 1)
			}
			return ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
		}
	}
}
