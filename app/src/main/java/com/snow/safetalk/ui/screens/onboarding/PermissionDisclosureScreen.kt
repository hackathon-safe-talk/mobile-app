package com.snow.safetalk.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PermissionDisclosureScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            "SafeTalk quyidagi ruxsatlardan foydalanadi:",
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text("📩 SMS ruxsati — firibgarlik va zararli xabarlarni aniqlash uchun")
        Text("🔔 Bildirishnoma ruxsati — Telegram va boshqa ilovalardagi xavflarni tekshirish uchun")

        Spacer(Modifier.height(12.dp))

        Text(
            "Xabar mazmuni tashqi serverlarga yuborilmaydi.\n" +
            "Barcha tahlillar faqat qurilmangizda amalga oshiriladi.",
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(24.dp))
        
        Text(
            "Davom etish orqali siz ushbu ruxsatlardan foydalanishga rozilik bildirasiz.",
            fontWeight = FontWeight.Light
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onContinue) {
            Text("Davom etish")
        }
    }
}
