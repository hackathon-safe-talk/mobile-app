package com.snow.safetalk.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.snow.safetalk.sources.SourcesDataStore
import com.snow.safetalk.telegram.NotificationAccessHelper
import com.snow.safetalk.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val store   = remember { SourcesDataStore(context) }

    val smsEnabled      by store.smsEnabled.collectAsState(initial = false)
    val telegramEnabled by store.telegramEnabled.collectAsState(initial = false)

    val snackbarHostState = remember { SnackbarHostState() }

    // ── Helper: open system App Settings ─────────────────────────────────────
    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data  = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    // ── Helper: open Notification Access settings ─────────────────────────────
    fun openNotificationAccess() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    // ── Helper: disable SMS source + cancel notifications ─────────────────────
    fun disableSms() {
        scope.launch {
            store.setSmsEnabled(false)
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancelAll()
            snackbarHostState.showSnackbar(
                "SMS himoyasi o'chirildi."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SMS permission launchers (Step 2: POST_NOTIFICATIONS after SMS)
    // ─────────────────────────────────────────────────────────────────────────
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { store.setSmsEnabled(true) }
        } else {
            scope.launch {
                store.setSmsEnabled(false)
                snackbarHostState.showSnackbar(
                    "Bildirishnoma ruxsati berilmadi. SMS himoya yoqilmadi."
                )
            }
        }
    }

    // Step 1: RECEIVE_SMS + READ_SMS
    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            scope.launch {
                store.setSmsEnabled(false)
                val result = snackbarHostState.showSnackbar(
                    message    = "SMS himoyasi uchun ruxsat kerak.",
                    actionLabel = "Sozlamalar",
                    duration    = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) openAppSettings()
            }
            return@rememberLauncherForActivityResult
        }
        // SMS granted — check POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (notifGranted) {
                scope.launch { store.setSmsEnabled(true) }
            } else {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            scope.launch { store.setSmsEnabled(true) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Telegram: Notification Access result launcher (re-check on return)
    // ─────────────────────────────────────────────────────────────────────────
    val notifAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = NotificationAccessHelper.isNotificationAccessGranted(context)
        if (granted) {
            scope.launch { store.setTelegramEnabled(true) }
        } else {
            scope.launch {
                store.setTelegramEnabled(false)
                snackbarHostState.showSnackbar(
                    "Notification access yoqilmasa Telegram himoya ishlamaydi."
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost   = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Xabar manbalari",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },

                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga", tint = AppColors.IconTint)
                        }
                    },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .animateContentSize(tween(280))
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "MANBALAR",
                    color = AppColors.PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ── Sources card ──────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {

                        // ── SMS row ───────────────────────────────────────
                        SourceToggleRow(
                            title    = "SMS",
                            subtitle = "Kiruvchi SMS xabarlarni avtomatik tahlil qilish",
                            checked  = smsEnabled,
                            onToggle = { wantEnabled ->
                                if (!wantEnabled) {
                                    disableSms()
                                    return@SourceToggleRow
                                }
                                // Turning ON: full permission chain
                                val smsGranted = listOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                ).all { p ->
                                    ContextCompat.checkSelfPermission(context, p) ==
                                        PackageManager.PERMISSION_GRANTED
                                }
                                if (!smsGranted) {
                                    smsPermLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.READ_SMS
                                        )
                                    )
                                    return@SourceToggleRow
                                }
                                // SMS granted — check notifications
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val notifGranted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!notifGranted) {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@SourceToggleRow
                                    }
                                }
                                // All granted
                                scope.launch { store.setSmsEnabled(true) }
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = AppColors.CardBorder
                        )

                        // ── Telegram row ──────────────────────────────────
                        SourceToggleRow(
                            title    = "Telegram",
                            subtitle = "Telegram xabarlarni tahlil qilish (Notification Access kerak)",
                            checked  = telegramEnabled,
                            onToggle = { wantEnabled ->
                                if (!wantEnabled) {
                                    scope.launch {
                                        store.setTelegramEnabled(false)
                                        snackbarHostState.showSnackbar("Telegram himoyasi o'chirildi.")
                                    }
                                    return@SourceToggleRow
                                }
                                // Turning ON: check Notification Access
                                val hasAccess = NotificationAccessHelper
                                    .isNotificationAccessGranted(context)
                                if (hasAccess) {
                                    scope.launch { store.setTelegramEnabled(true) }
                                } else {
                                    // Launch Notification Access settings, check on return
                                    notifAccessLauncher.launch(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    )
                                }
                            }
                        )
                    }
                }

                // ── Telegram: Notification Access hint (when OFF) ──────────
                AnimatedVisibility(
                    visible = !telegramEnabled,
                    enter   = fadeIn(tween(250)) + expandVertically(tween(280)),
                    exit    = fadeOut(tween(200)) + shrinkVertically(tween(250))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                            border = BorderStroke(1.dp, AppColors.CardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openNotificationAccess() }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint               = AppColors.PrimaryBlue,
                                    modifier           = Modifier.size(18.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Telegram uchun Notification Access yoqing",
                                        color      = AppColors.TextMain,
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Tizim sozlamalaridan SafeTalk uchun ruxsat bering",
                                        color    = AppColors.TextSubtitle,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ── SMS info card (when ON) ──────────────────────────────
                AnimatedVisibility(
                    visible = smsEnabled,
                    enter   = fadeIn(tween(250)) + expandVertically(tween(280)),
                    exit    = fadeOut(tween(200)) + shrinkVertically(tween(250))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(20.dp))
                        SourceInfoCard(
                            title = "SMS tahlili haqida",
                            bullets = listOf(
                                "SMS ruxsatlari so'raladi (RECEIVE_SMS, READ_SMS)",
                                "Faqat xabar matni tahlil qilinadi",
                                "Barcha tahlillar lokal — serverga yuborilmaydi",
                                "Shaxsiy ma'lumotlar saqlanmaydi"
                            )
                        )
                    }
                }

                // ── Telegram info card (when ON) ─────────────────────────
                AnimatedVisibility(
                    visible = telegramEnabled,
                    enter   = fadeIn(tween(250)) + expandVertically(tween(280)),
                    exit    = fadeOut(tween(200)) + shrinkVertically(tween(250))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(20.dp))
                        SourceInfoCard(
                            title = "Telegram tahlili haqida",
                            bullets = listOf(
                                "Notification Listener orqali xabarlar o'qiladi",
                                "Faqat xabar matni tahlil qilinadi",
                                "Barcha tahlillar lokal — serverga yuborilmaydi",
                                "Obuna talab qilinadi (pullik xizmat)"
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ── Reusable toggle row ───────────────────────────────────────────────────────

@Composable
private fun SourceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = AppColors.TextSubtitle, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        KaliStyleSwitch(checked = checked, onCheckedChange = onToggle)
    }
}

// ── Kali-style switch ─────────────────────────────────────────────────────────

@Composable
private fun KaliStyleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trackWidth: Dp = 52.dp,
    trackHeight: Dp = 28.dp,
    thumbSize: Dp = 22.dp,
    thumbPadding: Dp = 3.dp
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbPadding * 2 else 0.dp,
        animationSpec = tween(220),
        label = "thumbOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) AppColors.PrimaryBlue else AppColors.ToggleTrackOff,
        animationSpec = tween(220),
        label = "trackColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.White else AppColors.ToggleThumbOff,
        animationSpec = tween(220),
        label = "thumbColor"
    )

    Box(
        modifier = Modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(thumbPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

// ── Generic source info card ──────────────────────────────────────────────────

@Composable
private fun SourceInfoCard(title: String, bullets: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                color = AppColors.TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            bullets.forEach { text ->
                Text(
                    "• $text",
                    color = AppColors.TextCard,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
