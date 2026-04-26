package com.snow.safetalk.ui.screens

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snow.safetalk.notification.BackgroundPopupPermissionHelper
import com.snow.safetalk.notification.NotificationPermissionHelper
import com.snow.safetalk.settings.SettingsDataStore
import com.snow.safetalk.sources.SourcesDataStore
import com.snow.safetalk.telegram.NotificationAccessHelper
import com.snow.safetalk.ui.components.SafeTalkSwitch
import com.snow.safetalk.ui.theme.AppColors
import com.snow.safetalk.core.ToggleSoundPlayer
import com.snow.safetalk.protection.ProtectionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SecurityScreen(
    focusItem: String? = null,
    onFocusConsumed: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Data stores
    val sourcesStore = remember { SourcesDataStore(context) }
    val settingsStore = remember { SettingsDataStore(context) }

    // Sources state
    val smsEnabled by sourcesStore.smsEnabled.collectAsState(initial = false)
    val telegramEnabled by sourcesStore.telegramEnabled.collectAsState(initial = false)

    // Settings state
    val smsSourceEnabled by settingsStore.smsSourceEnabled.collectAsState(initial = false)
    val smsNotifications by settingsStore.smsNotifications.collectAsState(initial = false)
    val soundEnabled by settingsStore.soundEnabled.collectAsState(initial = true)
    val fullScreenAlertEnabled by settingsStore.fullScreenAlertEnabled.collectAsState(initial = true)

    // Protection manager
    val protectionManager = remember { ProtectionManager(context) }
    val alwaysOnEnabled by protectionManager.observeAlwaysOnProtectionEnabled()
        .collectAsState(initial = false)
    val bootRestoreEnabled by protectionManager.observeBootRestoreEnabled()
        .collectAsState(initial = true)

    // Notification access state (refreshed on resume)
    var notifAccessGranted by remember {
        mutableStateOf(NotificationAccessHelper.isNotificationAccessGranted(context))
    }

    // MIUI background popup permission state (refreshed on resume)
    var backgroundPopupEnabled by remember {
        mutableStateOf(BackgroundPopupPermissionHelper.isPermissionEnabled(context))
    }

    // Toggle debounce to prevent rapid double-taps
    var isTogglingProtection by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // ── Deep Navigation Scroll & Highlight ──
    val smsRequester = remember { BringIntoViewRequester() }
    val telegramRequester = remember { BringIntoViewRequester() }
    
    val smsHighlight = remember { Animatable(0f) }
    val telegramHighlight = remember { Animatable(0f) }

    // Read the actual focus target and the unique event ID
    val actualFocus = focusItem?.substringBefore("_")
    val focusId = focusItem?.substringAfter("_", "") ?: ""
    
    // Remember which focus ID we have already consumed. This survives recomposition and tab switching.
    var consumedFocusId by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(focusItem, consumedFocusId) {
        if (actualFocus != null && focusId.isNotEmpty() && focusId != consumedFocusId) {
            delay(300) // slight delay to ensure UI is laid out
            if (actualFocus == "sms") {
                smsRequester.bringIntoView()
                smsHighlight.animateTo(0.2f, tween(300))
                smsHighlight.animateTo(0f, tween(1000))
            } else if (actualFocus == "telegram") {
                telegramRequester.bringIntoView()
                telegramHighlight.animateTo(0.2f, tween(300))
                telegramHighlight.animateTo(0f, tween(1000))
            }
            
            // Mark this specific event as consumed, so it will NEVER replay.
            consumedFocusId = focusId
            
            // Still inform nav host to try removing it, just for extra cleanup
            onFocusConsumed()
        }
    }
    
    // ── Lifecycle Sync (Phase 4) ──
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Refresh notification access state
                notifAccessGranted = NotificationAccessHelper.isNotificationAccessGranted(context)

                // Check SMS Permissions
                val smsGranted = listOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                ).all { p ->
                    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
                }
                
                if (!smsGranted) {
                    scope.launch {
                        sourcesStore.setSmsEnabled(false)
                        settingsStore.disableAllSmsFeatures()
                    }
                }

                // Check if system-level notifications are enabled.
                // Catches MIUI silently disabling notifications for sideloaded APKs.
                if (!NotificationPermissionHelper.areNotificationsFullyEnabled(context)) {
                    scope.launch {
                        settingsStore.setSmsNotifications(false)
                    }
                }
                
                // Check Notification Access for Telegram
                if (!NotificationAccessHelper.isNotificationAccessGranted(context)) {
                    scope.launch {
                        sourcesStore.setTelegramEnabled(false)
                    }
                } else if (telegramEnabled) {
                    // Sync up in case it was somehow granted but store fell out of sync
                    scope.launch {
                        sourcesStore.setTelegramEnabled(true)
                    }
                }

                // Refresh MIUI background popup permission state
                backgroundPopupEnabled = BackgroundPopupPermissionHelper.isPermissionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── Helper functions for system intents ──
    val appSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        appSettingsLauncher.launch(intent)
    }

    fun openNotificationAccess() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    fun disableAllSmsRelated() {
        scope.launch {
            sourcesStore.setSmsEnabled(false)
            settingsStore.disableAllSmsFeatures()
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancelAll()
            snackbarHostState.showSnackbar(
                "SMS himoyasi o'chirildi. Xohlasangiz tizim sozlamalaridan SMS ruxsatini ham bekor qilishingiz mumkin."
            )
        }
    }

    // ── Notification Permission Launcher (Android 13+) ──
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                sourcesStore.setSmsEnabled(true)
                settingsStore.setSmsNotifications(true)
            }
        } else {
            // Check if permanently denied — show snackbar with settings action
            val activity = context as? Activity
            val permanentlyDenied = activity != null &&
                NotificationPermissionHelper.isPermissionPermanentlyDenied(activity)

            scope.launch {
                sourcesStore.setSmsEnabled(false)
                settingsStore.setSmsNotifications(false)
                val result = snackbarHostState.showSnackbar(
                    message = if (permanentlyDenied)
                        "Bildirishnoma ruxsati doimiy rad etilgan. Sozlamalardan yoqing."
                    else
                        "Bildirishnoma ruxsatisiz xavfsizlik ogohlantirishi ishlamaydi.",
                    actionLabel = if (permanentlyDenied) "Sozlamalar" else null,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    NotificationPermissionHelper.openNotificationSettings(context)
                }
            }
        }
    }

    // ── SMS Permission Launcher ──
    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            scope.launch {
                sourcesStore.setSmsEnabled(false)
                settingsStore.setSmsSourceEnabled(false)
                settingsStore.setSmsNotifications(false)
                snackbarHostState.showSnackbar("SMS tahlili uchun ruxsat berilmadi.")
            }
            return@rememberLauncherForActivityResult
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (NotificationPermissionHelper.areNotificationsFullyEnabled(context)) {
                scope.launch {
                    sourcesStore.setSmsEnabled(true)
                    settingsStore.setSmsSourceEnabled(true)
                    settingsStore.setSmsNotifications(true)
                }
            } else if (!NotificationPermissionHelper.isRuntimePermissionGranted(context)) {
                NotificationPermissionHelper.markPermissionAsked(context)
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Runtime granted but system-level off — open settings
                NotificationPermissionHelper.openNotificationSettings(context)
            }
        } else {
            scope.launch {
                sourcesStore.setSmsEnabled(true)
                settingsStore.setSmsSourceEnabled(true)
                settingsStore.setSmsNotifications(true)
            }
        }
    }

    // ── Telegram Notification Access Launcher ──
    val notifAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = NotificationAccessHelper.isNotificationAccessGranted(context)
        if (granted) {
            scope.launch { sourcesStore.setTelegramEnabled(true) }
        } else {
            scope.launch {
                sourcesStore.setTelegramEnabled(false)
            }
        }
    }

    fun requestSmsPermissionsFull() {
        val smsGranted = listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        ).all { p ->
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        }
        if (!smsGranted) {
            smsPermLauncher.launch(
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationPermissionHelper.areNotificationsFullyEnabled(context)) {
                val activity = context as? Activity
                if (activity != null &&
                    NotificationPermissionHelper.isPermissionPermanentlyDenied(activity)
                ) {
                    // Permanently denied — redirect to system settings
                    NotificationPermissionHelper.openNotificationSettings(context)
                } else if (!NotificationPermissionHelper.isRuntimePermissionGranted(context)) {
                    // Can still show the system dialog
                    NotificationPermissionHelper.markPermissionAsked(context)
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Runtime granted but system-level disabled (MIUI)
                    NotificationPermissionHelper.openNotificationSettings(context)
                }
                return
            }
        }
        
        scope.launch {
            sourcesStore.setSmsEnabled(true)
            settingsStore.setSmsSourceEnabled(true)
            settingsStore.setSmsNotifications(true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // Modified TopAppBar to only show Menu per requirements
                TopAppBar(
                    title = {
                        Text(
                            "Himoya",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },

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
                Spacer(modifier = Modifier.height(4.dp))

                // ═══════════ XABAR MANBALARI ═══════════════════════════════════
                SectionTitle("XABAR MANBALARI")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // ── SMS row ───────────────────────────────────────
                        ProtectionSettingItem(
                            title = "SMS",
                            subtitle = "Kelgan SMS xabarlarni firibgarlik alomatlari uchun tekshiradi",
                            checked = smsEnabled,
                            onToggle = { wantEnabled ->
                                ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                                if (!wantEnabled) {
                                    disableAllSmsRelated()
                                } else {
                                    requestSmsPermissionsFull()
                                }
                            },
                            explanation = listOf(
                                "SafeTalk telefoningizga kelgan SMS xabarlarni tekshiradi",
                                "Xabarda firibgarlik yoki shubhali havola aniqlansa ogohlantirish beradi",
                                "Bu funksiya ishlashi uchun SMS ruxsati talab qilinadi"
                            ),
                            modifier = Modifier
                                .bringIntoViewRequester(smsRequester)
                                .background(AppColors.PrimaryBlue.copy(alpha = smsHighlight.value))
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = AppColors.CardBorder
                        )

                        // ── Telegram row ──────────────────────────────────
                        ProtectionSettingItem(
                            title = "Telegram xabarlarini tahlil qilish",
                            subtitle = "Agar shubhali havola, fayl yoki firibgarlik alomatlari aniqlansa, sizni ogohlantiradi.",
                            checked = telegramEnabled,
                            onToggle = { wantEnabled ->
                                ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                                if (!wantEnabled) {
                                    scope.launch {
                                        sourcesStore.setTelegramEnabled(false)
                                    }
                                } else {
                                    if (NotificationAccessHelper.isNotificationAccessGranted(context)) {
                                        scope.launch { sourcesStore.setTelegramEnabled(true) }
                                    } else {
                                        notifAccessLauncher.launch(
                                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                        )
                                    }
                                }
                            },
                            explanation = listOf(
                                "SafeTalk Telegram bildirishnomalaridagi xabarlarni tekshiradi",
                                "Shubhali havola, fayl yoki firibgarlik iboralari aniqlansa ogohlantirish beradi",
                                "Bu funksiya ishlashi uchun bildirishnomalarga ruxsat kerak bo‘ladi"
                            ),
                            modifier = Modifier
                                .bringIntoViewRequester(telegramRequester)
                                .background(AppColors.PrimaryBlue.copy(alpha = telegramHighlight.value))
                        )
                    }
                }

                // ── Telegram helper card (if not granted) ───────────────
                AnimatedVisibility(
                    visible = !telegramEnabled,
                    enter = fadeIn(tween(250)) + expandVertically(tween(280)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
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
                                    .defaultMinSize(minHeight = 88.dp)
                                    .clickable { openNotificationAccess() }
                                    .padding(horizontal = 22.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = AppColors.PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Telegram uchun Notification Access yoqing",
                                        color = AppColors.TextMain,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Tizim sozlamalaridan SafeTalk uchun ruxsat bering",
                                        color = AppColors.TextSubtitle,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ═══════════ SMS XAVFSIZLIGI ═════════════════════════════════
                SectionTitle("SMS XAVFSIZLIGI")

                // ── SMS Source toggles and Revoke option  ─────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ProtectionSettingItem(
                            title = "SMS ruxsati",
                            subtitle = "Ilovaga kiruvchi SMS xabarlarini o‘qishga ruxsat beradi",
                            checked = smsSourceEnabled,
                            onToggle = { wantEnabled ->
                                ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                                if (!wantEnabled) {
                                    disableAllSmsRelated()
                                } else {
                                    requestSmsPermissionsFull()
                                }
                            },
                            explanation = listOf(
                                "Ilova kiruvchi SMS xabarlarni qabul qilib tahlil qiladi",
                                "Bu orqali firibgarlik xabarlarini aniqlash mumkin",
                                "Agar bu sozlama o‘chiq bo‘lsa SMS tahlili ishlamaydi"
                            )
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = AppColors.CardBorder
                        )

                        ProtectionSettingItem(
                            title = "Real-time SMS himoya",
                            subtitle = "Xavfli SMS kelganda avtomatik tahlil va ogohlantirish",
                            checked = smsNotifications,
                            onToggle = { wantEnabled ->
                                ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                                if (!wantEnabled) {
                                    disableAllSmsRelated()
                                } else {
                                    requestSmsPermissionsFull()
                                }
                            },
                            explanation = listOf(
                                "SafeTalk yangi kelgan SMS xabarlarni darhol tekshiradi",
                                "Agar xabar xavfli bo‘lsa sizga ogohlantirish ko‘rsatadi",
                                "Bu funksiya firibgarlik xabarlarini tez aniqlashga yordam beradi"
                            )
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = AppColors.CardBorder
                        )

                        ProtectionSettingItem(
                            title = "Ruxsatlarni bekor qilish",
                            subtitle = "Tizim sozlamalaridan SMS va bildirishnoma ruxsatini olib tashlash",
                            checked = false, 
                            isToggleable = false,
                            onToggle = { disableAllSmsRelated(); openAppSettings() },
                            explanation = listOf(
                                "Bu yer orqali ilova ruxsatlarini tizim sozlamalaridan o‘chirishingiz mumkin",
                                "Agar ruxsatlar olib tashlansa himoya funksiyalari ishlamaydi",
                                "Ruxsatlarni qayta yoqish uchun tizim sozlamalariga o‘tish kerak bo‘ladi"
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(6.dp))
                FooterHint("Ilova tizim ruxsatlarini o'zi bekor qila olmaydi")

                Spacer(Modifier.height(24.dp))

                // ═══════════ DOIMIY HIMOYA ══════════════════════════════════
                SectionTitle("DOIMIY HIMOYA")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ProtectionSettingItem(
                            title = "Doimiy himoya",
                            subtitle = if (alwaysOnEnabled)
                                "SafeTalk fonda xabarlarni kuzatib bormoqda"
                            else
                                "Ilova yopilganda ham himoyani faol saqlaydi",
                            checked = alwaysOnEnabled,
                            onToggle = { wantEnabled ->
                                if (isTogglingProtection) return@ProtectionSettingItem
                                ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                                isTogglingProtection = true
                                scope.launch {
                                    try {
                                        if (wantEnabled) {
                                            protectionManager.enableAlwaysOnProtection()
                                        } else {
                                            protectionManager.disableAlwaysOnProtection()
                                        }
                                    } finally {
                                        isTogglingProtection = false
                                    }
                                }
                            },
                            explanation = listOf(
                                "SafeTalk himoyasini ilova yopilganda ham faol saqlaydi",
                                "Fon rejimida xabarlar tekshirilishda davom etadi",
                                "Bildirishnoma panelida himoya belgisi ko'rinadi"
                            )
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = AppColors.CardBorder
                        )

                        ProtectionSettingItem(
                            title = "Qayta ishga tushganda tiklash",
                            subtitle = "Telefon qayta yoqilganda himoyani avtomatik tiklaydi",
                            checked = bootRestoreEnabled,
                            onToggle = { wantEnabled ->
                                ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                                scope.launch {
                                    protectionManager.setBootRestoreEnabled(wantEnabled)
                                }
                            },
                            explanation = listOf(
                                "Telefon qayta yoqilganda SafeTalk himoyasi avtomatik ishga tushadi",
                                "Bu funksiya faqat doimiy himoya yoqilgan bo'lsa ishlaydi",
                                "Tizim cheklovlari tufayli ba'zi qurilmalarda kechikish bo'lishi mumkin"
                            )
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = AppColors.CardBorder
                        )

                        ProtectionSettingItem(
                            title = "To’liq ekran ogohlantirish",
                            subtitle = if (fullScreenAlertEnabled)
                                "Xavfli xabar kelganda to’liq ekran ogohlantirish yoqilgan"
                            else
                                "Xavfli xabar kelganda faqat bildirishnoma ko’rsatiladi",
                            checked = fullScreenAlertEnabled,
                            onToggle = { wantEnabled ->
                                ToggleSoundPlayer.playToggleSound(context, soundEnabled)
                                scope.launch {
                                    settingsStore.setFullScreenAlertEnabled(wantEnabled)
                                    // On MIUI/HyperOS, also open the system popup permission
                                    // settings when the user turns the feature ON so they can
                                    // grant the "Display pop-up windows while running in the
                                    // background" permission that MIUI requires.
                                    if (wantEnabled &&
                                        BackgroundPopupPermissionHelper.isMiuiDevice() &&
                                        !backgroundPopupEnabled
                                    ) {
                                        BackgroundPopupPermissionHelper.openSettings(context)
                                    }
                                }
                            },
                            explanation = listOf(
                                "SafeTalk xavfli xabar kelganda darhol sizni ogohlantiradi.",
                                "Yoqilganda: xabar to’liq ekran ko’rinishida chiqadi (qurilma qulflangan bo’lsa ham).",
                                "O’chirilganda: faqat bildirishnoma panelida o’chmas ogohlantirish chiqadi.",
                                "Xiaomi/MIUI qurilmalarda: tizim sozlamalaridan ‘Fonda ishlayotganda oyna ochish’ ruxsatini ham yoqing."
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Notification Access Status ─────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!notifAccessGranted) Modifier.clickable {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    )
                                } else Modifier
                            )
                            .padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (notifAccessGranted) AppColors.Safe
                                    else AppColors.Warning
                                )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (notifAccessGranted)
                                    "Bildirishnoma ruxsati: berilgan"
                                else
                                    "Bildirishnoma ruxsati: talab qilinadi",
                                color = AppColors.TextMain,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (!notifAccessGranted) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Telegram himoyasi uchun ruxsat zarur",
                                    color = AppColors.TextSubtitle,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (!notifAccessGranted) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = AppColors.PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Device Compatibility Guidance ──────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AppColors.CardBg
                    ),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 22.dp, vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Qurilma sozlamalari",
                            color = AppColors.TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ba'zi qurilmalarda (Samsung, Xiaomi, Huawei, Tecno va boshqalar) " +
                                "fon rejimida ishlash cheklangan bo'lishi mumkin. " +
                                "Barqaror himoya uchun:",
                            color = AppColors.TextSubtitle,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Text(
                            text = "• Batareya optimizatsiyasidan SafeTalk ni chiqaring\n" +
                                "• \"Cheklanmagan\" yoki \"Fonda ishlashga ruxsat\" rejimini yoqing\n" +
                                "• Avtomatik ishga tushish (Autostart) ruxsatini bering",
                            color = AppColors.TextCard,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                FooterHint(
                    if (alwaysOnEnabled) "Himoya fonda faol"
                    else "Doimiy himoya hozir o'chirilgan"
                )

                Spacer(modifier = Modifier.height(16.dp))
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
private fun ProtectionSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    explanation: List<String>,
    modifier: Modifier = Modifier,
    isToggleable: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 88.dp)
                .clickable { if (isToggleable) onToggle(!checked) else onToggle(false) }
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (title == "Ruxsatlarni bekor qilish") AppColors.DangerDot else AppColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = AppColors.TextSubtitle,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Expand trigger
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp, horizontal = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Batafsil",
                        color = AppColors.PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AppColors.PrimaryBlue,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(rotation)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier.width(56.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isToggleable) {
                    SafeTalkSwitch(checked = checked, onCheckedChange = onToggle)
                } else {
                    Text(text = "›", color = AppColors.TextSubtitle, fontSize = 24.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                explanation.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = AppColors.PrimaryBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = line,
                            color = AppColors.TextCard,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}


