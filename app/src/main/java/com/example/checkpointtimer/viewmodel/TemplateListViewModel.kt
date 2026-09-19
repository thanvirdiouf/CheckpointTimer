package com.example.checkpointtimer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkpointtimer.data.TemplateRepository
import com.example.checkpointtimer.data.TimerTemplate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateListUiState(
    val templates: List<TimerTemplate> = emptyList(),
    val isLoading: Boolean = true,
)

class TemplateListViewModel(private val repository: TemplateRepository) : ViewModel() {

    val uiState: StateFlow<TemplateListUiState> = repository.observeTemplates()
        .map { TemplateListUiState(templates = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TemplateListUiState(),
        )

    fun delete(templateId: Long) {
        viewModelScope.launch { repository.deleteTemplate(templateId) }
    }

    fun duplicate(templateId: Long) {
        viewModelScope.launch { repository.duplicateTemplate(templateId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
