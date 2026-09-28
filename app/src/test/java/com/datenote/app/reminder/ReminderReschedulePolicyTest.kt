package com.datenote.app.reminder

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReschedulePolicyTest {
    private val now = Instant.parse("2026-09-28T12:00:00Z")

    @Test fun resumeSchedulesOnlyFutureTargetsAndDoesNotReplayMissedOnes() {
        assertTrue(shouldScheduleReminderAt(now.plusSeconds(1), now))
        assertFalse(shouldScheduleReminderAt(now, now))
        assertFalse(shouldScheduleReminderAt(now.minusSeconds(1), now))
        assertFalse(shouldScheduleReminderAt(null, now))
    }

    @Test fun resumePreservesAlreadyDueEnqueuedOrRunningWorkButNotFutureStaleWork() {
        val nowMillis = now.toEpochMilli()
        assertTrue(shouldPreserveDueWorkOnResume(isEnqueued = true, isRunning = false, nextScheduleTimeMillis = nowMillis, nowMillis = nowMillis))
        assertTrue(shouldPreserveDueWorkOnResume(isEnqueued = false, isRunning = true, nextScheduleTimeMillis = nowMillis + 60_000, nowMillis = nowMillis))
        assertFalse(shouldPreserveDueWorkOnResume(isEnqueued = true, isRunning = false, nextScheduleTimeMillis = nowMillis + 60_000, nowMillis = nowMillis))
        assertFalse(shouldPreserveDueWorkOnResume(isEnqueued = false, isRunning = false, nextScheduleTimeMillis = nowMillis, nowMillis = nowMillis))
    }
}
