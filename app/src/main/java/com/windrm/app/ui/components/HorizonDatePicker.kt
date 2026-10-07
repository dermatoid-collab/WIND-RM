package com.windrm.app.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.windrm.app.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Calendar for picking a ride day: only today through [horizonDays] ahead can be chosen, and weeks
 * start on Monday whatever the device locale says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorizonDatePickerDialog(
    initialDate: LocalDate,
    horizonDays: Long,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val maxDate = remember(today, horizonDays) { today.plusDays(horizonDays) }
    val datePickerState = remember {
        DatePickerState(
            // The picker takes its first weekday from the locale; weeks must start on Monday.
            locale = mondayFirstLocale(),
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            // Beyond this horizon Open-Meteo's high-resolution regional models fall back to lower
            // detail, and reliability drops accordingly -- see WeatherRepository's forecastRoute.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val candidate = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !candidate.isBefore(today) && !candidate.isAfter(maxDate)
                }
            },
        )
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = datePickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                if (picked != null) onConfirm(picked) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

/** The device locale if its weeks already start on Monday, otherwise the same language with a Monday-first region. */
private fun mondayFirstLocale(): Locale {
    val default = Locale.getDefault()
    if (WeekFields.of(default).firstDayOfWeek == DayOfWeek.MONDAY) return default
    // Week start is regional data, so borrowing a Monday-first region keeps the language's month and day names.
    return Locale(default.language, "GB")
}
