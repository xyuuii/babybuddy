package com.yueming.baby.data

import android.Manifest
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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yueming.baby.MainActivity
import com.yueming.baby.R

class ReminderNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "宝宝提醒"
        val notes = inputData.getString(KEY_NOTES).orEmpty()
        val reminderId = inputData.getString(KEY_REMINDER_ID).orEmpty()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        ensureChannel()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val content = notes.ifBlank { "有一条待办提醒到时间了" }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify((NOTIFICATION_BASE_ID + reminderId.hashCode()).absoluteValue, notification)
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "待办提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "复查、疫苗、用药和检查提醒"
        }
        manager.createNotificationChannel(channel)
    }

    private val Int.absoluteValue: Int
        get() = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TITLE = "title"
        const val KEY_NOTES = "notes"
        const val KEY_DUE_AT = "due_at"
        private const val CHANNEL_ID = "babybuddy_reminders"
        private const val NOTIFICATION_BASE_ID = 12_000
    }
}
