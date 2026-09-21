package com.datenote.app.domain

import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.sortForAll
import com.datenote.app.domain.model.sortForHome
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleSortingTest {
    @Test
    fun homePutsIncompleteSchedulesByNearestEndDateAndCompletedLast() {
        val schedules = listOf(
            schedule("已完成", id = 1, start = 80, end = 90, status = ScheduleStatus.COMPLETED),
            schedule("稍后", id = 2, start = 100, end = 110),
            schedule("无时间", id = 3, start = 100, end = 105),
            schedule("下午", id = 4, start = 100, end = 105, minute = 900),
            schedule("上午", id = 5, start = 100, end = 105, minute = 600),
        )

        assertEquals(
            listOf("上午", "下午", "无时间", "稍后", "已完成"),
            schedules.sortForHome().map { it.schedule.title },
        )
    }

    @Test
    fun allSchedulesUseUrgencyBucketsAndRecentCompletionFirst() {
        val schedules = listOf(
            schedule("较早完成", id = 1, start = 70, end = 80, status = ScheduleStatus.COMPLETED, completedAt = 100),
            schedule("即将开始", id = 2, start = 120, end = 130),
            schedule("已逾期", id = 3, start = 70, end = 90),
            schedule("最近完成", id = 4, start = 60, end = 70, status = ScheduleStatus.COMPLETED, completedAt = 200),
            schedule("进行中", id = 5, start = 90, end = 110),
        )

        assertEquals(
            listOf("已逾期", "进行中", "即将开始", "最近完成", "较早完成"),
            schedules.sortForAll(todayEpochDay = 100).map { it.schedule.title },
        )
    }

    private fun schedule(
        title: String,
        id: Long,
        start: Long,
        end: Long,
        status: ScheduleStatus = ScheduleStatus.TODO,
        minute: Int? = null,
        completedAt: Long? = null,
    ) = ScheduleWithSteps(
        schedule = ScheduleEntity(
            id = id,
            title = title,
            startEpochDay = start,
            endEpochDay = end,
            minuteOfDay = minute,
            status = status,
            completedAt = completedAt,
        ),
        steps = emptyList(),
    )
}
