package com.snow.safetalk.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.snow.safetalk.notification.BackgroundPopupPermissionHelper
import com.snow.safetalk.notification.NotificationPermissionHelper
import com.snow.safetalk.protection.ProtectionManager
import com.snow.safetalk.settings.SettingsDataStore
import com.snow.safetalk.sources.SourcesDataStore
import com.snow.safetalk.telegram.NotificationAccessHelper
import com.snow.safetalk.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-run unified protection setup screen — "Himoya sozlamalari".
 *
 * Feature-centered card order:
 * 1. SMS himoyasi        — RECEIVE_SMS + READ_SMS permissions
 * 2. Bildirishnoma ruxsati — POST_NOTIFICATIONS (Android 13+)
 * 3. Telegram himoyasi   — Notification Access (NLS) for messenger monitoring
 * 4. Doimiy himoya       — always-on background protection via ProtectionManager
 * 5. Qurilma sozlamalari — informational OEM battery/autostart guidance
 *
 * All cards reflect real device/app state and perform real setup actions.
 * States refresh on ON_RESUME when returning from system settings.
 */
@Composable
fun ProtectionOnboardingScreen(
    onCompleted: () -> Unit,
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsDataStore(context) }
    val sourcesStore = remember { SourcesDataStore(context) }
    val protectionManager = remember { ProtectionManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Observable states ──────────────────────────────────────────────

    // SMS permissions (RECEIVE_SMS + READ_SMS)
    var smsPermGranted by remember {
        mutableStateOf(
            listOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            ).all { p ->
                ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Notification fully enabled (runtime permission + system-level enabled)
    var notifFullyEnabled by remember {
        mutableStateOf(NotificationPermissionHelper.areNotificationsFullyEnabled(context))
    }

    // Notification Listener Service access (for Telegram monitoring)
    var nlsGranted by remember {
        mutableStateOf(NotificationAccessHelper.isNotificationAccessGranted(context))
    }

    // Always-on protection state
    val alwaysOnEnabled by protectionManager.observeAlwaysOnProtectionEnabled()
        .collectAsState(initial = false)

    // MIUI background popup permission state
    var backgroundPopupEnabled by remember {
        mutableStateOf(BackgroundPopupPermissionHelper.isPermissionEnabled(context))
    }
    // Track if user has visited settings and returned without enabling
    var showPopupWarning by remember { mutableStateOf(false) }
    var hasVisitedPopupSettings by remember { mutableStateOf(false) }

    // ── Refresh ALL states when returning from system settings ─────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsPermGranted = listOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                ).all { p ->
                    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
                }

                // Use the centralized helper — checks both runtime perm AND system state
                notifFullyEnabled = NotificationPermissionHelper.areNotificationsFullyEnabled(context)

                val nlsNow = NotificationAccessHelper.isNotificationAccessGranted(context)
                nlsGranted = nlsNow
                if (nlsNow) {
                    scope.launch { sourcesStore.setTelegramEnabled(true) }
                }

                // Refresh MIUI background popup permission state
                val popupNow = BackgroundPopupPermissionHelper.isPermissionEnabled(context)
                backgroundPopupEnabled = popupNow
                // Show warning if user visited settings but permission is still off
                if (hasVisitedPopupSettings && !popupNow) {
                    showPopupWarning = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Permission launchers ──────────────────────────────────────────

    // SMS permission launcher (RECEIVE_SMS + READ_SMS)
    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        smsPermGranted = allGranted
        if (allGranted) {
            scope.launch {
                sourcesStore.setSmsEnabled(true)
                settingsStore.setSmsSourceEnabled(true)
                settingsStore.setSmsNotifications(true)
            }
        }
    }

    // Notification permission launcher (Android 13+)
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Refresh the full state (runtime + system)
        notifFullyEnabled = NotificationPermissionHelper.areNotificationsFullyEnabled(context)
    }

    // NLS settings launcher (for Telegram monitoring)
    val nlsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = NotificationAccessHelper.isNotificationAccessGranted(context)
        nlsGranted = granted
        if (granted) {
            scope.launch { sourcesStore.setTelegramEnabled(true) }
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Header ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    AppColors.PrimaryBlue,
                                    AppColors.PrimaryBlueDark
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Himoya sozlamalari",
                color = AppColors.TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "SafeTalk xabarlaringizni doimiy himoya qilishi uchun " +
                    "quyidagi sozlamalarni yoqing.",
                color = AppColors.TextSubtitle,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // TOP PRIORITY: Protection features
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            // ── 1. SMS himoyasi ───────────────────────────────────
            SetupActionCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "SMS himoyasi",
                description = "Kiruvchi SMS xabarlar avtomatik tekshiriladi. " +
                    "Firibgarlik yoki xavfli xabar aniqlansa — darhol ogohlantirish beriladi.",
                actionLabel = if (smsPermGranted) "✓ Faol" else "Ruxsat berish",
                isCompleted = smsPermGranted,
                onAction = {
                    if (!smsPermGranted) {
                        smsPermLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                            )
                        )
                    }
                }
            )

            Spacer(Modifier.height(14.dp))

            // ── 2. Bildirishnoma ruxsati (Android 13+) ───────────
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupActionCard(
                    icon = Icons.Default.Notifications,
                    title = "Bildirishnoma ruxsati",
                    description = "Xavfli xabar aniqlanganda ogohlantirish ko'rsatish " +
                        "va himoya holatini bildirishnoma panelida ko'rsatish uchun kerak.",
                    actionLabel = if (notifFullyEnabled) "✓ Ruxsat berilgan"
                        else "Ruxsat berish",
                    isCompleted = notifFullyEnabled,
                    onAction = {
                        if (!notifFullyEnabled) {
                            val activity = context as? Activity
                            if (activity != null &&
                                NotificationPermissionHelper.isPermissionPermanentlyDenied(activity)
                            ) {
                                // Permission permanently denied — must go to system settings
                                NotificationPermissionHelper.openNotificationSettings(context)
                            } else if (!NotificationPermissionHelper.isRuntimePermissionGranted(context)) {
                                // Can still show the system dialog
                                NotificationPermissionHelper.markPermissionAsked(context)
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                // Runtime permission granted but system-level disabled
                                // (common on MIUI with sideloaded APKs)
                                NotificationPermissionHelper.openNotificationSettings(context)
                            }
                        }
                    }
                )

                Spacer(Modifier.height(14.dp))
            }

            // ── 3. Telegram himoyasi ─────────────────────────────
            SetupActionCard(
                icon = Icons.Default.Visibility,
                title = "Telegram himoyasi",
                description = "Telegram va boshqa messenjerlardan kelgan xabarlar " +
                    "avtomatik tekshiriladi. Buning uchun tizim sozlamalaridan " +
                    "\"Notification Access\" ruxsatini yoqish kerak.",
                actionLabel = if (nlsGranted) "✓ Faol" else "Sozlamalarni ochish",
                isCompleted = nlsGranted,
                onAction = {
                    if (!nlsGranted) {
                        nlsLauncher.launch(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                }
            )

            Spacer(Modifier.height(14.dp))

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // BACKGROUND & SYSTEM
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            // ── 4. Doimiy himoya ──────────────────────────────────
            SetupActionCard(
                icon = Icons.Default.Shield,
                title = "Doimiy himoya",
                description = "SafeTalk fon rejimida ishlaydi va kelgan xabarlarni " +
                    "avtomatik tekshiradi. Ilova yopilganda ham himoya faol qoladi.",
                actionLabel = if (alwaysOnEnabled) "✓ Yoqilgan" else "Yoqish",
                isCompleted = alwaysOnEnabled,
                onAction = {
                    if (!alwaysOnEnabled) {
                        scope.launch {
                            // Use the same full activation path as the Himoya screen:
                            // 1. Set the persisted flag + start FGS + schedule watchdog
                            protectionManager.enableAlwaysOnProtection()
                            // 2. Small delay to allow DataStore to propagate
                            delay(300)
                            // 3. Force sync to ensure FGS is definitely running,
                            //    matching what MainActivity.onCreate() does.
                            //    This covers race conditions where the initial
                            //    syncProtectionState() from onCreate ran before
                            //    the enable flag was persisted.
                            protectionManager.syncProtectionState()
                        }
                    }
                }
            )

            Spacer(Modifier.height(14.dp))

            // ── 5. To'liq ekran ogohlantirish (MIUI popup permission) ──
            SetupActionCard(
                icon = Icons.Default.Fullscreen,
                title = "To‘liq ekran ogohlantirish",
                description = "Xavfli xabarlarni darhol ko‘rsatish uchun bitta ruxsat talab qilinadi.\n\n" +
                              "Quyidagi sozlamani yoqing:\n" +
                              "→ 'Fonda ishlayotganda yangi oynalarni ochishga ruxsat berish'",
                actionLabel = if (backgroundPopupEnabled) "✓ Yoqilgan"
                    else "Ruxsatni yoqish",
                isCompleted = backgroundPopupEnabled,
                onAction = {
                    if (!backgroundPopupEnabled) {
                        hasVisitedPopupSettings = true
                        BackgroundPopupPermissionHelper.openSettings(context)
                    }
                }
            )

            // ── Warning: shown after returning from settings if still disabled ──
            AnimatedVisibility(
                visible = showPopupWarning && !backgroundPopupEnabled,
                enter = fadeIn(tween(250)) + expandVertically(tween(280)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AppColors.Warning.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, AppColors.Warning.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AppColors.Warning,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Bu ruxsat yoqilmasa, xavfli xabarlar faqat oddiy bildirishnoma sifatida ko‘rsatiladi.",
                            color = AppColors.Warning,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 6. Qurilma sozlamalari (unified guidance) ────────
            SetupInfoCard(
                icon = Icons.Default.BatteryAlert,
                title = "Qurilma sozlamalari",
                description = "Ba'zi qurilmalarda (Samsung, Xiaomi, Huawei, Tecno va boshqalar) " +
                    "fon rejimida ishlash cheklangan bo'lishi mumkin. " +
                    "Barqaror himoya uchun:\n\n" +
                    "• Batareya optimizatsiyasidan SafeTalk ni chiqaring\n" +
                    "• \"Cheklanmagan\" yoki \"Fonda ishlashga ruxsat\" rejimini yoqing\n" +
                    "• Avtomatik ishga tushish (Autostart) ruxsatini bering\n\n" +
                    "Bu sozlamalar qurilmangiz sozlamalaridan topiladi."
            )

            Spacer(Modifier.height(32.dp))

            // ── Complete Button ──────────────────────────────────
            Button(
                onClick = {
                    scope.launch {
                        settingsStore.setBackgroundProtectionInitialized(true)
                        settingsStore.setHasCompletedOnboarding(true)
                        onCompleted()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.PrimaryBlue
                )
            ) {
                Text(
                    text = "Tugatish",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Ruxsatlarni keyinroq ham Himoya bo'limidan sozlash mumkin",
                color = AppColors.TextFooter,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Reusable setup card components ──────────────────────────────────────────

/**
 * Actionable setup card with consistent structure:
 * Row 1: icon + title (feature name)
 * Row 2: description (what it does, in Uzbek)
 * Bottom: action button or completion status
 */
@Composable
private fun SetupActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    isCompleted: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(
            1.dp,
            if (isCompleted) AppColors.Safe.copy(alpha = 0.4f) else AppColors.CardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isCompleted) AppColors.Safe else AppColors.PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = description,
                color = AppColors.TextCard,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAction,
                enabled = !isCompleted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) AppColors.Safe.copy(alpha = 0.15f)
                        else AppColors.PrimaryBlue,
                    contentColor = if (isCompleted) AppColors.Safe else Color.White,
                    disabledContainerColor = AppColors.Safe.copy(alpha = 0.15f),
                    disabledContentColor = AppColors.Safe
                )
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Informational setup card (non-actionable) with same visual structure:
 * Row 1: icon + title
 * Row 2: description (no action button)
 */
@Composable
private fun SetupInfoCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = description,
                color = AppColors.TextCard,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

/**
 * Dedicated first-run Legal Acceptance Screen.
 * Placed before Protection Setup to enforce zero-bypass legal gating.
 */
@Composable
fun LegalAcceptanceScreen(
    onCompleted: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsDataStore(context) }

    var acceptedPrivacy by rememberSaveable { mutableStateOf(false) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = AppColors.PrimaryBlue,
                    modifier = Modifier.size(64.dp)
                )
            }

            Text(
                text = "SafeTalk",
                color = AppColors.TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "SafeTalk oflayn xavfsizlik va maxfiylikka asoslangan. Davom etish uchun qoidalar bilan tanishib chiqing va rozi bo'ling.",
                color = AppColors.TextSubtitle,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(48.dp))

            // ── Legal Checkboxes ─────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { acceptedPrivacy = !acceptedPrivacy }
            ) {
                Checkbox(
                    checked = acceptedPrivacy,
                    onCheckedChange = { acceptedPrivacy = it },
                    colors = CheckboxDefaults.colors(checkedColor = AppColors.PrimaryBlue)
                )
                Text(
                    text = "Men roziman: ",
                    color = AppColors.TextMain,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                TextButton(onClick = onNavigateToPrivacy, contentPadding = PaddingValues(start = 4.dp, end = 4.dp)) {
                    Text("Maxfiylik siyosati", color = AppColors.PrimaryBlue, fontSize = 15.sp)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .clickable { acceptedTerms = !acceptedTerms }
            ) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = { acceptedTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = AppColors.PrimaryBlue)
                )
                Text(
                    text = "Men roziman: ",
                    color = AppColors.TextMain,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                TextButton(onClick = onNavigateToTerms, contentPadding = PaddingValues(start = 4.dp, end = 4.dp)) {
                    Text("Foydalanish shartlari", color = AppColors.PrimaryBlue, fontSize = 15.sp)
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        // Crucial: write to SharedPreferences immediately via the DataStore extension
                        settingsStore.setHasAcceptedLegal(true)
                        onCompleted()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = acceptedPrivacy && acceptedTerms,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.PrimaryBlue,
                    disabledContainerColor = AppColors.PrimaryBlue.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Davom etish",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
