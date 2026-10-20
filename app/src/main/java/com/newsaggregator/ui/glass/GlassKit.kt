package com.newsaggregator.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.newsaggregator.ui.theme.LocalNewsColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * NewsFlow glass system — EXACT port of the original v4.1.7 drawable recipes.
 *
 * The 4.1.7 look is NOT a blur; it is the "ring recipe" layer-list used by
 * story_card_bg / tabbar_bg / pill_bg / header_btn_bg / search_bg:
 *   L1 rim      : 0.75dp-visible edge ring, 315° gradient
 *                 #A6FFFFFF -> #14FFFFFF -> #40FFFFFF (bright top-left corner)
 *   L2 plate    : inset 0.75dp, glass_plate (#8C050510 dark / #D9F5F5FA light)
 *                 — 55% opacity so the animated orbs show through clearly
 *   L3 body     : inset 0.75dp, vertical gradient (bottom -> top)
 *                 card  : #1FFFFFFF -> #0DFFFFFF
 *                 chrome: #14FFFFFF -> #08FFFFFF
 *   Radii       : inner = outer - 0.75dp (outer-inner radius = inset rule)
 *
 * Behind everything sits the animated 3-orb field from GradientBackgroundView
 * (22s loop, dark: 0xAA5500B0 / 0x66FF6B4A / 0x4460A5FA; light: 0x22A855F7 /
 * 0x18FF6B4A / 0x1560A5FA).
 */

/** Haze state published by [NewsGlassBackdrop]; kept for API compatibility. */
val LocalNewsHaze = staticCompositionLocalOf<HazeState?> { null }
typealias HazeState = Unit

/** Body gradient variant: story cards vs chrome bars (header / tab bar / search). */
enum class GlassBody { Card, Chrome }

@Composable
fun NewsGlassBackdrop(content: @Composable () -> Unit) {
    val dark = !LocalNewsColors.current.isLight
    val transition = rememberInfiniteTransition(label = "orbField")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbAngle"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF050510) else Color(0xFFF5F5FA))
    ) {
        // Orb field behind EVERYTHING (mirrors GradientBackgroundView.onDraw).
        Box(Modifier.fillMaxSize()) {
            OrbField(dark = dark, angleDegrees = t)
        }
        CompositionLocalProvider(LocalNewsHaze provides Unit) {
            content()
        }
    }
}

@Composable
private fun OrbField(dark: Boolean, angleDegrees: Float) {
    val t = angleDegrees
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        if (dark) {
            orb(0xAA5500B0.toInt(), baseX = 0.75f, baseY = 0.12f, r = 0.45f,
                t = t, speed = 1.0f, phase = 0f, driftX = 0.15f, driftY = 0.08f, driftScale = 0.15f)
            orb(0x66FF6B4A.toInt(), baseX = 0.15f, baseY = 0.70f, r = 0.38f,
                t = t, speed = 0.7f, phase = 120f, driftX = 0.12f, driftY = 0.10f, driftScale = 0.12f)
            orb(0x4460A5FA.toInt(), baseX = 0.60f, baseY = 0.40f, r = 0.30f,
                t = t, speed = 0.5f, phase = 240f, driftX = 0.10f, driftY = 0.15f, driftScale = 0.10f)
        } else {
            orb(0x22A855F7.toInt(), baseX = 0.75f, baseY = 0.12f, r = 0.45f,
                t = t, speed = 1.0f, phase = 0f, driftX = 0.15f, driftY = 0.08f, driftScale = 0.15f)
            orb(0x18FF6B4A.toInt(), baseX = 0.15f, baseY = 0.70f, r = 0.38f,
                t = t, speed = 0.7f, phase = 120f, driftX = 0.12f, driftY = 0.10f, driftScale = 0.12f)
            orb(0x1560A5FA.toInt(), baseX = 0.60f, baseY = 0.40f, r = 0.30f,
                t = t, speed = 0.5f, phase = 240f, driftX = 0.10f, driftY = 0.15f, driftScale = 0.10f)
        }
    }
}

private fun DrawScope.orb(
    color: Int,
    baseX: Float,
    baseY: Float,
    r: Float,
    t: Float,
    speed: Float,
    phase: Float,
    driftX: Float,
    driftY: Float,
    driftScale: Float
) {
    val rad = Math.toRadians((t * speed + phase).toDouble())
    val scale = 1f + sin(rad * 0.5).toFloat() * driftScale
    val cx = size.width * baseX + sin(rad).toFloat() * size.width * driftX
    val cy = size.height * baseY + cos(rad).toFloat() * size.height * driftY
    val radius = size.minDimension * r * scale
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(color), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )
}

/**
 * The exact 4.1.7 ring recipe as a Modifier.
 *
 * L1 rim      : 0.75dp edge ring, 315° gradient #A6FFFFFF→#14FFFFFF→#40FFFFFF
 * L2 plate    : inset 0.75dp, [plateColor] (glass_plate), radius = R − 0.75dp
 * L3 body     : inset 0.75dp, vertical gradient (top [bodyTop] → bottom [bodyBottom])
 */
fun Modifier.newsGlassSurface(
    cornerRadius: Dp,
    plateColor: Color,
    bodyTop: Color,
    bodyBottom: Color
): Modifier = drawBehind {
    val outer = cornerRadius.toPx()
    val inset = 0.75.dp.toPx()
    val inner = (outer - inset).coerceAtLeast(0f)
    val innerSize = Size(size.width - inset * 2f, size.height - inset * 2f)
    val innerTopLeft = Offset(inset, inset)

    // L1 — rim gradient across the full bounds; only the outer 0.75dp ring
    // stays visible once the plate and body layers cover the middle.
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xA6FFFFFF),
                Color(0x14FFFFFF),
                Color(0x40FFFFFF)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        cornerRadius = CornerRadius(outer)
    )
    // L2 — near-opaque plate (55% in dark mode: clear glass, orbs show through)
    drawRoundRect(
        color = plateColor,
        topLeft = innerTopLeft,
        size = innerSize,
        cornerRadius = CornerRadius(inner)
    )
    // L3 — frosted translucent body (Android angle=90: start bottom → end top,
    // replicated as Compose vertical top→bottom).
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(bodyTop, bodyBottom),
            startY = 0f,
            endY = size.height
        ),
        topLeft = innerTopLeft,
        size = innerSize,
        cornerRadius = CornerRadius(inner)
    )
}

@Composable
fun newsGlassSurfaceStyle(cornerRadius: Dp, body: GlassBody = GlassBody.Card): Modifier {
    val colors = LocalNewsColors.current
    val bodyTop = if (body == GlassBody.Card) Color(0x0DFFFFFF) else Color(0x08FFFFFF)
    val bodyBottom = if (body == GlassBody.Card) Color(0x1FFFFFFF) else Color(0x14FFFFFF)
    return Modifier.newsGlassSurface(cornerRadius, colors.glassPlate, bodyTop, bodyBottom)
}

/** Real-time frost — kept for API compatibility; the 4.1.7 look uses no blur. */
fun Modifier.newsGlassFrost(state: HazeState?): Modifier = this

/** Chrome hairline border resolved per mode. */
@Composable
fun newsChromeBorder(): Color = LocalNewsColors.current.glassBorder

/**
 * Description text block on a slightly-opaque scrim. Body copy never sits
 * directly on raw glass: the scrim guarantees contrast in both modes.
 */
@Composable
fun NewsGlassDesc(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val colors = LocalNewsColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (colors.isLight) Color(0x99FFFFFF) else Color(0x8C050510))
            .border(1.dp, newsChromeBorder(), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.Text(
            text,
            color = colors.textSecondary,
            fontSize = if (!fontSize.isUnspecified) fontSize else 13.sp,
            lineHeight = if (!fontSize.isUnspecified) fontSize * 1.45f else 18.9.sp
        )
    }
}

/**
 * Story card / settings section card: the exact story_card_bg recipe
 * (20dp default radius, plate + #1F→#0D body under a 0.75dp rim ring).
 */
@Composable
fun NewsGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalNewsColors.current
    val bodyTop = if (colors.isLight) Color(0x0DFFFFFF) else Color(0x0DFFFFFF)
    val bodyBottom = Color(0x1FFFFFFF)
    Column(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .newsGlassSurface(cornerRadius, colors.glassPlate, bodyTop, bodyBottom)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Chrome bar (header 20dp / bottom tab bar 24dp): the tabbar_bg recipe
 * (plate + #14→#08 body under a 0.75dp rim ring).
 */
@Composable
fun NewsGlassPill(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalNewsColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .newsGlassSurface(
                cornerRadius,
                colors.glassPlate,
                bodyTop = Color(0x08FFFFFF),
                bodyBottom = Color(0x14FFFFFF)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Small frosted icon button (header_btn_bg recipe: 10dp ring recipe).
 */
@Composable
fun NewsGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalNewsColors.current
    Box(
        modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .newsGlassSurface(
                10.dp,
                colors.glassPlate,
                bodyTop = Color(0x08FFFFFF),
                bodyBottom = Color(0x14FFFFFF)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Inline chip (chip_bg): 10dp radius, #0AFFFFFF fill, 1dp #14FFFFFF stroke.
 * Selected variant (chip_bg_selected): #22FF6B4A fill, 1dp #30FF6B4A stroke.
 */
@Composable
fun NewsGlassChip(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalNewsColors.current
    val fill = if (selected) Color(0x22FF6B4A) else Color(0x0AFFFFFF)
    val stroke = if (selected) Color(0x30FF6B4A) else Color(0x14FFFFFF)
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .border(1.dp, stroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Time chip (time_chip_bg): 8dp radius, glass_bg fill, no stroke.
 */
@Composable
fun NewsTimeChip(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit(9f, TextUnitType.Sp),
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp
) {
    val colors = LocalNewsColors.current
    androidx.compose.material3.Text(
        text,
        fontSize = fontSize,
        color = colors.textTertiary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.glassBg)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    )
}
