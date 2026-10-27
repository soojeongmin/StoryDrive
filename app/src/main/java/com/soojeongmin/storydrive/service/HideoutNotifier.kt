package com.soojeongmin.storydrive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soojeongmin.storydrive.R
import com.soojeongmin.storydrive.StoryDriveApp
import com.soojeongmin.storydrive.story.HideoutPrompt

class HideoutNotifier(private val context: Context) : HideoutPrompt {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.hideout_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun ask(description: String) {
        val body = description.ifBlank { context.getString(R.string.hideout_body_default) }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_book)
            .setContentTitle(context.getString(R.string.hideout_title))
            .setContentText(body)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setTimeoutAfter(TIMEOUT_MS)
            .setAutoCancel(true)
            .addAction(action(R.string.hideout_stop, stop = true))
            .addAction(action(R.string.hideout_skip, stop = false))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun dismiss() = manager.cancel(NOTIFICATION_ID)

    private fun action(label: Int, stop: Boolean): Notification.Action {
        val intent = Intent(context, HideoutReceiver::class.java).putExtra(EXTRA_STOP, stop)
        val pending = PendingIntent.getBroadcast(
            context, if (stop) 1 else 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, context.getString(label), pending).build()
    }

    companion object {
        private const val CHANNEL_ID = "hideout_quiet"
        private const val LEGACY_CHANNEL_ID = "hideout"
        private const val NOTIFICATION_ID = 11
        private const val TIMEOUT_MS = 180_000L
        const val EXTRA_STOP = "stop"
    }
}

class HideoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = (context.applicationContext as StoryDriveApp).engine
        engine.decideHideout(intent.getBooleanExtra(HideoutNotifier.EXTRA_STOP, false))
        HideoutNotifier(context.applicationContext).dismiss()
    }
}
