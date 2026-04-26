package com.snow.safetalk.navigation

import androidx.compose.runtime.Composable

/** Thin entry-point kept for MainActivity compatibility. Delegates to [SafeTalkNavHost]. */
@Composable
fun SafeTalkNavigation(
    pendingAnalysisId: String? = null,
    onAnalysisConsumed: () -> Unit = {}
) = SafeTalkNavHost(
    pendingAnalysisId  = pendingAnalysisId,
    onAnalysisConsumed = onAnalysisConsumed
)
