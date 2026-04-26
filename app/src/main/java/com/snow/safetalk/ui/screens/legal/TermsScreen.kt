package com.snow.safetalk.ui.screens.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    onBack: () -> Unit
) {
    var isUzbek by rememberSaveable { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BgSolid)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = if (isUzbek) "Foydalanish Shartlari" else "Terms of Service",
                        color = AppColors.TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppColors.TextMain
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { isUzbek = !isUzbek }
                    ) {
                        Text(
                            text = if (isUzbek) "EN" else "UZ",
                            color = AppColors.PrimaryBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            val textContent = LegalContent.getTermsOfService(isUzbek)
            val mainColor = AppColors.TextMain

            // ── Single AnnotatedString: eliminates per-line recomposition ──
            val annotatedText = remember(textContent) {
                buildAnnotatedString {
                    val lines = textContent.split("\n")
                    for (line in lines) {
                        when {
                            line.isBlank() -> append("\n")
                            line.startsWith("**") && line.endsWith("**") -> {
                                val headerText = line.removeSurrounding("**")
                                // Use larger relative scaling or just bold for headers
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(headerText)
                                }
                                append("\n")
                            }
                            line.startsWith("•") -> {
                                val cleanLine = line.removePrefix("• ").removePrefix("•").trim()
                                append("   •  ")
                                val parts = cleanLine.split("**")
                                for (i in parts.indices) {
                                    if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(parts[i]) }
                                    else append(parts[i])
                                }
                                append("\n")
                            }
                            else -> {
                                val parts = line.split("**")
                                for (i in parts.indices) {
                                    if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(parts[i]) }
                                    else append(parts[i])
                                }
                                append("\n")
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = annotatedText,
                    color = AppColors.TextMain,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val context = LocalContext.current
                TextButton(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(LegalLinks.TERMS_URL))
                    context.startActivity(intent)
                }) {
                    Text("Foydalanish shartlari", color = AppColors.PrimaryBlue)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
