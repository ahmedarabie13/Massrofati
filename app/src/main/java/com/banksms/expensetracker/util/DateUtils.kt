package com.banksms.expensetracker.util

import java.text.SimpleDateFormat
import java.util.*

enum class DateRangePreset(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time"),
    CUSTOM("Custom Range")
}

data class DateRange(
    val preset: DateRangePreset,
    val startTime: Long,
    val endTime: Long,
    val label: String
)

object DateUtils {

    private val fullDateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun formatFullDateTime(timestamp: Long): String = fullDateTimeFormat.format(Date(timestamp))
    fun formatShortDate(timestamp: Long): String = shortDateFormat.format(Date(timestamp))
    fun formatMonthYear(timestamp: Long): String = monthYearFormat.format(Date(timestamp))
    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

    fun getDateRange(preset: DateRangePreset, customStart: Long? = null, customEnd: Long? = null): DateRange {
        val calendar = Calendar.getInstance()

        return when (preset) {
            DateRangePreset.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis

                DateRange(preset, start, end, "This Month (${formatMonthYear(start)})")
            }

            DateRangePreset.LAST_MONTH -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis

                DateRange(preset, start, end, "Last Month (${formatMonthYear(start)})")
            }

            DateRangePreset.LAST_30_DAYS -> {
                val end = System.currentTimeMillis()
                calendar.timeInMillis = end
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                val start = calendar.timeInMillis
                DateRange(preset, start, end, "Last 30 Days")
            }

            DateRangePreset.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                val end = System.currentTimeMillis()
                DateRange(preset, start, end, "This Year (${Calendar.getInstance().get(Calendar.YEAR)})")
            }

            DateRangePreset.ALL_TIME -> {
                DateRange(preset, 0L, Long.MAX_VALUE, "All Time")
            }

            DateRangePreset.CUSTOM -> {
                val start = customStart ?: 0L
                val end = customEnd ?: System.currentTimeMillis()
                val label = "${formatShortDate(start)} - ${formatShortDate(end)}"
                DateRange(preset, start, end, label)
            }
        }
    }
}
