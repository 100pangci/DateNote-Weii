package com.datenote.app.domain

import com.datenote.app.domain.parser.AiParseException
import com.datenote.app.domain.parser.AiResponseParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseParserTest {
    private val parser = AiResponseParser()

    @Test fun parsesNormalJsonAndMultipleItems() {
        val result = parser.parse(
            """{"items":[{"title":"发美肌","date":"2026-10-20","time":null,"category":null,"note":"之前完成","remindBeforeMinutes":4320,"confidence":0.92,"uncertainties":[]},{"title":"联系小优","date":"2026-09-21","time":"09:30","category":"联系","note":"","remindBeforeMinutes":null,"confidence":1.2,"uncertainties":[]}],"warnings":[]}""",
            LocalDate.of(2026, 9, 20),
        )
        assertEquals(2, result.items.size)
        assertEquals(0.92, result.items.first().confidence, 0.001)
        assertEquals(1.0, result.items[1].confidence, 0.001)
        val time = result.items[1].time!!
        assertEquals(570, time.hour * 60 + time.minute)
    }

    @Test fun removesMarkdownFence() {
        val result = parser.parse("""```json
            {"items":[{"title":"册子","date":"2026-10-24","time":null}],"warnings":[]}
            ```""", LocalDate.of(2026, 9, 20))
        assertEquals("册子", result.items.single().title)
    }

    @Test fun invalidDateIsReturnedAsWarningInsteadOfStored() {
        val result = parser.parse("""{"items":[{"title":"坏日期","date":"not-a-date"}],"warnings":[]}""")
        assertTrue(result.items.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test(expected = AiParseException::class)
    fun damagedJsonFailsClearly() {
        parser.parse("not json")
    }

    @Test(expected = AiParseException::class)
    fun emptyResponseIsRejected() {
        parser.parse("{\"items\":[],\"warnings\":[]}")
    }

    @Test fun duplicateItemsProduceWarning() {
        val result = parser.parse("""{"items":[{"title":"凛","date":"2026-11-01"},{"title":"凛","date":"2026-11-01"}],"warnings":[]}""", LocalDate.of(2026, 9, 20))
        assertTrue(result.warnings.any { it.contains("重复") })
    }

    @Test fun parsesDateRangeAndOrderedStepsIncludingCompletedState() {
        val result = parser.parse(
            """{"items":[{"title":"制作小优的衣服","startDate":"2026-10-10","endDate":"2026-10-17","steps":[{"title":"买材料","isCompleted":true},{"title":"制作","isCompleted":false},{"title":"打包","isCompleted":false}]}],"warnings":[]}""",
            LocalDate.of(2026, 9, 20),
        )
        val item = result.items.single()
        assertEquals(LocalDate.of(2026, 10, 10), item.startDate)
        assertEquals(LocalDate.of(2026, 10, 17), item.endDate)
        assertEquals(listOf("买材料", "制作", "打包"), item.steps.map { it.title })
        assertTrue(item.steps.first().isCompleted)
    }

    @Test fun missingStartDateIsMarkedUncertainWhenOnlyEndDateExists() {
        val item = parser.parse(
            """{"items":[{"title":"发美肌","endDate":"2026-10-20"}],"warnings":[]}""",
            LocalDate.of(2026, 9, 20),
        ).items.single()
        assertEquals(item.startDate, item.endDate)
        assertTrue(item.uncertainties.any { it.contains("开始日期") })
    }
}
