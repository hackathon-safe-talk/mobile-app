package com.snow.safetalk

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped ViewModel that owns the pending deep-link analysis ID.
 *
 * Using a [StateFlow] here instead of a Compose [androidx.compose.runtime.MutableState]
 * class field avoids:
 *  - Memory leaks (ViewModel survives config changes)
 *  - Double-init risk from multiple setContent() recompositions
 *  - The need to call recreate() when a new intent arrives
 *
 * Lifecycle: survives rotation, cleared when the Activity is truly finished.
 */
class MainViewModel : ViewModel() {

    private val _pendingAnalysisId = MutableStateFlow<String?>(null)

    /** Observed in [MainActivity] setContent {} to drive deep-link navigation. */
    val pendingAnalysisId: StateFlow<String?> = _pendingAnalysisId.asStateFlow()

    /**
     * Called from [MainActivity.onNewIntent] when a notification carries an analysis_id.
     * Pass null to clear the pending state after it has been consumed.
     */
    fun setPendingId(id: String?) {
        _pendingAnalysisId.value = id
    }
}
