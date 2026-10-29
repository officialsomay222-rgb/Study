package com.example.ui.pdf

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Refined matte-satin glass button with clean borders and zero visual noise.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = text ?: "Action Button",
    enabled: Boolean = true,
    accentColor: Color = Color(0xFF38BDF8),
    isPill: Boolean = false,
    isActive: Boolean = false
) {
    val alphaMultiplier = if (enabled) 1f else 0.42f
    val shape = if (isPill) RoundedCornerShape(22.dp) else CircleShape
    val effectiveAccent = if (isActive) Color(0xFF38BDF8) else accentColor
    val bgBrush = androidx.compose.runtime.remember(isActive, alphaMultiplier) {
        Brush.verticalGradient(
            colors = listOf(
                if (isActive) Color(0xFF1E324E).copy(alpha = 0.96f) else Color(0xFF1A2234).copy(alpha = 0.94f * alphaMultiplier),
                if (isActive) Color(0xFF111C2E).copy(alpha = 0.98f) else Color(0xFF0E1422).copy(alpha = 0.98f * alphaMultiplier)
            )
        )
    }

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        shadowElevation = if (isActive) 4.dp else 2.dp,
        enabled = enabled
    ) {
        Box(
            modifier = Modifier
                .background(bgBrush)
                .border(
                    BorderStroke(
                        1.dp,
                        if (isActive) effectiveAccent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.18f * alphaMultiplier)
                    ),
                    shape = shape
                )
                .padding(
                    horizontal = if (text != null && icon != null) 14.dp else if (text != null) 12.dp else 10.dp,
                    vertical = 10.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = if (isActive) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.92f * alphaMultiplier),
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (text != null) {
                    if (icon != null) Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        color = if (isActive) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.95f * alphaMultiplier),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Ultra-Lightweight Minimalist Floating Control Ball anchored in the bottom-right corner.
 * Optimized for 120fps on low-end devices with zero overdraw and no sparking lights.
 */
@Composable
fun LiquidGlassOrbButton(
    onClick: () -> Unit,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    pageText: String? = null,
    accentColor: Color = Color(0xFF38BDF8)
) {
    val iconRotation by animateFloatAsState(
        targetValue = if (isOpen) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconRotation"
    )

    val orbBrush = androidx.compose.runtime.remember(isOpen) {
        Brush.verticalGradient(
            listOf(
                if (isOpen) Color(0xFF22314A) else Color(0xFF1E293B),
                if (isOpen) Color(0xFF111A2B) else Color(0xFF0F172A)
            )
        )
    }

    val badgeBrush = androidx.compose.runtime.remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF1B2436),
                Color(0xFF0E1420)
            )
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier
    ) {
        // Companion page counter badge (only shown in PDF reader when closed)
        if (pageText != null && !isOpen) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(badgeBrush)
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = pageText,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Lightweight Matte-Obsidian Floating Ball (No heavy shadow layers or sparking lights)
        Surface(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 6.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(orbBrush)
                    .border(
                        BorderStroke(
                            1.2.dp,
                            if (isOpen) accentColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.26f)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOpen) accentColor.copy(alpha = 0.14f)
                            else Color.White.copy(alpha = 0.05f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Tune,
                        contentDescription = if (isOpen) "Close Controls" else "Open Controls",
                        tint = if (isOpen) accentColor else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(iconRotation)
                    )
                }
            }
        }
    }
}

/**
 * Clean Frosted Backdrop that adapts to the active PDF Theme.
 */
@Composable
fun LiquidGlassBackdrop(
    paperTheme: PaperTheme,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrimBrush = when (paperTheme) {
        PaperTheme.CLEAN_WHITE -> Brush.verticalGradient(
            listOf(
                Color(0xFF0F172A).copy(alpha = 0.35f),
                Color(0xFF090D16).copy(alpha = 0.55f)
            )
        )
        PaperTheme.WARM_SEPIA -> Brush.verticalGradient(
            listOf(
                Color(0xFF241810).copy(alpha = 0.55f),
                Color(0xFF150E09).copy(alpha = 0.75f)
            )
        )
        PaperTheme.DARK_MODE -> Brush.verticalGradient(
            listOf(
                Color(0xFF070B14).copy(alpha = 0.65f),
                Color(0xFF000000).copy(alpha = 0.82f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimBrush)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
    )
}
