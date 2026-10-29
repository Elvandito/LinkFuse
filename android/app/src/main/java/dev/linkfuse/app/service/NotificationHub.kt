package dev.linkfuse.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.linkfuse.app.MainActivity
import dev.linkfuse.app.R
import dev.linkfuse.app.model.MergeStatus
import dev.linkfuse.app.model.UiState
import dev.linkfuse.app.vpn.MergeVpnService

/** One persistent notification that always tells the truth about the merge. */
object NotificationHub {

    private const val CHANNEL_ID = "linkfuse_status"
    private const val NOTIF_ID = 42

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW   // visible but silent
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            androidx.core.app.NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }

    fun build(context: Context, state: UiState): Notification {
        val text = when (state.status) {
            MergeStatus.MERGING -> context.getString(R.string.notif_merging, state.links.size) +
                speedSuffix(state)
            MergeStatus.PAUSED -> context.getString(R.string.notif_paused)
            MergeStatus.NO_CONNECTIONS -> context.getString(R.string.notif_no_links)
            MergeStatus.ERROR -> context.getString(R.string.notif_error, state.message ?: "unknown")
            MergeStatus.STOPPED -> context.getString(R.string.notif_stopped)
        }

        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggle = if (state.status == MergeStatus.MERGING) {
            action(context, R.string.action_pause, MergeVpnService.ACTION_PAUSE, 1)
        } else {
            action(context, R.string.action_resume, MergeVpnService.ACTION_RESUME, 1)
        }
        val stop = action(context, R.string.action_stop, MergeVpnService.ACTION_STOP, 2)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_link)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.action_pause).let {
                if (state.status == MergeStatus.MERGING) it else context.getString(R.string.action_resume)
            }, toggle)
            .addAction(0, context.getString(R.string.action_stop), stop)
            .build()
    }

    private fun action(context: Context, label: Int, serviceAction: String, code: Int): PendingIntent =
        PendingIntent.getService(
            context, code,
            Intent(context, MergeVpnService::class.java).apply { action = serviceAction },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun speedSuffix(state: UiState): String {
        if (state.totalDown + state.totalUp <= 0) return ""
        return " · ↓${human(state.totalDown)}/s ↑${human(state.totalUp)}/s"
    }

    private fun human(bytes: Long): String = when {
        bytes >= 1_000_000 -> "${(bytes / 1_000_000.0).let { "%.1f".format(it) }} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }
}
