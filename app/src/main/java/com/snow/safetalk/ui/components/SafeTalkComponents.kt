package com.snow.safetalk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snow.safetalk.ui.theme.AppColors

@Composable
fun SafeTalkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trackWidth: Dp = 52.dp,
    trackHeight: Dp = 28.dp,
    thumbSize: Dp = 22.dp,
    thumbPadding: Dp = 3.dp
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbPadding * 2 else 0.dp,
        animationSpec = tween(220),
        label = "thumbOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) AppColors.PrimaryBlue else AppColors.ToggleTrackOff,
        animationSpec = tween(220),
        label = "trackColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.White else AppColors.ToggleThumbOff,
        animationSpec = tween(220),
        label = "thumbColor"
    )

    Box(
        modifier = Modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(thumbPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 88.dp)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = AppColors.TextSubtitle, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            SafeTalkSwitch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
