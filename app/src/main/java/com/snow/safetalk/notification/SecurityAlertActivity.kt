package com.snow.safetalk.notification

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.MainActivity

/**
 * Dedicated full-screen alert Activity for DANGEROUS threat notifications.
 *
 * This Activity serves as the target for [SecurityAlertNotificationManager.setFullScreenIntent].
 * It is separate from [MainActivity] because the Android framework imposes strict
 * requirements on the Activity that receives a full-screen intent:
 *
 *  - `showWhenLocked` — must display even when the device is locked
 *  - `turnScreenOn` — must wake the screen when the Activity starts
 *  - Lightweight — must render instantly (no heavy Compose navigation stack)
 *
 * **Flow:**
 *  1. System fires the full-screen PendingIntent → this Activity launches over lock screen
 *  2. User sees a red/pulsing danger alert with risk score and a "View Analysis" button
 *  3. Tapping the button → launches [MainActivity] with the `analysis_id` extra
 *     → which navigates to ResultScreen → which cancels the notification
 *
 * This Activity does NOT cancel the notification itself — that responsibility belongs
 * exclusively to ResultScreen via [SecurityAlertNotificationManager.cancelForAnalysis].
 */
class SecurityAlertActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SecAlertActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Force display over lock screen + wake device ──────────────────────
        configureLockScreenBypass()

        val analysisId = intent?.getStringExtra("analysis_id")
        val riskScore = intent?.getIntExtra("risk_score", 0) ?: 0

        Log.d(TAG, "onCreate: analysisId=$analysisId, riskScore=$riskScore")

        if (analysisId.isNullOrBlank()) {
            Log.w(TAG, "No analysis_id in intent — finishing immediately")
            finish()
            return
        }

        setContent {
            SecurityAlertScreen(
                riskScore = riskScore,
                onViewAnalysis = {
                    // Launch MainActivity which navigates to ResultScreen (the ONLY
                    // place that cancels the notification).
                    // from_safetalk_internal=true is required: MainActivity.onCreate/onNewIntent
                    // silently drops analysis_id from untrusted callers.
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("analysis_id", analysisId)
                        putExtra("from_safetalk_internal", true)
                    }
                    startActivity(mainIntent)
                    finish()
                },
                onDismiss = {
                    // Dismiss this Activity but do NOT cancel the notification.
                    // The notification remains persistent — user must eventually
                    // open ResultScreen to clear it.
                    finish()
                }
            )
        }
    }

    /**
     * Configure this Activity to display over the lock screen and wake the device.
     *
     * Uses the modern API (setShowWhenLocked / setTurnScreenOn) on Android 8.1+
     * and the legacy FLAG approach as fallback.
     */
    private fun configureLockScreenBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            // Dismiss the keyguard so the Activity is interactive (not just visible
            // behind the lock screen with an unlock prompt)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

// ── Full-screen alert UI ──────────────────────────────────────────────────────

@Composable
private fun SecurityAlertScreen(
    riskScore: Int,
    onViewAnalysis: () -> Unit,
    onDismiss: () -> Unit
) {
    val dangerRed = Color(0xFFE53935)
    val dangerRedDark = Color(0xFFB71C1C)
    val bgDark = Color(0xFF0D0D0D)

    // Pulsing animation for urgency
    val infiniteTransition = rememberInfiniteTransition(label = "danger_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgDark, dangerRedDark.copy(alpha = 0.3f), bgDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Pulsing danger circle ─────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(pulseScale)
            ) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(dangerRed.copy(alpha = 0.12f))
                )
                // Inner circle
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(dangerRed.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$riskScore%",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        color = dangerRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Alert headline ────────────────────────────────────────────────
            Text(
                text = "🔴  XAVFLI XABAR",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = dangerRed,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )

            Text(
                text = "Xavfli xabar aniqlandi!\nSizning xavfsizligingiz uchun darhol tekshiring.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── "View Analysis" CTA ───────────────────────────────────────────
            Button(
                onClick = onViewAnalysis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = dangerRed,
                    contentColor = Color.White
                )
            ) {
                Text(
                    "BATAFSIL KO'RISH",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            }

            // ── Secondary "close overlay" (notification persists) ─────────────
            TextButton(onClick = onDismiss) {
                Text(
                    "Keyinroq ko'raman",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
