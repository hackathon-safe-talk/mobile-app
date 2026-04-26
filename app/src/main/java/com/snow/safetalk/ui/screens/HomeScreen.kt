package com.snow.safetalk.ui.screens

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.snow.safetalk.R
import com.snow.safetalk.analysis.AnalysisConstants
import com.snow.safetalk.history.AnalysisResultUi
import com.snow.safetalk.history.HistoryViewModel
import com.snow.safetalk.history.RiskLabel
import com.snow.safetalk.settings.SettingsDataStore
import com.snow.safetalk.sources.SourcesDataStore
import com.snow.safetalk.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    historyViewModel: HistoryViewModel,
    onCheckMessage: () -> Unit = {},
    onNavigateToResult: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToSecurity: (String) -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val historyState by historyViewModel.uiState.collectAsState()
    val latestResult by historyViewModel.latestResult.collectAsState()
    val totalHistoryCount by historyViewModel.totalHistoryCount.collectAsState()
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Data stores
    val settingsStore = remember { SettingsDataStore(context) }
    val sourcesStore = remember { SourcesDataStore(context) }
    
    // Status states for Himoya holati card
    val smsEnabled by sourcesStore.smsEnabled.collectAsState(initial = false)
    val telegramEnabled by sourcesStore.telegramEnabled.collectAsState(initial = false)
    
    val isDarkMode by settingsStore.isDarkMode.collectAsState(initial = true)

    var showBotDialog by remember { mutableStateOf(false) }

    if (showBotDialog) {
        val isInstalled = com.snow.safetalk.bot.TelegramAppDetector.hasSupportedTelegramClient(context)
        com.snow.safetalk.ui.components.SafeTalkBotDialog(
            isTelegramInstalled = isInstalled,
            onDismiss = { showBotDialog = false },
            onConfirm = {
                showBotDialog = false
                if (isInstalled) {
                    com.snow.safetalk.bot.BotLauncher.openSafeTalkBot(context)
                } else {
                    com.snow.safetalk.bot.BotLauncher.installTelegram(context)
                }
            }
        )
    }

    // ── Slogan animation ──────────────────────────────────────────────────
    var sloganVisible by remember { mutableStateOf(false) }
    val sloganAlpha by animateFloatAsState(
        targetValue = if (sloganVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "sloganAlpha"
    )
    val sloganOffsetY by animateDpAsState(
        targetValue = if (sloganVisible) 0.dp else 8.dp,
        animationSpec = tween(durationMillis = 900),
        label = "sloganOffset"
    )
    LaunchedEffect(Unit) { 
        sloganVisible = true 
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        Scaffold(
            containerColor  = Color.Transparent,
            snackbarHost    = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter            = painterResource(R.drawable.safetalk_shield),
                                contentDescription = "SafeTalk",
                                modifier           = Modifier.size(54.dp),
                                contentScale       = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text       = "SafeTalk",
                                color      = AppColors.TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 26.sp
                            )
                        }
                    },
                    actions = {
                        // 🤖 Bot bo'limi
                        IconButton(onClick = { showBotDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bot_assistant),
                                contentDescription = "SafeTalk Bot",
                                tint = AppColors.IconTint,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                        // 🔔 Notifications
                        IconButton(onClick = onNavigateToNotifications) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Bildirishnomalar",
                                tint = AppColors.IconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // 📊 Statistika
                        IconButton(onClick = onNavigateToStatistics) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = "Statistika",
                                tint = AppColors.IconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // ⓘ Ilova haqida
                        IconButton(onClick = onNavigateToAbout) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Ilova haqida",
                                tint = AppColors.IconTint,
                                modifier = Modifier.size(24.dp)
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
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // ── 1. Animated slogan ────────────────────────────────────────
                Text(
                    text      = "Xabarlaringiz uchun aqlli himoyachi",
                    color     = AppColors.TextSubtitle,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .alpha(sloganAlpha)
                        .offset(y = sloganOffsetY)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── 2. Himoya holati card ─────────────────────────────────────
                HimoyaHolatiCard(
                    smsEnabled = smsEnabled,
                    telegramEnabled = telegramEnabled,
                    isDarkMode = isDarkMode,
                    onNavigateFocus = onNavigateToSecurity
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── 3. Primary CTA button ─────────────────────────────────────
                GradientCtaButton(
                    isDarkMode = isDarkMode,
                    onClick = onCheckMessage
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── NEW: So'ngi tahlil (Latest Result) ───────────────────────
                LatestResultCard(
                    result = latestResult,
                    isDarkMode = isDarkMode,
                    onClick = { latestResult?.let { onNavigateToDetail(it.id) } }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ── 4. So'ngi tahlillar (Archive Gateway) ────────────────────
                HistoryEntryCard(
                    isDarkMode = isDarkMode,
                    historyCount = totalHistoryCount,
                    onClick = { onNavigateToResult() /* Actually we route to history now, mapping handled at NavHost */ }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Barcha tahlillar faqat qurilmangizda amalga oshiriladi.",
                    color     = AppColors.TextFooter,
                    fontSize  = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ── Himoya holati card — gradient security panel ─────────────────────────────

@Composable
private fun HimoyaHolatiCard(
    smsEnabled: Boolean,
    telegramEnabled: Boolean,
    isDarkMode: Boolean,
    onNavigateFocus: (String) -> Unit
) {
    val cardBgModifier = if (isDarkMode) {
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0D2137),
                    Color(0xFF0F2D4A),
                    Color(0xFF0A1E35)
                )
            )
        )
    } else {
        Modifier.background(AppColors.CardBg)
    }
    
    val borderColor = if (isDarkMode) Color(0xFF2D9CDB).copy(alpha = 0.30f) else AppColors.CardBorder
    val glowColor = Color(0xFF1E88E5).copy(alpha = 0.18f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // soft outer glow drawn behind the card
            .glowEffect(color = Color(0xFF1A6EBF), radius = if (isDarkMode) 28.dp else 12.dp, alpha = if (isDarkMode) 0.22f else 0.05f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .then(cardBgModifier)
                // border as a thin overlay
                .then(
                    Modifier.drawBehind {
                        drawRoundRect(
                            color        = borderColor,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                            style        = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                )
        ) {
            // Radial highlight on the right side
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // right-side radial glow overlay
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(160.dp)
                        .align(Alignment.CenterEnd)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    glowColor,
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter            = painterResource(R.drawable.safetalk_shield),
                            contentDescription = "Protection status",
                            modifier           = Modifier.size(45.dp),
                            contentScale       = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text       = "Himoya holati",
                            color      = if (isDarkMode) Color(0xFFCDD9E5) else AppColors.TextMain,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 18.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    StatusRow(
                        label = "SMS",
                        isOn = smsEnabled,
                        isDarkMode = isDarkMode,
                        onText = "Faol",
                        offText = "O'chiq",
                        onClick = { onNavigateFocus("sms") }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color    = if (isDarkMode) Color(0xFF2D9CDB).copy(alpha = 0.18f) else AppColors.Divider
                    )

                    StatusRow(
                        label = "Telegram",
                        isOn = telegramEnabled,
                        isDarkMode = isDarkMode,
                        onText = "Faol",
                        offText = "O'chiq",
                        onClick = { onNavigateFocus("telegram") }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    isOn: Boolean,
    isDarkMode: Boolean,
    onText: String,
    offText: String,
    onClick: () -> Unit
) {
    val statusColor = if (isOn) AppColors.Safe else AppColors.DangerDot

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment   = Alignment.CenterVertically
    ) {
        Text(
            text      = label,
            color     = if (isDarkMode) AppColors.TextCard else AppColors.TextSubtitle,
            fontSize  = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text       = if (isOn) onText else offText,
                color      = statusColor,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isDarkMode) Color(0xFF6B7A90) else AppColors.TextFooter,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── History sections ────────────────────────────────────────────────────────

@Composable
internal fun LatestResultCard(
    result: AnalysisResultUi?,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 92.dp)
            .clickable(enabled = result != null, onClick = onClick),
        shape  = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container (48dp circular background)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isDarkMode) Color(0xFF162B44) else Color(0xFFE3F2FD)),
                contentAlignment = Alignment.Center
            ) {
                if (result != null) {
                    val rColor = riskColor(result.label)
                    // Result state: keep the risk-level visual shield icon + subtle glow
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
                            painter = painterResource(riskIconRes(result.riskScore)),
                            contentDescription = null,
                            modifier = Modifier.size(45.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    // Empty/default state: consistent history style icon
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = AppColors.PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "So'ngi tahlil",
                        color = AppColors.TextMain,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                    
                    if (result?.showUnreadDot == true) {
                        Surface(
                            color = AppColors.PrimaryBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AppColors.PrimaryBlue))
                                Spacer(Modifier.width(6.dp))
                                Text("YANGI", color = AppColors.PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (result != null) {
                    val rColor = riskColor(result.label)
                    val rLabel = riskLabelText(result.riskScore)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = rColor, fontWeight = FontWeight.SemiBold)) {
                                append(rLabel)
                            }
                            append(" • ${result.riskScore}% — ${result.message.replace("\n", " ")}")
                        },
                        color = AppColors.TextSubtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.alpha(0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                } else {
                    Text(
                        text = "Hali tahlillar yo'q",
                        color = AppColors.TextSubtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.alpha(0.75f),
                        lineHeight = 18.sp
                    )
                }
            }

            if (result != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.timestampFormatted,
                    color = AppColors.TextFooter,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun HistoryEntryCard(
    isDarkMode: Boolean,
    historyCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 92.dp)
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container (48dp circular background)
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
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "So'ngi tahlillar",
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                if (historyCount > 0) {
                    Text(
                        text = "Jami: $historyCount ta tahlil",
                        color = AppColors.TextSubtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.alpha(0.75f),
                        lineHeight = 18.sp
                    )
                } else {
                    Text(
                        text = "Tahlillar arxivi va natijalar",
                        color = AppColors.TextSubtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.alpha(0.75f),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = null,
                tint               = AppColors.TextFooter,
                modifier           = Modifier.size(22.dp)
            )
        }
    }
}

// ── Risk drawable + color helpers ─────────────────────────────────────────────

internal fun riskIconRes(risk: Int): Int = when {
    risk >= AnalysisConstants.DANGEROUS_MIN -> R.drawable.shield_danger
    risk >= AnalysisConstants.SUSPICIOUS_MIN -> R.drawable.shield_warning
    else -> R.drawable.shield_safe
}

@Composable
internal fun riskColor(label: RiskLabel): Color = when (label) {
    RiskLabel.DANGEROUS  -> AppColors.DangerDot // Mapped to Vivid Red or equivalent Light constraint
    RiskLabel.SUSPICIOUS -> AppColors.Warning   // Bright Orange or adjusted
    RiskLabel.SAFE       -> AppColors.Safe      // Bright Green or Light override
}

internal fun riskLabelText(score: Int): String = when {
    score >= AnalysisConstants.DANGEROUS_MIN -> "Xavfli"
    score >= AnalysisConstants.SUSPICIOUS_MIN -> "Shubhali"
    else -> "Xavfsiz"
}

// ── Gradient CTA button ───────────────────────────────────────────────────────

@Composable
private fun GradientCtaButton(isDarkMode: Boolean, onClick: () -> Unit) {
    val btnGradient = Brush.horizontalGradient(
        colors = if (isDarkMode) listOf(
            Color(0xFF1052A0),   // deep blue (left)
            Color(0xFF1E88E5),   // bright blue (right)
        ) else listOf(
            Color(0xFF2F80ED),
            Color(0xFF3B8BFF)
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            // outer glow behind the button
            .glowEffect(color = if (isDarkMode) Color(0xFF1E88E5) else Color(0xFF2F80ED), radius = 22.dp, alpha = 0.30f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(btnGradient)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text       = "Xabarni tekshirish",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
            }
        }
    }
}

// ── Glow effect Modifier extension ───────────────────────────────────────────
// Draws a soft blurred glow shadow behind the composable using drawBehind +
// BlurMaskFilter via drawIntoCanvas.

private fun Modifier.glowEffect(
    color: Color,
    radius: Dp,
    alpha: Float = 0.25f
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
            radius.toPx(),
            android.graphics.BlurMaskFilter.Blur.NORMAL
        )
        frameworkPaint.color = color.copy(alpha = alpha).toArgb()
        canvas.drawRoundRect(
            left   = 0f,
            top    = 0f,
            right  = size.width,
            bottom = size.height,
            radiusX = 20.dp.toPx(),
            radiusY = 20.dp.toPx(),
            paint   = paint
        )
    }
}

