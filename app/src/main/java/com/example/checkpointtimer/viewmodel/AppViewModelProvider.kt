package com.example.checkpointtimer.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.checkpointtimer.CheckpointTimerApp

/** Wires ViewModels up to the [AppContainer] without pulling in a DI framework. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer { TemplateListViewModel(app().container.templateRepository) }
        initializer { TimerViewModel(app()) }
    }

    /** The editor is scoped to one template, so its factory carries the id it was opened with. */
    fun editorFactory(templateId: Long) = viewModelFactory {
        initializer {
            TemplateEditorViewModel(app().container.templateRepository, templateId)
        }
    }

    private fun CreationExtras.app(): CheckpointTimerApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CheckpointTimerApp
}
