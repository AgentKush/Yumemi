package org.koitharu.kotatsu.core.github

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
import org.koitharu.kotatsu.core.util.FileSize
import org.koitharu.kotatsu.settings.about.AppUpdateActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles app update notifications.
 */
@Singleton
class AppUpdateNotifier @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	private val notificationManager = NotificationManagerCompat.from(context)

	init {
		createNotificationChannel()
	}

	/**
	 * Show a notification about an available update.
	 */
	@SuppressLint("MissingPermission") // Permission is checked in hasNotificationPermission()
	fun showUpdateNotification(version: AppVersion) {
		if (!hasNotificationPermission()) {
			return
		}

		val intent = Intent(context, AppUpdateActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
		}

		val pendingIntent = PendingIntent.getActivity(
			context,
			NOTIFICATION_ID,
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_updated)
			.setContentTitle(context.getString(R.string.new_version_s, version.name))
			.setContentText(
				context.getString(R.string.size_s, FileSize.BYTES.format(context, version.apkSize))
			)
			.setStyle(
				NotificationCompat.BigTextStyle()
					.bigText(buildNotificationText(version))
			)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
			.addAction(
				R.drawable.ic_download,
				context.getString(R.string.download),
				pendingIntent,
			)
			.addAction(
				0,
				context.getString(R.string.close),
				createDismissIntent(),
			)
			.build()

		notificationManager.notify(NOTIFICATION_ID, notification)
	}

	/**
	 * Cancel the update notification.
	 */
	fun cancelNotification() {
		notificationManager.cancel(NOTIFICATION_ID)
	}

	private fun buildNotificationText(version: AppVersion): String {
		return buildString {
			append(context.getString(R.string.new_version_s, version.name))
			appendLine()
			append(context.getString(R.string.size_s, FileSize.BYTES.format(context, version.apkSize)))

			// Add first few lines of changelog if available
			val changelog = version.description.lines()
				.filter { it.isNotBlank() }
				.take(3)
			if (changelog.isNotEmpty()) {
				appendLine()
				appendLine()
				append(context.getString(R.string.changelog))
				append(":")
				changelog.forEach { line ->
					appendLine()
					append("• ")
					append(line.removePrefix("- ").removePrefix("* ").trim())
				}
			}
		}
	}

	private fun createDismissIntent(): PendingIntent {
		val intent = Intent(context, AppUpdateDismissReceiver::class.java)
		return PendingIntent.getBroadcast(
			context,
			DISMISS_REQUEST_CODE,
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				context.getString(R.string.updates),
				NotificationManager.IMPORTANCE_LOW,
			).apply {
				description = context.getString(R.string.updates)
				setShowBadge(true)
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
		private const val CHANNEL_ID = "app_updates"
		private const val NOTIFICATION_ID = 10001
		private const val DISMISS_REQUEST_CODE = 10002
	}
}
