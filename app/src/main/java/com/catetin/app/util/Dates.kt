package com.catetin.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodPreset { TODAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_7_DAYS, ALL }

data class DateRange(val start: Long, val end: Long)

object Dates {
    private val id = Locale("id", "ID")
    private val dayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", id)
    private val shortDayFormat = DateTimeFormatter.ofPattern("d MMM", id)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", id)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", id)
    private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", id)

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun now(): LocalDateTime = LocalDateTime.now(zone())

    fun range(preset: PeriodPreset, today: LocalDate = LocalDate.now(zone())): DateRange {
        val start: LocalDate
        val end: LocalDate
        when (preset) {
            PeriodPreset.TODAY -> {
                start = today
                end = today
            }
            PeriodPreset.THIS_WEEK -> {
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                end = start.plusDays(6)
            }
            PeriodPreset.LAST_WEEK -> {
                end = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1)
                start = end.minusDays(6)
            }
            PeriodPreset.THIS_MONTH -> {
                start = today.withDayOfMonth(1)
                end = today.with(TemporalAdjusters.lastDayOfMonth())
            }
            PeriodPreset.LAST_MONTH -> {
                val lastMonth = today.minusMonths(1)
                start = lastMonth.withDayOfMonth(1)
                end = lastMonth.with(TemporalAdjusters.lastDayOfMonth())
            }
            PeriodPreset.THIS_YEAR -> {
                start = today.withDayOfYear(1)
                end = today.with(TemporalAdjusters.lastDayOfYear())
            }
            PeriodPreset.LAST_7_DAYS -> {
                start = today.minusDays(6)
                end = today
            }
            PeriodPreset.ALL -> {
                start = LocalDate.of(2000, 1, 1)
                end = today
            }
        }
        return DateRange(
            start = start.atStartOfDay(zone()).toInstant().toEpochMilli(),
            end = end.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli() - 1,
        )
    }

    fun startOfToday(): Long = LocalDate.now(zone()).atStartOfDay(zone()).toInstant().toEpochMilli()

    fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().format(dayFormat)

    fun formatShortDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().format(shortDayFormat)

    fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalTime().format(timeFormat)

    fun formatDateTime(millis: Long): String = formatDate(millis) + " · " + formatTime(millis)

    fun formatMonth(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().format(monthFormat)

    fun formatWeekday(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().format(weekdayFormat)

    fun dayHeader(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()
        val today = LocalDate.now(zone())
        return when (date) {
            today -> "Hari ini"
            today.minusDays(1) -> "Kemarin"
            else -> date.format(dayFormat)
        }
    }

    fun monthLabel(date: LocalDate = LocalDate.now(zone())): String = date.format(monthFormat)

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDateTime()

    fun periodLabel(preset: PeriodPreset): String = when (preset) {
        PeriodPreset.TODAY -> "Hari ini"
        PeriodPreset.THIS_WEEK -> "Minggu ini"
        PeriodPreset.LAST_WEEK -> "Minggu lalu"
        PeriodPreset.THIS_MONTH -> "Bulan ini"
        PeriodPreset.LAST_MONTH -> "Bulan lalu"
        PeriodPreset.THIS_YEAR -> "Tahun ini"
        PeriodPreset.LAST_7_DAYS -> "7 hari terakhir"
        PeriodPreset.ALL -> "Semua waktu"
    }
}
