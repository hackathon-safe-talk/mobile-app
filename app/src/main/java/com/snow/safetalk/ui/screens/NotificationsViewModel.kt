package com.snow.safetalk.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snow.safetalk.history.*
import com.snow.safetalk.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsState(
    val items: List<AnalysisResultUi> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

class NotificationsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = HistoryRepository(HistoryDatabase.getDatabase(app))
    private val settingsDataStore = SettingsDataStore(app)

    private val _uiState = MutableStateFlow(NotificationsState())
    val uiState: StateFlow<NotificationsState> = _uiState.asStateFlow()

    init {
        loadNotifications()
        // Watch for changes in history to refresh notifications
        viewModelScope.launch {
            repository.getLatestResultFlow().collect {
                loadNotifications()
            }
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val retention = settingsDataStore.historyRetentionDays.firstOrNull() ?: 90
            
            // Notifications are items where source is not MANUAL and not deleted
            // We reuse getHistoryPaginated or similar, but for notifications we might want all recent ones
            // For now, let's fetch a large page (e.g. 50) of non-manual items
            val (allItems, _) = repository.getHistoryPaginated(
                riskLabel = null,
                sinceTimestamp = 0L,
                page = 0,
                pageSize = 50,
                retentionDays = retention
            )
            
            val notificationItems = allItems.filter { it.source != MessageSource.MANUAL }
            
            _uiState.update { 
                it.copy(
                    items = notificationItems,
                    isLoading = false
                ) 
            }
        }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedIds.contains(id)) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun toggleSelectAllVisible(visibleIds: List<String>) {
        _uiState.update { state ->
            val allVisibleSelected = visibleIds.all { state.selectedIds.contains(it) }
            val newSelection = if (allVisibleSelected) {
                state.selectedIds - visibleIds.toSet()
            } else {
                state.selectedIds + visibleIds.toSet()
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun enterSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = true) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun deleteSelected() {
        val idsToDelete = _uiState.value.selectedIds.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            repository.softDelete(idsToDelete)
            clearSelection()
            loadNotifications()
        }
    }

    fun clearAllNotificationsFiltered(visibleIds: List<String>) {
        if (visibleIds.isEmpty()) return
        viewModelScope.launch {
            repository.softDelete(visibleIds)
            loadNotifications()
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            repository.markRead(id)
            _uiState.update { state ->
                state.copy(items = state.items.map { if (it.id == id) it.copy(isRead = true) else it })
            }
        }
    }

    suspend fun getById(id: String): AnalysisResultUi? {
        return repository.getById(id)
    }
}
