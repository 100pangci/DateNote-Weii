package com.datenote.app.domain.parser

import java.time.LocalDate
import java.time.LocalTime

data class ParsedStep(
    val title: String,
    val isCompleted: Boolean,
)

data class ParsedSchedule(
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val time: LocalTime?,
    val category: String?,
    val note: String,
    val remindBeforeMinutes: Long?,
    val steps: List<ParsedStep>,
    val confidence: Double,
    val uncertainties: List<String>,
) {
    val date: LocalDate get() = startDate
}

data class ValidatedAiResult(
    val items: List<ParsedSchedule>,
    val warnings: List<String>,
)

class AiParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
