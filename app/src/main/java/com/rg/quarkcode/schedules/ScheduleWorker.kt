package com.rg.quarkcode.schedules

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rg.quarkcode.MainActivity

// Fires a schedule as a notification carrying its prompt.
// Tap opens Quark Code; nothing executes headlessly.
class ScheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val prompt = inputData.getString(KEY_PROMPT) ?: ""
        val open = PendingIntent.getActivity(
            applicationContext,
            title.hashCode(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Schedules", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(prompt)
            .setStyle(NotificationCompat.BigTextStyle().bigText(prompt))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(title.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "quark_schedules"
        const val KEY_TITLE = "title"
        const val KEY_PROMPT = "prompt"
    }
}
