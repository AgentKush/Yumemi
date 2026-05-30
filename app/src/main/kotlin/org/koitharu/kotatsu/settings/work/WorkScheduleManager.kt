package org.koitharu.kotatsu.settings.work

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.github.AppUpdateCheckWorker
import org.koitharu.kotatsu.core.reminders.ReadingReminderWorker
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.processLifecycleScope
import org.koitharu.kotatsu.suggestions.ui.SuggestionsWorker
import org.koitharu.kotatsu.tracker.work.TrackWorker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduleManager @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	private val suggestionScheduler: SuggestionsWorker.Scheduler,
	private val trackerScheduler: TrackWorker.Scheduler,
) : SharedPreferences.OnSharedPreferenceChangeListener {

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_TRACKER_ENABLED,
			AppSettings.KEY_TRACKER_FREQUENCY,
			AppSettings.KEY_TRACKER_WIFI_ONLY -> updateWorker(
				scheduler = trackerScheduler,
				isEnabled = settings.isTrackerEnabled,
				force = key != AppSettings.KEY_TRACKER_ENABLED,
			)

			AppSettings.KEY_SUGGESTIONS,
			AppSettings.KEY_SUGGESTIONS_WIFI_ONLY -> updateWorker(
				scheduler = suggestionScheduler,
				isEnabled = settings.isSuggestionsEnabled,
				force = key != AppSettings.KEY_SUGGESTIONS,
			)

			AppSettings.KEY_AUTO_UPDATE_CHECK,
			AppSettings.KEY_UPDATE_CHECK_WIFI_ONLY,
			AppSettings.KEY_UPDATE_CHECK_INTERVAL -> {
				AppUpdateCheckWorker.schedule(context, settings)
			}

			AppSettings.KEY_READING_REMINDER,
			AppSettings.KEY_READING_REMINDER_HOUR -> {
				ReadingReminderWorker.schedule(context, settings)
			}
		}
	}

	fun init() {
		settings.subscribe(this)
		processLifecycleScope.launch(Dispatchers.Default) {
			updateWorkerImpl(trackerScheduler, settings.isTrackerEnabled, true) // always force due to adaptive interval
			updateWorkerImpl(suggestionScheduler, settings.isSuggestionsEnabled, false)
			// Schedule app update checks
			AppUpdateCheckWorker.schedule(context, settings)
			ReadingReminderWorker.schedule(context, settings)
		}
	}

	private fun updateWorker(scheduler: PeriodicWorkScheduler, isEnabled: Boolean, force: Boolean) {
		processLifecycleScope.launch(Dispatchers.Default) {
			updateWorkerImpl(scheduler, isEnabled, force)
		}
	}

	private suspend fun updateWorkerImpl(scheduler: PeriodicWorkScheduler, isEnabled: Boolean, force: Boolean) {
		if (force || scheduler.isScheduled() != isEnabled) {
			if (isEnabled) {
				scheduler.schedule()
			} else {
				scheduler.unschedule()
			}
		}
	}
}
