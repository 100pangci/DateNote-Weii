package com.datenote.app.domain.parser

import com.datenote.app.data.remote.AiResponseDto
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.Json

class AiResponseParser(
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {
    fun parse(raw: String, today: LocalDate = LocalDate.now()): ValidatedAiResult {
        val content = cleanJson(raw)
        val response = runCatching { json.decodeFromString<AiResponseDto>(content) }
            .getOrElse { throw AiParseException("invalid response", it) }
        val warnings = response.warnings.toMutableList()
        val items = response.items.mapNotNull { item ->
            val title = item.title.trim()
            if (title.isBlank()) {
                warnings += "有一条安排没有标题"
                return@mapNotNull null
            }
            val date = runCatching { LocalDate.parse(item.date) }.getOrNull()
            if (date == null) {
                warnings += "“$title”的日期没有看清"
                return@mapNotNull null
            }
            if (date.isBefore(today.minusYears(10)) || date.isAfter(today.plusYears(10))) {
                warnings += "“$title”的日期看起来不太合理"
                return@mapNotNull null
            }
            val time = item.time?.takeIf { it.isNotBlank() }?.let { parseTime(it) }
            if (item.time != null && item.time.isNotBlank() && time == null) {
                warnings += "“$title”的时间格式没有看清"
            }
            val reminder = item.remindBeforeMinutes?.takeIf { it in 0..43_200 }
            if (item.remindBeforeMinutes != null && reminder == null) warnings += "“$title”的提醒时间已暂不采用"
            ParsedSchedule(
                title = title,
                date = date,
                time = time,
                category = item.category?.trim()?.takeIf { it.isNotEmpty() },
                note = item.note.trim(),
                remindBeforeMinutes = reminder,
                confidence = item.confidence.coerceIn(0.0, 1.0),
                uncertainties = item.uncertainties.map(String::trim).filter(String::isNotEmpty),
            )
        }
        items.groupBy { Triple(it.title, it.date, it.time) }.filterValues { it.size > 1 }.keys.forEach {
            warnings += "发现了可能重复的安排：${it.first}"
        }
        if (items.isEmpty() && warnings.isEmpty()) throw AiParseException("empty response")
        return ValidatedAiResult(items, warnings.distinct())
    }

    private fun parseTime(value: String): LocalTime? {
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    private fun cleanJson(raw: String): String {
        val trimmed = raw.trim()
        val unfenced = if (trimmed.startsWith("```")) {
            trimmed.removePrefix("```").removePrefix("json").removePrefix("JSON").removeSuffix("```").trim()
        } else trimmed
        val start = unfenced.indexOf('{')
        val end = unfenced.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiParseException("no json object")
        return unfenced.substring(start, end + 1)
    }
}
