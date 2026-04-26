package com.snow.safetalk.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.history.AnalysisResultUi
import com.snow.safetalk.history.HistoryViewModel
import com.snow.safetalk.history.RiskLabel
import com.snow.safetalk.ui.theme.AppColors
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToResult: (String) -> Unit,
    isDarkMode: Boolean
) {
    val state by viewModel.uiState.collectAsState()

    // Filter Dropdown States
    var showTimeFilter by remember { mutableStateOf(false) }
    var showRiskFilter by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // Reset selection mode when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelection()
        }
    }

    Scaffold(
        containerColor = AppColors.BgSolid,
        topBar = {
            Column(modifier = Modifier.background(AppColors.BgSolid).statusBarsPadding()) {
                // ── ROW 1: Navigation & Title ────────────────────────────────
                TopAppBar(
                    title = { 
                        if (!state.isSelectionMode) {
                            Text(
                                text = "So'ngi tahlillar",
                                color = AppColors.TextMain,
                                fontWeight = FontWeight.Bold, 
                                fontSize = 20.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                        }
                    },
                    navigationIcon = {
                        if (state.isSelectionMode) {
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text("Bekor qilish", color = AppColors.PrimaryBlue, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Orqaga", 
                                    tint = AppColors.IconTint
                                )
                            }
                        }
                    },
                    actions = {
                        if (state.isSelectionMode) {
                            // Bekor qilish | Select All | Delete Selected
                            IconButton(onClick = { viewModel.toggleSelectAllVisible(state.items.map { it.id }) }) {
                                val allVisibleSelected = state.items.isNotEmpty() && state.items.all { state.selectedIds.contains(it.id) }
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
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                // ── ROW 2: Filters & Actions (Only in Normal Mode) ───────────
                if (!state.isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // ── 1. Vaqt Filter ──
                            Box {
                                TextButton(
                                    onClick = { showTimeFilter = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = state.filter.timeLabel,
                                            color = if (state.filter.sinceTimestamp > 0) AppColors.PrimaryBlue else AppColors.TextSubtitle,
                                            fontSize = 14.sp
                                        )
                                        Icon(Icons.Default.ArrowDropDown, null, tint = AppColors.TextFooter, modifier = Modifier.size(18.dp))
                                    }
                                }
                                DropdownMenu(expanded = showTimeFilter, onDismissRequest = { showTimeFilter = false }) {
                                    val times = listOf(
                                        "Bugun" to getStartOfDay(),
                                        "Shu hafta" to getStartOfWeek(),
                                        "Shu oy" to getStartOfMonth(),
                                        "Barcha vaqt" to 0L
                                    )
                                    times.forEach { (label, ts) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = if (state.filter.sinceTimestamp == ts) AppColors.PrimaryBlue else AppColors.TextMain) },
                                            onClick = {
                                                viewModel.applyFilters(state.filter.riskLabel, label, ts)
                                                showTimeFilter = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // ── 2. Xavf Filter ──
                            Box {
                                TextButton(
                                    onClick = { showRiskFilter = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val (label, color) = when (state.filter.riskLabel) {
                                            RiskLabel.DANGEROUS -> "Xavfli" to AppColors.DangerDot
                                            RiskLabel.SUSPICIOUS -> "Shubhali" to AppColors.Warning
                                            RiskLabel.SAFE -> "Xavfsiz" to AppColors.Safe
                                            null -> "Barchasi" to AppColors.TextSubtitle
                                        }
                                        Text(label, color = color, fontSize = 14.sp)
                                        Icon(Icons.Default.ArrowDropDown, null, tint = AppColors.TextFooter, modifier = Modifier.size(18.dp))
                                    }
                                }
                                DropdownMenu(expanded = showRiskFilter, onDismissRequest = { showRiskFilter = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Barchasi", color = AppColors.TextMain) },
                                        onClick = { viewModel.applyFilters(null, state.filter.timeLabel, state.filter.sinceTimestamp); showRiskFilter = false }
                                    )
                                    RiskLabel.entries.forEach { risk ->
                                        val riskText = when(risk) {
                                            RiskLabel.SAFE -> "Xavfsiz"
                                            RiskLabel.SUSPICIOUS -> "Shubhali"
                                            RiskLabel.DANGEROUS -> "Xavfli"
                                        }
                                        DropdownMenuItem(
                                            text = { Text(riskText, color = riskColor(risk)) },
                                            onClick = { viewModel.applyFilters(risk, state.filter.timeLabel, state.filter.sinceTimestamp); showRiskFilter = false }
                                        )
                                    }
                                }
                            }
                        }

                        // ── 3. Actions ──
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.enterSelectionMode() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Tanlash", tint = AppColors.IconTint)
                            }
                            
                            if (state.items.isNotEmpty()) {
                                IconButton(onClick = { showClearAllConfirm = true }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Tozalash", tint = AppColors.DangerDot)
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!state.isSelectionMode && state.totalPages > 0) {
                Surface(
                    color = AppColors.CardBg,
                    border = BorderStroke(1.dp, AppColors.CardBorder),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.loadPage(state.currentPage - 1) }, 
                            enabled = state.hasPrevPage
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Oldingi", tint = if (state.hasPrevPage) AppColors.PrimaryBlue else AppColors.TextFooter)
                        }
                        
                        Text(
                            text = "Sahifa ${state.currentPage + 1} / ${state.totalPages}",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )

                        IconButton(
                            onClick = { viewModel.loadPage(state.currentPage + 1) }, 
                            enabled = state.hasNextPage
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Keyingi", tint = if (state.hasNextPage) AppColors.PrimaryBlue else AppColors.TextFooter)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            // Active Filters Banner
            if (state.filter.sinceTimestamp > 0 || state.filter.riskLabel != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.filter.sinceTimestamp > 0) {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.applyFilters(state.filter.riskLabel, "Barcha vaqt", 0L) },
                            label = { Text(state.filter.timeLabel) }
                        )
                    }
                    if (state.filter.riskLabel != null) {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.applyFilters(null, state.filter.timeLabel, state.filter.sinceTimestamp) },
                            label = { Text(state.filter.riskLabel!!.name) }
                        )
                    }
                }
            }

            if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Match Home screen archive identity visual token
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isDarkMode) Color(0xFF162B44) else Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = AppColors.PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tahlillar topilmadi",
                            color = AppColors.TextFooter,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        val isSelected = state.selectedIds.contains(item.id)
                        HistoryListItemSelectable(
                            item = item,
                            isDarkMode = isDarkMode,
                            isSelected = isSelected,
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleSelection(item.id)
                                } else {
                                    viewModel.markRead(item.id)
                                    onNavigateToResult(item.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(item.id)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ── Confirmation Dialog ──
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Tarixni tozalash", color = AppColors.TextMain) },
            text = { Text("Rostdan ham hozir filtrda ko'rinayotgan barcha tahlillarni tozalamoqchimisiz?", color = AppColors.TextSubtitle) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistoryFiltered(state.items.map { it.id })
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

// ── Time Helpers ─────────────────────────────────────────────────────────────

private fun getStartOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun getStartOfWeek(): Long {
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun getStartOfMonth(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// ── Selectable List Item ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryListItemSelectable(
    item: AnalysisResultUi,
    isDarkMode: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDanger = item.label == RiskLabel.DANGEROUS
    val highlightColor = if (!isDarkMode && isDanger) Color(0xFFFFF5F5) else AppColors.CardBg
    val bgColor = if (isSelected) AppColors.PrimaryBlue.copy(alpha = 0.15f) else highlightColor
    val borderColor = if (isSelected) AppColors.PrimaryBlue else AppColors.CardBorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Unread blue dot (Left side in normal mode)
            if (!isSelectionMode && item.showUnreadDot) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppColors.PrimaryBlue))
                Spacer(modifier = Modifier.width(12.dp))
            }

            val rColor = riskColor(item.label)
            Box(contentAlignment = Alignment.Center) {
                // Subtle radial glow behind the shield
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(rColor.copy(alpha = 0.28f), Color.Transparent),
                                    center = center,
                                    radius = size.width / 2f
                                )
                            )
                        }
                )
                Image(
                    painter = painterResource(riskIconRes(item.riskScore)),
                    contentDescription = "Risk Level",
                    modifier = Modifier.size(24.dp).scale(2f),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${riskLabelText(item.riskScore)} • ${item.riskScore}%",
                    color = riskColor(item.label),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.message.replace("\n", " "),
                    color = AppColors.TextSubtitle,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Side: Timestamp (Normal) OR Checkmark (Selection)
            if (isSelectionMode) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AppColors.PrimaryBlue else AppColors.ToggleTrackOff),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.timestampFormatted,
                    color = AppColors.TextFooter,
                    fontSize = 12.sp
                )
            }
        }
    }
}
