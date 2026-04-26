package com.snow.safetalk.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Complete color palette for one theme variant.
 */
data class SafeTalkPalette(
    // ── Background ──────────────────────────────────────────────────
    val bgSolid: Color,

    // ── Cards ───────────────────────────────────────────────────────
    val cardBg: Color,
    val cardBorder: Color,

    // ── Accent ──────────────────────────────────────────────────────
    val primaryBlue: Color,
    val primaryBlueDark: Color,
    val checkInner: Color,

    // ── Text ────────────────────────────────────────────────────────
    val textMain: Color,
    val textSubtitle: Color,
    val textCard: Color,
    val textFooter: Color,

    // ── Status ──────────────────────────────────────────────────────
    val dangerDot: Color,
    val safe: Color,
    val warning: Color,

    // ── Toggle / misc ───────────────────────────────────────────────
    val toggleTrackOff: Color,
    val toggleThumbOff: Color,
    val iconTint: Color,
    val divider: Color,
)

// ── DARK palette ──────────────────────────────────────────────────────────────

val DarkPalette = SafeTalkPalette(
    bgSolid         = Color(0xFF010409),
    cardBg          = Color(0xFF0B1A2A),
    cardBorder      = Color(0xFF1E3246),
    primaryBlue     = Color(0xFF2D9CDB),
    primaryBlueDark = Color(0xFF1A7FB8),
    checkInner      = Color(0xFF0B1F36),
    textMain        = Color(0xFFFFFFFF),
    textSubtitle    = Color(0xFF8FA6C1),
    textCard        = Color(0xFF9FB4CC),
    textFooter      = Color(0xFF7F95AD),
    dangerDot       = Color(0xFFE74C3C),
    safe            = Color(0xFF2ECC71),
    warning         = Color(0xFFF39C12),
    toggleTrackOff  = Color(0xFF2A3545),
    toggleThumbOff  = Color(0xFF6B7B8D),
    iconTint        = Color(0xFFE5EEF8),
    divider         = Color(0xFF1E3246),
)

// ── LIGHT palette ─────────────────────────────────────────────────────────────

val LightPalette = SafeTalkPalette(
    bgSolid         = Color(0xFFF5F6FA),
    cardBg          = Color(0xFFFFFFFF),
    cardBorder      = Color(0xFFE0E4EA),
    primaryBlue     = Color(0xFF2D9CDB),
    primaryBlueDark = Color(0xFF1A7FB8),
    checkInner      = Color(0xFFD6EDFA),
    textMain        = Color(0xFF0F1419),
    textSubtitle    = Color(0xFF5B6B7D),
    textCard        = Color(0xFF4A5568),
    textFooter      = Color(0xFF8A95A5),
    dangerDot       = Color(0xFFE74C3C),
    safe            = Color(0xFF27AE60),
    warning         = Color(0xFFE67E22),
    toggleTrackOff  = Color(0xFFCDD5DC),
    toggleThumbOff  = Color(0xFFA0AAB4),
    iconTint        = Color(0xFF2C3E50),
    divider         = Color(0xFFE0E4EA),
)

// ── CompositionLocal ──────────────────────────────────────────────────────────

val LocalSafeTalkColors = staticCompositionLocalOf { DarkPalette }

/**
 * Single-access-point for the current palette.
 * All properties are @Composable getters that read from CompositionLocal,
 * so they automatically reflect the active theme (dark/light).
 * Every existing `AppColors.X` call continues to work unchanged.
 */
object AppColors {
    val BgSolid: Color         @Composable get() = LocalSafeTalkColors.current.bgSolid
    val CardBg: Color          @Composable get() = LocalSafeTalkColors.current.cardBg
    val CardBorder: Color      @Composable get() = LocalSafeTalkColors.current.cardBorder
    val PrimaryBlue: Color     @Composable get() = LocalSafeTalkColors.current.primaryBlue
    val PrimaryBlueDark: Color @Composable get() = LocalSafeTalkColors.current.primaryBlueDark
    val CheckInner: Color      @Composable get() = LocalSafeTalkColors.current.checkInner
    val TextMain: Color        @Composable get() = LocalSafeTalkColors.current.textMain
    val TextSubtitle: Color    @Composable get() = LocalSafeTalkColors.current.textSubtitle
    val TextCard: Color        @Composable get() = LocalSafeTalkColors.current.textCard
    val TextFooter: Color      @Composable get() = LocalSafeTalkColors.current.textFooter
    val DangerDot: Color       @Composable get() = LocalSafeTalkColors.current.dangerDot
    val Safe: Color            @Composable get() = LocalSafeTalkColors.current.safe
    val Warning: Color         @Composable get() = LocalSafeTalkColors.current.warning
    val ToggleTrackOff: Color  @Composable get() = LocalSafeTalkColors.current.toggleTrackOff
    val ToggleThumbOff: Color  @Composable get() = LocalSafeTalkColors.current.toggleThumbOff
    val IconTint: Color        @Composable get() = LocalSafeTalkColors.current.iconTint
    val Divider: Color         @Composable get() = LocalSafeTalkColors.current.divider
}
