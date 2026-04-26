package com.snow.safetalk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.R
import com.snow.safetalk.analysis.AnalysisConstants
import com.snow.safetalk.history.AnalysisResultUi
import com.snow.safetalk.history.HistoryViewModel
import com.snow.safetalk.history.MessageSource
import com.snow.safetalk.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    historyViewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenResult: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val filteredHistory = state.items
    var showClearAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppColors.BgSolid,
        topBar = {
            TopAppBar(
                title = {
                    if (!state.isSelectionMode) {
                        Text(
                            text = "Bildirishnomalar",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    if (state.isSelectionMode) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Bekor qilish", color = AppColors.PrimaryBlue, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Ortga",
                                tint = AppColors.IconTint
                            )
                        }
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        // Bekor qilish | Select All | Delete Selected
                        IconButton(onClick = { viewModel.toggleSelectAllVisible(filteredHistory.map { it.id }) }) {
                            val allVisibleSelected = filteredHistory.isNotEmpty() && filteredHistory.all { state.selectedIds.contains(it.id) }
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Barchasini tanlash",
                                tint = if (allVisibleSelected) AppColors.PrimaryBlue else AppColors.IconTint
                            )
                        }
                        
                        if (state.selectedIds.isNotEmpty()) {
                            TextButton(onClick = { viewModel.deleteSelected() }) {
                                Text(
                                    text = "O'chirish",
                                    color = AppColors.DangerDot,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else if (filteredHistory.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.enterSelectionMode() }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "O'chirish",
                                    tint = AppColors.IconTint
                                )
                            }
                            IconButton(onClick = { showClearAllConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Tozalash",
                                    tint = AppColors.DangerDot
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Hozircha avtomatik aniqlangan xabarlar yo'q",
                    color = AppColors.TextSubtitle,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                items(filteredHistory, key = { it.id }) { item ->
                    val isSelected = state.selectedIds.contains(item.id)
                    NotificationItem(
                        item = item,
                        isSelected = isSelected,
                        isSelectionMode = state.isSelectionMode,
                        onClick = {
                            if (state.isSelectionMode) {
                                viewModel.toggleSelection(item.id)
                            } else {
                                historyViewModel.selectResult(item)
                                viewModel.markRead(item.id)
                                onOpenResult()
                            }
                        },
                        onLongClick = {
                            viewModel.toggleSelection(item.id)
                        }
                    )
                }
            }
        }
    }

    // ── Confirmation Dialog ──
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Bildirishnomalar tozalash", color = AppColors.TextMain) },
            text = { Text("Rostdan ham hozir ko'rinayotgan barcha bildirishnomalarni tozalamoqchimisiz?", color = AppColors.TextSubtitle) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllNotificationsFiltered(filteredHistory.map { it.id })
                        showClearAllConfirm = false
                    }
                ) {
                    Text("Tozalash", color = AppColors.DangerDot, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Bekor qilish", color = AppColors.TextMain)
                }
            },
            containerColor = AppColors.CardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationItem(
    item: AnalysisResultUi,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    // Determine visual properties based on global logic
    val riskIcon = riskIconRes(item.riskScore)

    val riskColor = when {
        item.riskScore >= AnalysisConstants.DANGEROUS_MIN -> AppColors.DangerDot
        item.riskScore >= AnalysisConstants.SUSPICIOUS_MIN -> AppColors.Warning
        else -> AppColors.Safe
    }

    val riskLabel = riskLabelText(item.riskScore)
    val displayTitle = "$riskLabel • ${item.riskScore}%"

    val bgColor = if (isSelected) AppColors.PrimaryBlue.copy(alpha = 0.15f) else AppColors.CardBg
    val borderColor = if (isSelected) AppColors.PrimaryBlue else AppColors.CardBorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Subtle radial glow behind the shield
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(riskColor.copy(alpha = 0.28f), Color.Transparent),
                                    center = center,
                                    radius = size.width / 2f
                                )
                            )
                        }
                )
                Image(
                    painter = painterResource(riskIcon),
                    contentDescription = "Risk rating",
                    modifier = Modifier.size(24.dp).scale(2f),
                    contentScale = ContentScale.Fit
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    color = riskColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.message,
                    color = AppColors.TextSubtitle,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AppColors.PrimaryBlue else AppColors.ToggleTrackOff),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = item.timestampFormatted,
                    color = AppColors.TextFooter,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
