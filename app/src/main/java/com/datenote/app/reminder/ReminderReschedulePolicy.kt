package com.datenote.app.reminder

import java.time.Instant

/**
 * A resume pass replaces only future reminders. Due/past reminders are left as-is so
 * an already-enqueued WorkManager job can finish; a missed reminder is not replayed.
 */
internal fun shouldScheduleReminderAt(reminderAt: Instant?, now: Instant): Boolean =
    reminderAt != null && reminderAt.isAfter(now)

internal fun shouldPreserveDueWorkOnResume(
    isEnqueued: Boolean,
    isRunning: Boolean,
    nextScheduleTimeMillis: Long,
    nowMillis: Long,
): Boolean = isRunning || (isEnqueued && nextScheduleTimeMillis <= nowMillis)
