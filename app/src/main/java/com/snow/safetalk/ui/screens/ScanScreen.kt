package com.snow.safetalk.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit = {},
    onAnalyze: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var messageText by remember { mutableStateOf("") }

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
                            text = "Xabarni tekshirish",
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
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
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
                Spacer(modifier = Modifier.height(12.dp))

                // ── Subtitle ──────────────────────────────────────────────────
                Text(
                    text = "Shubhali xabarni quyidagi maydonga joylashtiring",
                    color = AppColors.TextSubtitle,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Input card ────────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = {
                            Text(
                                "Shubhali xabarni shu yerga joylashtiring...",
                                color = AppColors.TextFooter
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 160.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor       = AppColors.TextMain,
                            unfocusedTextColor     = AppColors.TextMain,
                            cursorColor            = AppColors.PrimaryBlue,
                            focusedBorderColor     = Color.Transparent,
                            unfocusedBorderColor   = Color.Transparent,
                            focusedContainerColor  = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Primary button ────────────────────────────────────────────
                Button(
                    onClick = {
                        if (messageText.isBlank()) {
                            Toast.makeText(context, "Xabar maydonini to'ldiring", Toast.LENGTH_SHORT).show()
                        } else {
                            onAnalyze(messageText)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.PrimaryBlueDark,
                        contentColor = AppColors.TextMain
                    )
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Tekshirish",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Footer note ───────────────────────────────────────────────
                Text(
                    text = "Barcha tahlillar faqat qurilmangizda amalga oshiriladi va hech qayerda saqlanmaydi.",
                    color = AppColors.TextFooter,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
