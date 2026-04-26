package com.snow.safetalk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.history.HistoryViewModel
import com.snow.safetalk.ui.theme.AppColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    historyViewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val historyState by historyViewModel.uiState.collectAsState()
    val stats by historyViewModel.globalStats.collectAsState()

    val totalMessages = stats.total
    val dangerMessages = stats.danger
    val suspiciousMessages = stats.suspicious
    val safeMessages = stats.safe

    Scaffold(
        containerColor = AppColors.BgSolid,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tahlillar statistikasi",
                        color = AppColors.TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Ortga",
                            tint = AppColors.IconTint
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // ── Section 1: Summary Cards ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Barcha xabarlar",
                    value = totalMessages.toString(),
                    color = AppColors.PrimaryBlue
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Xavfsiz",
                    value = safeMessages.toString(),
                    color = AppColors.Safe
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Shubhali",
                    value = suspiciousMessages.toString(),
                    color = AppColors.Warning
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Xavfli",
                    value = dangerMessages.toString(),
                    color = AppColors.DangerDot
                )
            }

            // ── Section 2: Risk Distribution ──
            if (totalMessages > 0) {
                val dangerPct = ((dangerMessages.toFloat() / totalMessages) * 100).roundToInt()
                val suspiciousPct = ((suspiciousMessages.toFloat() / totalMessages) * 100).roundToInt()
                val safePct = 100 - dangerPct - suspiciousPct

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Xavf taqsimoti",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            if (safePct > 0) Box(modifier = Modifier.weight(safePct.toFloat()).fillMaxHeight().background(AppColors.Safe))
                            if (suspiciousPct > 0) Box(modifier = Modifier.weight(suspiciousPct.toFloat()).fillMaxHeight().background(AppColors.Warning))
                            if (dangerPct > 0) Box(modifier = Modifier.weight(dangerPct.toFloat()).fillMaxHeight().background(AppColors.DangerDot))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Legends
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LegendItem(color = AppColors.DangerDot, label = "Xavfli: $dangerPct%")
                            LegendItem(color = AppColors.Warning, label = "Shubhali: $suspiciousPct%")
                            LegendItem(color = AppColors.Safe, label = "Xavfsiz: $safePct%")
                        }
                    }
                }
            }

            // ── Section 3: System Insights ──
            if (totalMessages > 0) {
                Text(
                    text = "Xulosalar",
                    color = AppColors.TextSubtitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val dangerPct = ((dangerMessages.toFloat() / totalMessages) * 100).roundToInt()
                    
                    if (dangerPct > 0) {
                        InsightItem("Aniqlangan xabarlarning $dangerPct% qismi xavfli bo'lishi mumkin.")
                    }
                    
                    if (dangerMessages > suspiciousMessages && dangerMessages > safeMessages) {
                        InsightItem("Foydalanuvchiga zararli havolalardan ehtiyot bo'lish tavsiya etiladi. Tahlillar ko'proq xavfli holatlarni aniqlamoqda.")
                    } else if (safeMessages > (dangerMessages + suspiciousMessages)) {
                        InsightItem("Ko'pchilik tahlillar xavfsiz xabarlarni ko'rsatmoqda. Tizim odatiy muloqotni himoya qilmoqda.")
                    } else if (suspiciousMessages > 0) {
                        InsightItem("Xabarlar orasida shubhali belgilar ko'rsatayotganlari yetarlicha mavjud. Ularni ochishdan oldin ikki marta tekshiring.")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, title: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = AppColors.TextSubtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = color,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = AppColors.TextMain,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InsightItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.CardBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.PrimaryBlue)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = AppColors.TextCard,
            fontSize = 14.sp
        )
    }
}
