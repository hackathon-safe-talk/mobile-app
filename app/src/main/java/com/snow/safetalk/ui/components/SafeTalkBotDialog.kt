package com.snow.safetalk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.snow.safetalk.bot.BotLinks
import com.snow.safetalk.ui.theme.AppColors

@Composable
fun SafeTalkBotDialog(
    isTelegramInstalled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Avatar (centered)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AppColors.PrimaryBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.snow.safetalk.R.drawable.ic_bot_assistant),
                        contentDescription = "SafeTalk Bot",
                        tint = AppColors.PrimaryBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isTelegramInstalled) {
                    // 2. Title
                    Text(
                        text = "SafeTalk Bot",
                        color = AppColors.TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 4. Username
                    Text(
                        text = "@${BotLinks.USERNAME}",
                        color = AppColors.PrimaryBlue,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Description
                    Text(
                        text = "Telegram ichida xabarlar, havolalar va fayllarni qulay tarzda tezkor tekshiruvchi yordamchi bot.",
                        color = AppColors.TextSubtitle,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Note
                    Text(
                        text = "Davom etsangiz, mos Telegram ilovasi ochiladi.",
                        color = AppColors.TextFooter,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Title for no telegram
                    Text(
                        text = "Telegram topilmadi",
                        color = AppColors.TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message for no telegram
                    Text(
                        text = "SafeTalk Bot'dan foydalanish uchun qurilmada Telegram ilovasi o‘rnatilgan bo‘lishi kerak.",
                        color = AppColors.TextSubtitle,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 6. Buttons
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isTelegramInstalled) "Telegramda ochish" else "Telegramni yuklab olish",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Bekor qilish",
                        color = AppColors.TextSubtitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
