package com.example.checkpointtimer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.checkpointtimer.timer.TimerService
import com.example.checkpointtimer.timer.TimerState
import com.example.checkpointtimer.timer.TimerStateHolder
import kotlinx.coroutines.flow.StateFlow

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    val state: StateFlow<TimerState> = TimerStateHolder.state

    fun start(templateId: Long) {
        TimerService.start(getApplication(), templateId)
    }

    fun pause() {
        TimerService.pause(getApplication())
    }

    fun resume() {
        TimerService.resume(getApplication())
    }

    fun reset() {
        TimerService.reset(getApplication())
    }

    fun stop() {
        TimerService.stop(getApplication())
    }

    /** Called once the UI has shown the alert, so it is not replayed on the next recomposition. */
    fun consumeAlert() {
        TimerStateHolder.clearAlert()
    }
}
