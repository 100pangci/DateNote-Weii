package com.datenote.app.domain.model

import com.datenote.app.data.local.ScheduleWithSteps

private val homeScheduleComparator = compareBy<ScheduleWithSteps>(
    { it.schedule.status == ScheduleStatus.COMPLETED },
    { it.schedule.endEpochDay },
    { it.schedule.minuteOfDay == null },
    { it.schedule.minuteOfDay ?: Int.MAX_VALUE },
    { it.schedule.startEpochDay },
    { it.schedule.id },
)

private val scheduleDateComparator = compareBy<ScheduleWithSteps>(
    { it.schedule.endEpochDay },
    { it.schedule.minuteOfDay == null },
    { it.schedule.minuteOfDay ?: Int.MAX_VALUE },
    { it.schedule.startEpochDay },
    { it.schedule.id },
)

/** Orders schedules shown for one selected day by what needs attention first. */
fun Iterable<ScheduleWithSteps>.sortForHome(): List<ScheduleWithSteps> =
    sortedWith(homeScheduleComparator)

/**
 * Orders the all-schedules list by urgency. Completed schedules stay at the end
 * and are ordered by the time they were completed, newest first.
 */
fun Iterable<ScheduleWithSteps>.sortForAll(todayEpochDay: Long): List<ScheduleWithSteps> =
    sortedWith { left, right ->
        val bucketComparison = allSchedulesBucket(left, todayEpochDay)
            .compareTo(allSchedulesBucket(right, todayEpochDay))
        if (bucketComparison != 0) {
            bucketComparison
        } else if (
            left.schedule.status == ScheduleStatus.COMPLETED &&
            right.schedule.status == ScheduleStatus.COMPLETED
        ) {
            compareCompletedAt(left, right).takeIf { it != 0 }
                ?: scheduleDateComparator.compare(left, right)
        } else {
            scheduleDateComparator.compare(left, right)
        }
    }

private fun allSchedulesBucket(item: ScheduleWithSteps, todayEpochDay: Long): Int = when {
    item.schedule.status == ScheduleStatus.COMPLETED -> 3
    item.schedule.endEpochDay < todayEpochDay -> 0
    item.schedule.startEpochDay <= todayEpochDay -> 1
    else -> 2
}

private fun compareCompletedAt(left: ScheduleWithSteps, right: ScheduleWithSteps): Int {
    val leftCompletedAt = left.schedule.completedAt
    val rightCompletedAt = right.schedule.completedAt
    return when {
        leftCompletedAt == rightCompletedAt -> 0
        leftCompletedAt == null -> 1
        rightCompletedAt == null -> -1
        leftCompletedAt > rightCompletedAt -> -1
        else -> 1
    }
}
