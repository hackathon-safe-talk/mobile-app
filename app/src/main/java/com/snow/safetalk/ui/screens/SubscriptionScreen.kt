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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit = {}
) {
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
                            "Obuna",
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
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Obuna rejalari",
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Card 1: BEPUL
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SMS tahlili",
                                color = AppColors.TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            // BEPUL Badge
                            BadgeCard(text = "BEPUL", bgColor = AppColors.Safe.copy(alpha = 0.15f), textColor = AppColors.Safe)
                        }
                        
                        Text(
                            text = "SMS xabarlarni real vaqtda tahlil qilish — to'liq bepul va cheksiz.",
                            color = AppColors.TextSubtitle,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card 2: PRO
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.PrimaryBlue.copy(alpha = 0.5f)) // Slightly highlighted border for PRO
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Messenjer tahlili",
                                color = AppColors.TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            // PRO Badge
                            BadgeCard(text = "PRO", bgColor = AppColors.PrimaryBlue.copy(alpha = 0.15f), textColor = AppColors.PrimaryBlue)
                        }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Telegram va boshqa messenjerlardan kelgan xabarlarni tahlil qilish.",
                                color = AppColors.TextSubtitle,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                            Text(
                                text = "Tez kunda ishga tushadi",
                                color = AppColors.TextFooter,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun BadgeCard(text: String, bgColor: Color, textColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
