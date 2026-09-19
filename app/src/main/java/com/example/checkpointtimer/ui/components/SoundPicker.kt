package com.example.checkpointtimer.ui.components

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import com.example.checkpointtimer.data.SoundChoice

fun SoundChoice.displayName(): String = when (this) {
    SoundChoice.Beep -> "Beep"
    SoundChoice.Alarm -> "Default alarm"
    SoundChoice.Notification -> "Default notification"
    SoundChoice.Silent -> "Silent"
    is SoundChoice.Ringtone -> "Chosen ringtone"
}

/**
 * Dropdown of the built-in sound options, plus an entry that opens the system ringtone picker
 * for anything else. The result is stored as a URI string inside [SoundChoice.Ringtone].
 */
@Composable
fun SoundPicker(
    label: String,
    sound: SoundChoice,
    onSoundChange: (SoundChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        // A null URI means the user chose "Silent".
        onSoundChange(picked?.let { SoundChoice.Ringtone(it.toString()) } ?: SoundChoice.Silent)
    }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${sound.displayName()}")
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BUILT_IN_SOUNDS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName()) },
                    onClick = {
                        expanded = false
                        onSoundChange(option)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Choose ringtone…") },
                onClick = {
                    expanded = false
                    ringtoneLauncher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose $label")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            (sound as? SoundChoice.Ringtone)?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it.uri))
                            }
                        },
                    )
                },
            )
        }
    }
}

private val BUILT_IN_SOUNDS = listOf(
    SoundChoice.Beep,
    SoundChoice.Alarm,
    SoundChoice.Notification,
    SoundChoice.Silent,
)
