package org.koitharu.kotatsu.core.github

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Broadcast receiver for handling update notification dismissal.
 */
@AndroidEntryPoint
class AppUpdateDismissReceiver : BroadcastReceiver() {

	@Inject
	lateinit var updateNotifier: AppUpdateNotifier

	override fun onReceive(context: Context, intent: Intent) {
		updateNotifier.cancelNotification()
	}
}
