package com.snow.safetalk.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.R
import com.snow.safetalk.history.AnalysisResultUi
import com.snow.safetalk.history.RiskLabel
import com.snow.safetalk.ui.theme.AppColors

// ── Risk state helper ─────────────────────────────────────────────────────────

private data class RiskState(
    val color: Color,
    val iconRes: Int,
    val headline: String,
    val subtitle: String
)

@Composable
private fun riskState(label: RiskLabel): RiskState = when (label) {
    RiskLabel.DANGEROUS -> RiskState(
        color    = AppColors.DangerDot,
        iconRes  = R.drawable.shield_danger,
        headline = "Xavfli xabar aniqlandi",
        subtitle = "Bu xabar xavfli — sizdan maxfiy ma'lumot yoki tezkor harakat so'ralmoqda."
    )
    RiskLabel.SUSPICIOUS -> RiskState(
        color    = AppColors.Warning,
        iconRes  = R.drawable.shield_warning,
        headline = "Shubhali xabar aniqlandi",
        subtitle = "Xabar to'liq ishonchli emas. Unda ehtiyot bo'lishni talab qiladigan belgilar mavjud."
    )
    RiskLabel.SAFE -> RiskState(
        color    = AppColors.Safe,
        iconRes  = R.drawable.shield_safe,
        headline = "Xavfsiz xabar",
        subtitle = "Xabarda firibgarlikka xos belgilar aniqlanmadi."
    )
}

// ── ResultScreen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    result: AnalysisResultUi?,
    onBack: () -> Unit = {},
    onAnalyzeAnother: () -> Unit = {},
    onMarkRead: (String) -> Unit = {}
) {
    if (result == null) return

    // Mark as read when this screen is opened
    LaunchedEffect(result.id) {
        onMarkRead(result.id)
    }

    val state = riskState(result.label)

    var showMessageDetails by rememberSaveable { mutableStateOf(false) }
    var showMessage by rememberSaveable { mutableStateOf(false) }
    var showReasons by rememberSaveable { mutableStateOf(false) }
    var showRecommendations by rememberSaveable { mutableStateOf(false) }
    var showTipsSheet by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Tekshiruv natijasi",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },

                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Orqaga",
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
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Big circular status icon ──────────────────────────────────
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_animation"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(scale)
                ) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(state.color.copy(alpha = 0.06f))
                    )
                    // Inner circle
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(state.color.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${result.riskScore}%",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = state.color
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Headline ──────────────────────────────────────────────────
                Text(
                    state.headline,
                    color = state.color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Subtitle ──────────────────────────────────────────────────
                Text(
                    state.subtitle,
                    color = AppColors.TextSubtitle,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ── Risk score ────────────────────────────────────────────────
                Text(
                    "Xavf darajasi: ${result.riskScore} / 100",
                    color = state.color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── Confidence pill ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(AppColors.CardBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Aniqlik darajasi: ${result.confidence}%",
                        color = AppColors.TextCard,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Accordion 1: Message Details (Metadata) ───────────────────
                AccordionSection(
                    title    = "Xabar tafsilotlari",
                    expanded = showMessageDetails,
                    onToggle = { showMessageDetails = !showMessageDetails }
                ) {
                    DarkInnerCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetadataRow("Jo'natuvchi:", result.senderName)
                            MetadataRow("Manba:", result.sourceApp)
                            MetadataRow("Qabul qilingan vaqt:", formatTimestamp(result.receivedTimestamp))
                            MetadataRow("Aniqlangan fayl:", result.detectedFileName)
                            MetadataRow("Fayl turi:", result.detectedFileType)
                            MetadataRow("Aniqlangan havola:", result.detectedUrl)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Accordion 2: Analyzed message ─────────────────────────────
                AccordionSection(
                    title    = "Tahlil qilingan xabar",
                    expanded = showMessage,
                    onToggle = { showMessage = !showMessage }
                ) {
                    DarkInnerCard {
                        val displayMsg = result.message
                            .replace(Regex("\\[Telegram:.*?\\]\\n?"), "")
                            .lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString("\n")

                        val finalDisplay = if (displayMsg.isNotBlank()) {
                            displayMsg
                        } else if (!result.detectedFileName.isNullOrBlank()) {
                            "Fayl kiritilgan: ${result.detectedFileName}"
                        } else {
                            "(xabar matn kiritilmagan)"
                        }

                        Text(
                            finalDisplay,
                            color = AppColors.TextCard,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Accordion 2: Reasons ──────────────────────────────────────
                AccordionSection(
                    title    = when (result.label) {
                        RiskLabel.SAFE       -> "Nima uchun bu xabar xavfsiz?"
                        RiskLabel.SUSPICIOUS -> "Nima uchun bu xabar shubhali?"
                        RiskLabel.DANGEROUS  -> "Nima uchun bu xabar xavfli?"
                    },
                    expanded = showReasons,
                    onToggle = { showReasons = !showReasons }
                ) {
                    DarkInnerCard {
                        val bullets = result.reasons.ifEmpty {
                            listOf("Xavf belgisi topilmadi, lekin ehtiyot bo'ling.")
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            bullets.forEach { reason ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(state.color)
                                    )
                                    Text(
                                        reason,
                                        color = AppColors.TextCard,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Accordion 4: Recommendations ──────────────────────────────
                AccordionSection(
                    title    = "Siz uchun tavsiya",
                    expanded = showRecommendations,
                    onToggle = { showRecommendations = !showRecommendations }
                ) {
                    DarkInnerCard {
                        val bullets = result.recommendations.ifEmpty {
                            listOf("Shubhali havola va fayllarga ehtiyot bo‘ling.")
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            bullets.forEach { advice ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(state.color)
                                    )
                                    Text(
                                        advice,
                                        color = AppColors.TextCard,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── "Xavfsizlik maslahatlari" outlined button ─────────────────
                OutlinedButton(
                    onClick = { showTipsSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AppColors.CardBg,
                        contentColor = AppColors.PrimaryBlue
                    ),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Xavfsizlik maslahatlari", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Primary CTA ───────────────────────────────────────────────
                Button(
                    onClick = onAnalyzeAnother,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.PrimaryBlueDark,
                        contentColor = AppColors.TextMain
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Boshqa xabarni tekshirish", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Tips bottom sheet ──────────────────────────────────────────
        if (showTipsSheet) {
            TipsBottomSheet(
                label = result.label,
                onDismiss = { showTipsSheet = false }
            )
        }
    }
}

// ── Tips bottom sheet composable ──────────────────────────────────────────────

private fun tipsForState(label: RiskLabel): List<String> = when (label) {
    RiskLabel.DANGEROUS -> listOf(
        "Bunday xabarlarga JAVOB BERMANG.",
        "Hech qanday havola yoki fayl ochmang.",
        "Xabar yuboruvchini bloklang.",
        "Shaxsiy ma'lumotlaringizni hech kimga bermang.",
        "Kerak bo'lsa, rasmiy tashkilotga murojaat qiling."
    )
    RiskLabel.SUSPICIOUS -> listOf(
        "Xabardagi havolalarni ochmang.",
        "Yuboruvchining rasmiy hisob ekanligini tekshiring.",
        "Shoshilmasdan o'ylab ko'ring — manipulyatsiya bo'lishi mumkin.",
        "Shubhalansangiz, tashkilotga to'g'ridan-to'g'ri murojaat qiling."
    )
    RiskLabel.SAFE -> listOf(
        "Xabar xavfsiz ko'rinadi, lekin ehtiyot bo'ling.",
        "Noma'lum raqamlardan kelgan xabarlarga e'tiborli bo'ling.",
        "Shaxsiy ma'lumotlarni faqat rasmiy kanallarda ulashing."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipsBottomSheet(
    label: RiskLabel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tips = tipsForState(label)
    val stateColor = when (label) {
        RiskLabel.DANGEROUS  -> AppColors.DangerDot
        RiskLabel.SUSPICIOUS -> AppColors.Warning
        RiskLabel.SAFE       -> AppColors.Safe
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.CardBg,
        contentColor = AppColors.TextMain
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Xavfsizlik maslahatlari",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = AppColors.TextMain
            )
            Spacer(modifier = Modifier.height(8.dp))

            tips.forEach { tip ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(stateColor)
                    )
                    Text(tip, color = AppColors.TextCard, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Accordion composable ──────────────────────────────────────────────────────

@Composable
private fun AccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(250),
        label = "arrowRotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column(modifier = Modifier.animateContentSize(tween(280))) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = AppColors.PrimaryBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotation)
                    )
                    Text(
                        title,
                        color = AppColors.TextMain,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.TextSubtitle,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expandable content
            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = AppColors.CardBorder
                )
                Box(modifier = Modifier.padding(16.dp)) {
                    content()
                }
            }
        }
    }
}

// ── Metadata Components ───────────────────────────────────────────────────────

@Composable
private fun MetadataRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = AppColors.TextSubtitle,
            fontSize = 13.sp,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value.takeIf { !it.isNullOrBlank() } ?: "aniqlanmadi",
            color = AppColors.TextCard,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return "aniqlanmadi"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return "%02d.%02d.%04d, %02d:%02d".format(
        cal.get(java.util.Calendar.DAY_OF_MONTH),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
}

// ── Dark inner card ───────────────────────────────────────────────────────────

@Composable
private fun DarkInnerCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.CardBorder),
        content = { Column(modifier = Modifier.padding(14.dp), content = content) }
    )
}
