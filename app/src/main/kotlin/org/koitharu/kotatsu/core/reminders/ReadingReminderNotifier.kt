package org.koitharu.kotatsu.core.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows a daily "time to read" reminder notification.
 */
@Singleton
class ReadingReminderNotifier @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	private val notificationManager = NotificationManagerCompat.from(context)

	init {
		createNotificationChannel()
	}

	@SuppressLint("MissingPermission") // checked in hasNotificationPermission()
	fun showReminder() {
		if (!hasNotificationPermission()) {
			return
		}
		val builder = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_stat_book_plus)
			.setContentTitle(context.getString(R.string.reading_reminder_title))
			.setContentText(context.getString(R.string.reading_reminder_text))
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setCategory(NotificationCompat.CATEGORY_REMINDER)
		val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
		if (launchIntent != null) {
			launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
			builder.setContentIntent(
				PendingIntent.getActivity(
					context,
					NOTIFICATION_ID,
					launchIntent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
				),
			)
		}
		notificationManager.notify(NOTIFICATION_ID, builder.build())
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				context.getString(R.string.reading_reminders),
				NotificationManager.IMPORTANCE_DEFAULT,
			).apply {
				description = context.getString(R.string.reading_reminders_summary)
			}
			notificationManager.createNotificationChannel(channel)
		}
	}

	private fun hasNotificationPermission(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			ContextCompat.checkSelfPermission(
				context,
				Manifest.permission.POST_NOTIFICATIONS,
			) == PackageManager.PERMISSION_GRANTED
		} else {
			true
		}
	}

	companion object {
		private const val CHANNEL_ID = "reading_reminders"
		private const val NOTIFICATION_ID = 10011
	}
}
