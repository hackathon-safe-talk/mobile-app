package com.snow.safetalk.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snow.safetalk.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class HistoryFilter(
    val riskLabel: RiskLabel? = null,
    val sinceTimestamp: Long = 0L,
    val timeLabel: String = "Barcha vaqt"
)

data class HistoryArchiveState(
    val items: List<AnalysisResultUi> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    val hasPrevPage: Boolean = false,
    val filter: HistoryFilter = HistoryFilter(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val totalItemsCount: Int = 0
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = HistoryRepository(HistoryDatabase.getDatabase(app))
    private val settingsDataStore = SettingsDataStore(app)

    private val _uiState = MutableStateFlow(HistoryArchiveState())
    val uiState: StateFlow<HistoryArchiveState> = _uiState.asStateFlow()

    private val _selected = MutableStateFlow<AnalysisResultUi?>(null)
    val selected: StateFlow<AnalysisResultUi?> = _selected.asStateFlow()

    val latestResult: StateFlow<AnalysisResultUi?> = repository.getLatestResultFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val totalHistoryCount: StateFlow<Int> = repository.getHistoryCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val globalStats: StateFlow<GlobalStats> = repository.getGlobalStatsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GlobalStats()
        )

    init {
        loadPage(0)
        // Automatically refresh page 0 when new items arrive from ANY source (SMS/TG/Manual)
        viewModelScope.launch {
            repository.getLatestResultFlow().collect { _ ->
                if (_uiState.value.currentPage == 0 && _uiState.value.filter.sinceTimestamp == 0L) {
                    loadPage(0)
                }
            }
        }
    }

    fun loadPage(page: Int = _uiState.value.currentPage) {
        viewModelScope.launch {
            val filter = _uiState.value.filter
            val retention = settingsDataStore.historyRetentionDays.firstOrNull() ?: 90

            val (items, totalItems) = repository.getHistoryPaginated(
                riskLabel = filter.riskLabel,
                sinceTimestamp = filter.sinceTimestamp,
                page = page,
                retentionDays = retention
            )

            val totalPages = if (totalItems == 0) 1 else kotlin.math.ceil(totalItems.toDouble() / 10.0).toInt()
            
            // Clamp the page safely if a filter/deletion pushed us out of bounds
            val safePage = if (page >= totalPages && totalPages > 0) totalPages - 1 else page

            // If we clamped, reload using the safe page string
            if (safePage != page) {
                loadPage(safePage)
                return@launch
            }

            _uiState.update { 
                it.copy(
                    items = items,
                    currentPage = safePage,
                    totalPages = totalPages,
                    hasNextPage = safePage < totalPages - 1,
                    hasPrevPage = safePage > 0
                )
            }
        }
    }

    /** Add (or promote) a result. Auto-cleans retention window */
    fun addResult(result: AnalysisResultUi) {
        viewModelScope.launch {
            val retention = settingsDataStore.historyRetentionDays.firstOrNull() ?: 90
            repository.addResult(result, retentionDays = retention)
            
            // If we are observing page 0 and no filters are active, hot-reload 
            if (_uiState.value.currentPage == 0 && _uiState.value.filter.sinceTimestamp == 0L) {
                loadPage(0)
            }
        }
    }

    fun applyFilters(riskLabel: RiskLabel?, timeLabel: String, sinceTimestamp: Long) {
        _uiState.update { 
            it.copy(
                filter = HistoryFilter(riskLabel, sinceTimestamp, timeLabel),
                currentPage = 0, // Reset to page 0 whenever filters change
                isSelectionMode = false,
                selectedIds = emptySet()
            ) 
        }
        loadPage(0)
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
            loadPage()
        }
    }

    fun clearAllHistoryFiltered(visibleIds: List<String>) {
        if (visibleIds.isEmpty()) return
        viewModelScope.launch {
            repository.softDelete(visibleIds)
            loadPage()
        }
    }

    /** Mark an item as read (removes unread blue dot). */
    fun markRead(id: String) {
        viewModelScope.launch {
            repository.markRead(id)
            // Hot reload local item visually to avoid full DB fetch
            _uiState.update { state ->
                state.copy(items = state.items.map { if (it.id == id) it.copy(isRead = true) else it })
            }
        }
    }

    /** Find a record by ID for deep-links and history clicks */
    suspend fun getById(id: String): AnalysisResultUi? {
        return repository.getById(id)
    }

    fun selectResult(result: AnalysisResultUi) {
        _selected.value = result
    }

    fun clearSelectedResult() {
        _selected.value = null
    }
}
