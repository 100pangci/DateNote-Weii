package com.datenote.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.datenote.app.DateNoteApplication
import com.datenote.app.R
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.progress
import com.datenote.app.data.local.ScheduleEntity
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderNotifications {
    const val CHANNEL_ID = "schedule_reminders"
    const val SCHEDULE_ID = "schedule_id"
    const val WORK_TAG = "schedule-reminders"

    fun createChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(android.app.NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), android.app.NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notification_channel_description)
            })
        }
    }
}

class ReminderScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun sync(schedule: com.datenote.app.data.local.ScheduleEntity, defaultReminderTimeMinutes: Int = 9 * 60) {
        cancel(schedule.id)
        if (schedule.remindBeforeMinutes == null || schedule.status == ScheduleStatus.COMPLETED) return
        if (!NotificationAccess.status(context).canPost) return
        val reminderAt = reminderDateTime(schedule, defaultReminderTimeMinutes)?.atZone(ZoneId.systemDefault()) ?: return
        val delay = Duration.between(ZonedDateTime.now(ZoneId.systemDefault()).toInstant(), reminderAt.toInstant()).toMillis()
        if (delay <= 0) return
        val request = OneTimeWorkRequestBuilder<ScheduleReminderWorker>()
            .setInputData(workDataOf(ReminderNotifications.SCHEDULE_ID to schedule.id))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(ReminderNotifications.WORK_TAG)
            .addTag(uniqueName(schedule.id))
            .build()
        workManager.enqueueUniqueWork(uniqueName(schedule.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(id: Long) { workManager.cancelUniqueWork(uniqueName(id)) }

    fun cancelAll() { workManager.cancelAllWorkByTag(ReminderNotifications.WORK_TAG) }

    suspend fun rescheduleAll(schedules: List<ScheduleEntity>, defaultReminderTimeMinutes: Int) {
        if (!NotificationAccess.status(context).canPost) {
            cancelAll()
            return
        }
        schedules.forEach { sync(it, defaultReminderTimeMinutes) }
    }

    private fun uniqueName(id: Long): String = "schedule-reminder-$id"
}

class ScheduleReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(ReminderNotifications.SCHEDULE_ID, 0L)
        if (id == 0L) return Result.failure()
        val app = applicationContext as DateNoteApplication
        val scheduleWithSteps = app.scheduleRepository.getWithSteps(id) ?: return Result.success()
        val schedule = scheduleWithSteps.schedule
        if (schedule.status == ScheduleStatus.COMPLETED || schedule.remindBeforeMinutes == null) return Result.success()
        if (!NotificationAccess.status(applicationContext).canPost) return Result.success()
        ReminderNotifications.createChannel(applicationContext)
        val today = LocalDate.now()
        val days = schedule.endEpochDay - today.toEpochDay()
        val baseBody = when {
            days < 0 -> applicationContext.getString(R.string.notification_overdue, -days)
            days == 0L -> applicationContext.getString(R.string.notification_today)
            days == 1L -> applicationContext.getString(R.string.notification_tomorrow)
            else -> applicationContext.getString(R.string.notification_days, days)
        }
        val progress = scheduleWithSteps.progress
        val body = buildString {
            append(baseBody)
            if (progress.hasSteps) {
                append("\n")
                append(applicationContext.getString(R.string.notification_progress, progress.completedCount, progress.totalCount))
                scheduleWithSteps.orderedSteps.firstOrNull { !it.isCompleted }?.let {
                    append("\n")
                    append(applicationContext.getString(R.string.notification_next_step, it.title))
                }
            }
        }
        val intent = android.content.Intent(applicationContext, com.datenote.app.MainActivity::class.java)
            .putExtra(ReminderNotifications.SCHEDULE_ID, id)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(applicationContext, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(schedule.title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(android.app.NotificationManager::class.java).notify(id.hashCode(), notification)
        return Result.success()
    }
}
