package com.snow.safetalk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.settings.SettingsDataStore
import com.snow.safetalk.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember { SettingsDataStore(context) }
    val isDarkMode by store.isDarkMode.collectAsState(initial = true)
    
    val versionName = "1.0.0"

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
                            "Ilova haqida",
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // Centered Info Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) AppColors.PrimaryBlue.copy(alpha = 0.15f) else Color(0xFFD6E4F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "SafeTalk Info",
                        tint = AppColors.PrimaryBlue,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "SafeTalk",
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "v$versionName",
                    color = AppColors.TextSubtitle,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Card A (Description)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "SafeTalk — bu SMS firibgarliklarini aniqlash va foydalanuvchilarni xavfli xabarlardan himoya qilish uchun yaratilgan xavfsizlik ilovasi.",
                            color = AppColors.TextMain,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                        Text(
                            text = "Ilova to'liq offline ishlaydi va barcha tahlillar faqat qurilmangizda amalga oshiriladi.",
                            color = AppColors.TextSubtitle,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card B (App info table style)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AppInfoRow(label = "Ilova nomi", value = "SafeTalk")
                        HorizontalDivider(color = AppColors.Divider)
                        
                        AppInfoRow(label = "Versiya", value = versionName)
                        HorizontalDivider(color = AppColors.Divider)
                        
                        AppInfoRow(label = "Ishlab chiqaruvchi", value = "SafeTalk Team")
                        HorizontalDivider(color = AppColors.Divider)
                        
                        AppInfoRow(label = "Maqsad", value = "SMS xavfsizlik tahlili")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Xavfsizlik. Maxfiylik. Ishonch.",
                    color = AppColors.TextFooter,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun AppInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.TextSubtitle,
            fontSize = 15.sp
        )
        Text(
            text = value,
            color = AppColors.TextMain,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}
