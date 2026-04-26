package com.snow.safetalk.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    val fullText = "Xabarlaringiz uchun aqlli himoyachi"
    var displayedText by remember { mutableStateOf("") }
    var showLogo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 0.0s -> initial load
        delay(300)
        // 0.3s -> logo appears
        showLogo = true
        delay(100)
        // 0.4s -> typing animation starts
        // total duration ~ 1.6s for 35 chars = ~45ms per char
        for (i in fullText.indices) {
            displayedText = fullText.substring(0, i + 1)
            delay(40)
        }
        // Total time so far ~ 1.8s. Wait until 2.3s total.
        delay(500)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010409)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                // Position slightly above center (~40% from top)
                .fillMaxHeight(0.8f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showLogo) {
                Image(
                    painter = painterResource(id = R.drawable.ic_splash_logo_clean),
                    contentDescription = "SafeTalk Logo",
                    modifier = Modifier.width(200.dp), // Adjust width as needed
                    contentScale = ContentScale.Fit
                )
                
                Spacer(modifier = Modifier.height(50.dp))
                
                // Fixed height to prevent jumping when text appears
                Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = displayedText,
                        color = Color(0xFFD1D5DB),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
