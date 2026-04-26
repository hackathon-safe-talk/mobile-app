package com.snow.safetalk.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Global screen background wrapper.
 * Uses AppColors.BgSolid (#010409) as a flat solid color.
 */
@Composable
fun SafeTalkBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        content()
    }
}
