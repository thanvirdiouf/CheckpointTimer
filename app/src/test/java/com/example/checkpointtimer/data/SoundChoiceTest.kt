package com.example.checkpointtimer.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The storage string is the only thing keeping a chosen sound across restarts. */
class SoundChoiceTest {

    @Test
    fun `built-in choices survive a storage round trip`() {
        listOf(
            SoundChoice.Beep,
            SoundChoice.Alarm,
            SoundChoice.Notification,
            SoundChoice.Silent,
        ).forEach { choice ->
            assertEquals(choice, SoundChoice.fromStorageString(choice.toStorageString()))
        }
    }

    @Test
    fun `a chosen ringtone keeps its uri`() {
        val choice = SoundChoice.Ringtone("content://media/internal/audio/media/42")

        val restored = SoundChoice.fromStorageString(choice.toStorageString())

        assertEquals(choice, restored)
    }

    @Test
    fun `a uri containing the separator is not truncated`() {
        val uri = "content://media/internal/audio/media/42?title=beep:alarm"
        val restored = SoundChoice.fromStorageString(SoundChoice.Ringtone(uri).toStorageString())

        assertEquals(SoundChoice.Ringtone(uri), restored)
    }

    @Test
    fun `missing and unrecognised values fall back to the default beep`() {
        assertEquals(SoundChoice.Beep, SoundChoice.fromStorageString(null))
        assertEquals(SoundChoice.Beep, SoundChoice.fromStorageString(""))
        assertEquals(SoundChoice.Beep, SoundChoice.fromStorageString("something-else"))
    }
}
