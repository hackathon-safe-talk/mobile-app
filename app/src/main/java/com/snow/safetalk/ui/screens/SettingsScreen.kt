package com.snow.safetalk.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Star
import com.snow.safetalk.R
import com.snow.safetalk.analysis.AnalysisConstants
import com.snow.safetalk.settings.SettingsDataStore
import com.snow.safetalk.ui.components.SettingsToggleRow
import com.snow.safetalk.ui.theme.AppColors
import com.snow.safetalk.core.ToggleSoundPlayer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val store   = remember { SettingsDataStore(context) }

    val isDarkMode       by store.isDarkMode.collectAsState(initial = true)
    val soundEnabled     by store.soundEnabled.collectAsState(initial = true)
    val strongHighlight  by store.strongHighlight.collectAsState(initial = true)
    val smsNotifications by store.smsNotifications.collectAsState(initial = false)
    val smsSourceEnabled by store.smsSourceEnabled.collectAsState(initial = false)
    val notifThreshold   by store.notificationThreshold.collectAsState(initial = 40)
    val showExplanation  by store.showExplanation.collectAsState(initial = true)
    val historyRetention by store.historyRetentionDays.collectAsState(initial = 90)

    val snackbarHostState = remember { SnackbarHostState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost   = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Sozlamalar", color = AppColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 20.sp) },

                    // actions removed per requirement
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // ═══════════ OBUNA ═══════════════════════════════════════
                SettingsCard {
                    SettingsRow(
                        title    = "OBUNA",
                        leadIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        subtitle = "Premium imkoniyatlarni ko'rish\nMessenger tahlili va qo'shimcha himoya",
                        trailing = {
                            Icon(
                                imageVector        = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint               = AppColors.TextFooter,
                                modifier           = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.clickable { onNavigateToSubscription() }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ═══════════ KO'RINISH ═══════════════════════════════════
                SectionTitle("KO'RINISH")

                // ── Mavzu ─────────────────────────────────────────────────
                SettingsCard {
                    SettingsToggleRow(
                        title = "Mavzu",
                        subtitle = if (isDarkMode) "DARK — xavf ranglari aniqroq" else "LIGHT — kunduzgi foydalanish uchun",
                        checked = isDarkMode,
                        onToggle = { newDark ->
                            ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                            scope.launch { store.setDarkMode(newDark) }
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                FooterHint("Ilova standart holatda DARK rejimda ishlaydi")

                Spacer(Modifier.height(16.dp))

                // ── Ovoz ──────────────────────────────────────────────────
                SettingsCard {
                    SettingsToggleRow(
                        title = "Ovoz",
                        subtitle = if (soundEnabled) "UI ovozlar yoqilgan" else "UI ovozlar o'chirilgan",
                        checked = soundEnabled,
                        onToggle = { new ->
                            scope.launch { store.setSoundEnabled(new) }
                            ToggleSoundPlayer.playToggleSound(context, new)
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                FooterHint("Toggle ovozlarini yoqish yoki o'chirish")

                Spacer(Modifier.height(24.dp))

                // ═══════════ TIL ═════════════════════════════════════════
                SectionTitle("TIL")
                SettingsCard {
                    SettingsRow(
                        title    = "Joriy til: O'zbek",
                        subtitle = "O'zbek · Русский · English",
                        trailing = {
                            Icon(
                                imageVector        = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint               = AppColors.TextFooter,
                                modifier           = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.clickable { /* Language picker future */ }
                    )
                }

                // ═══════════ TARIX ═════════════════════════════════════════
                SectionTitle("TARIX")
                SettingsCard {
                    var expanded by remember { mutableStateOf(false) }
                    val options = listOf(30 to "30 kun", 90 to "90 kun", 180 to "6 oy", 365 to "1 yil")
                    val currentOptionLabel = options.find { it.first == historyRetention }?.second ?: "90 kun"

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp).fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentOptionLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Asosiy tahlillar saqlanish muddati") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            options.forEach { (days, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                                        scope.launch { store.setHistoryRetentionDays(days) }
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                FooterHint("Eski tahlillar avtomatik o'chiriladi")

                Spacer(Modifier.height(24.dp))

                // ═══════════ XAVFSIZLIK RANGLARI ═════════════════════════
                SectionTitle("XAVFSIZLIK RANGLARI")
                SettingsCard {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                        RiskLegendRow(AppColors.DangerDot, "Xavfli",   "Ehtiyot bo'ling — firibgarlik aniqlandi")
                        Spacer(Modifier.height(18.dp))
                        RiskLegendRow(AppColors.Warning,   "Shubhali", "Diqqat — xabar ishonchsiz ko'rinadi")
                        Spacer(Modifier.height(18.dp))
                        RiskLegendRow(AppColors.Safe,      "Xavfsiz",  "Xavfli belgilar topilmadi")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Kuchli ajratib ko'rsatish ─────────────────────────────
                SettingsCard {
                    SettingsToggleRow(
                        title    = "Kuchli ajratib ko'rsatish",
                        subtitle = if (strongHighlight) "Xavfli xabarlar yaqqolroq belgilanadi" else "Xavfli xabarlar oddiy ko'rinishda",
                        checked  = strongHighlight,
                        onToggle = { new -> ToggleSoundPlayer.playToggleSound(context, soundEnabled); scope.launch { store.setStrongHighlight(new) } }
                    )
                }
                Spacer(Modifier.height(6.dp))
                FooterHint("Ranglar ehtiyotkorlikni uy'otadi, vahima emas")



                SettingsCard {
                    Column(Modifier.padding(vertical = 20.dp)) {
                        Text(
                            "Bildirishnoma darajasi",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        ThresholdRadioRow("Faqat Xavfli (${AnalysisConstants.DANGEROUS_MIN}+)", "Faqat aniq xavfli xabarlar uchun", notifThreshold == AnalysisConstants.DANGEROUS_MIN) {
                            ToggleSoundPlayer.playToggleSound(context, soundEnabled); scope.launch { store.setNotificationThreshold(AnalysisConstants.DANGEROUS_MIN) }
                        }
                        Spacer(Modifier.height(4.dp))
                        ThresholdRadioRow("Shubhali + Xavfli (${AnalysisConstants.SUSPICIOUS_MIN}+)", "Shubhali va xavfli xabarlar uchun", notifThreshold == AnalysisConstants.SUSPICIOUS_MIN) {
                            ToggleSoundPlayer.playToggleSound(context, soundEnabled); scope.launch { store.setNotificationThreshold(AnalysisConstants.SUSPICIOUS_MIN) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                SettingsCard {
                    SettingsToggleRow(
                        title    = "Tushuntirishni ko'rsatish",
                        subtitle = if (showExplanation) "Nima uchun xavfli ekanligi ko'rsatiladi" else "Tushuntirish yashirilgan",
                        checked  = showExplanation,
                        onToggle = { new -> ToggleSoundPlayer.playToggleSound(context, soundEnabled); scope.launch { store.setShowExplanation(new) } }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ═══════════ HUQUQIY MA'LUMOTLAR ═════════════════════════
                SectionTitle("HUQUQIY MA'LUMOTLAR")
                SettingsCard {
                    SettingsRow(
                        title    = "Maxfiylik Siyosati",
                        subtitle = "Ma'lumotlaringiz qanday himoyalanishi haqida",
                        trailing = {
                            Icon(
                                imageVector        = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint               = AppColors.TextFooter,
                                modifier           = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.clickable { onNavigateToPrivacy() }
                    )
                    Divider(color = AppColors.CardBorder, thickness = 1.dp)
                    SettingsRow(
                        title    = "Foydalanish Shartlari",
                        subtitle = "Ilovadan foydalanish bo'yicha qoidalar",
                        trailing = {
                            Icon(
                                imageVector        = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint               = AppColors.TextFooter,
                                modifier           = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.clickable { onNavigateToTerms() }
                    )
                }

                Spacer(Modifier.height(30.dp))
                Text(
                    "Tahlil faqat qurilmada amalga oshiriladi — server yo'q",
                    color = AppColors.TextFooter, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// REUSABLE COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = AppColors.PrimaryBlue, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, letterSpacing = 0.16.em,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

@Composable
private fun FooterHint(text: String) {
    Text(text, color = AppColors.TextFooter, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) { content() }
}

/**
 * Reusable row for plain toggle cards (no side labels).
 * Title+subtitle flex left, trailing content right-aligned.
 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leadIcon: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 88.dp)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadIcon != null) {
                    leadIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, color = AppColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = AppColors.TextSubtitle, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
}



// ── Risk legend ──────────────────────────────────────────────────────────────

@Composable
private fun RiskLegendRow(color: Color, label: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(desc, color = AppColors.TextCard, fontSize = 13.sp)
        }
    }
}

// ── Radio row ────────────────────────────────────────────────────────────────

@Composable
private fun ThresholdRadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (selected) AppColors.PrimaryBlue.copy(alpha = 0.2f) else AppColors.ToggleTrackOff),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(AppColors.PrimaryBlue))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = AppColors.TextMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = AppColors.TextFooter, fontSize = 12.sp)
        }
    }
}
