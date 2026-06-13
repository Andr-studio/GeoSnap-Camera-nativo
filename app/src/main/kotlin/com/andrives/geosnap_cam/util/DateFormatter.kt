package com.andrives.geosnap_cam.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date and time formatting utilities for the watermark.
 */
object DateFormatter {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun formatDate(date: Date = Date(), timezone: String = ""): String {
        if (timezone.isNotEmpty()) {
            try {
                dateFormat.timeZone = TimeZone.getTimeZone(timezone)
            } catch (_: Exception) { }
        }
        return dateFormat.format(date).uppercase()
    }

    fun formatTime(date: Date = Date(), timezone: String = ""): String {
        if (timezone.isNotEmpty()) {
            try {
                timeFormat.timeZone = TimeZone.getTimeZone(timezone)
            } catch (_: Exception) { }
        }
        return timeFormat.format(date)
    }

    fun formatFull(date: Date = Date(), timezone: String = ""): String {
        if (timezone.isNotEmpty()) {
            try {
                fullFormat.timeZone = TimeZone.getTimeZone(timezone)
            } catch (_: Exception) { }
        }
        return fullFormat.format(date)
    }
}
