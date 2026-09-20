package com.datenote.app.domain.parser

import java.time.LocalDate
import java.time.LocalTime

data class ParsedSchedule(
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val category: String?,
    val note: String,
    val remindBeforeMinutes: Long?,
    val confidence: Double,
    val uncertainties: List<String>,
)

data class ValidatedAiResult(
    val items: List<ParsedSchedule>,
    val warnings: List<String>,
)

class AiParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
