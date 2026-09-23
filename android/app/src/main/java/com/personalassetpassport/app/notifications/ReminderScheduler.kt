package com.personalassetpassport.app.notifications

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
import androidx.work.*
import com.personalassetpassport.app.MainActivity
import com.personalassetpassport.app.PassportApplication
import com.personalassetpassport.app.R
import com.personalassetpassport.app.data.Reminder
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context) {
    private val work = WorkManager.getInstance(context)

    fun allowed() =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun schedule(reminder: Reminder) {
        if (reminder.delivered) return
        val request =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf("id" to reminder.id))
                .setInitialDelay(
                    maxOf(0, reminder.dueAt - System.currentTimeMillis()),
                    TimeUnit.MILLISECONDS,
                )
                .addTag("asset-reminders")
                .build()
        work.enqueueUniqueWork("reminder-${reminder.id}", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(id: String) {
        work.cancelUniqueWork("reminder-$id")
        NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    fun restore(reminders: List<Reminder>) {
        reminders.filterNot { it.delivered }.forEach(::schedule)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PassportApplication
        val id = inputData.getString("id") ?: return Result.failure()
        val reminder = app.repository.dao.reminder(id) ?: return Result.success()
        val asset = app.repository.dao.asset(reminder.assetId) ?: return Result.success()
        if (asset.deletedAt != null || reminder.delivered) return Result.success()
        if (!app.scheduler.allowed())
            return Result.success() // Kept pending locally; settings can reschedule.
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "asset-reminders",
                applicationContext.getString(R.string.reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val intent =
            Intent(applicationContext, MainActivity::class.java).putExtra("assetId", asset.id)
        val pending =
            PendingIntent.getActivity(
                applicationContext,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(applicationContext, "asset-reminders")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(reminder.title)
                .setContentText(asset.name)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        return try {
            manager.notify(id.hashCode(), notification)
            app.repository.dao.reminder(reminder.copy(delivered = true))
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
}
