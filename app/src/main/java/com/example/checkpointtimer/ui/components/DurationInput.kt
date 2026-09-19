package com.example.checkpointtimer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Hours / minutes / seconds entry, shared by the total duration and by each checkpoint. */
@Composable
fun DurationInput(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onDurationChange: (hours: Int, minutes: Int, seconds: Int) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NumberField(
            value = hours,
            label = "Hours",
            isError = isError,
            onValueChange = { onDurationChange(it, minutes, seconds) },
        )
        NumberField(
            value = minutes,
            label = "Minutes",
            isError = isError,
            onValueChange = { onDurationChange(hours, it, seconds) },
        )
        NumberField(
            value = seconds,
            label = "Seconds",
            isError = isError,
            onValueChange = { onDurationChange(hours, minutes, it) },
        )
    }
}

@Composable
private fun RowScope.NumberField(
    value: Int,
    label: String,
    isError: Boolean,
    onValueChange: (Int) -> Unit,
) {
    // Held as text so the field can be emptied while typing; blank reads as zero.
    var text by rememberSaveable { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        if (text.isNotBlank() && text.toIntOrNull() != value) text = value.toString()
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(3)
            text = digits
            onValueChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(textAlign = TextAlign.Center),
        modifier = Modifier.weight(1f),
    )
}
