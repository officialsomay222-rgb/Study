package com.example.ui.pdf

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import kotlinx.coroutines.delay

/**
 * Ultra-premium Liquid Glass button with specular rim, translucent frosted body,
 * and glowing neon accents.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = text ?: "Action Button",
    enabled: Boolean = true,
    accentColor: Color = Color(0xFF00E5FF),
    isPill: Boolean = false,
    isActive: Boolean = false
) {
    val alphaMultiplier = if (enabled) 1f else 0.4f
    val shape = if (isPill) RoundedCornerShape(24.dp) else CircleShape
    val effectiveAccent = if (isActive) Color(0xFF00E5FF) else accentColor

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .shadow(
                elevation = if (isActive) 12.dp else 6.dp,
                shape = shape,
                ambientColor = effectiveAccent.copy(alpha = if (isActive) 0.45f else 0.2f),
                spotColor = effectiveAccent.copy(alpha = if (isActive) 0.6f else 0.3f)
            ),
        shape = shape,
        color = Color.Transparent,
        enabled = enabled
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (isActive) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.20f * alphaMultiplier),
                            if (isActive) Color(0xFF1E3A5F).copy(alpha = 0.75f) else Color(0xFF161F36).copy(alpha = 0.65f * alphaMultiplier),
                            if (isActive) Color(0xFF0C192E).copy(alpha = 0.95f) else Color(0xFF0A0F1E).copy(alpha = 0.90f * alphaMultiplier)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        if (isActive) 1.5.dp else 1.1.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isActive) 0.85f else 0.65f * alphaMultiplier),
                                effectiveAccent.copy(alpha = if (isActive) 0.8f else 0.4f * alphaMultiplier),
                                Color.White.copy(alpha = 0.15f * alphaMultiplier)
                            )
                        )
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
                        tint = if (isActive) Color(0xFF00E5FF) else Color.White.copy(alpha = alphaMultiplier),
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (text != null) {
                    if (icon != null) Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        color = if (isActive) Color(0xFF00E5FF) else Color.White.copy(alpha = alphaMultiplier),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Refined Minimalist Floating Liquid Glass Orb (Ball) anchored in bottom-right corner.
 * Features ultra-smooth spring physics, liquid ripple wave on opening, rotating morph icon,
 * and high-end 3D specular glass highlights.
 */
@Composable
fun LiquidGlassOrbButton(
    onClick: () -> Unit,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    pageText: String? = null,
    accentColor: Color = Color(0xFF00E5FF)
) {
    // Ambient breathing pulse when closed
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Fluid spring rotation for the icon (0 deg when closed -> 180 deg when open)
    val iconRotation by animateFloatAsState(
        targetValue = if (isOpen) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "iconRotation"
    )

    // Ball scale animation when opened vs closed
    val orbScale by animateFloatAsState(
        targetValue = if (isOpen) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "orbScale"
    )

    // Expanding liquid ripple ring effect when opening
    var rippleScale by remember { mutableFloatStateOf(0f) }
    var rippleAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            rippleScale = 0.8f
            rippleAlpha = 0.8f
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 400) {
                val fraction = (System.currentTimeMillis() - startTime) / 400f
                rippleScale = 0.8f + (fraction * 1.5f)
                rippleAlpha = (1f - fraction) * 0.75f
                delay(16)
            }
            rippleAlpha = 0f
        } else {
            rippleAlpha = 0f
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier
    ) {
        // Minimalist companion glass page badge
        if (pageText != null && !isOpen) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = accentColor.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.24f),
                                    Color(0xFF131A2D).copy(alpha = 0.85f),
                                    Color(0xFF080D1A).copy(alpha = 0.94f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                1.1.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.65f),
                                        accentColor.copy(alpha = 0.45f)
                                    )
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = pageText,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // The Floating Glass Orb / Ball
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp)
        ) {
            // Liquid ripple shockwave on opening
            if (rippleAlpha > 0.02f) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(rippleScale)
                        .border(
                            BorderStroke(2.dp, accentColor.copy(alpha = rippleAlpha)),
                            CircleShape
                        )
                )
            }

            // Ambient glowing aura
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(if (isOpen) orbScale * 1.1f else pulseScale)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accentColor.copy(alpha = if (isOpen) 0.55f else 0.28f),
                                Color(0xFF7C4DFF).copy(alpha = if (isOpen) 0.35f else 0.15f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            // Glass sphere surface with 3D specular highlight
            Surface(
                onClick = onClick,
                modifier = Modifier
                    .size(56.dp)
                    .scale(orbScale)
                    .shadow(
                        elevation = if (isOpen) 18.dp else 12.dp,
                        shape = CircleShape,
                        ambientColor = accentColor.copy(alpha = if (isOpen) 0.55f else 0.35f),
                        spotColor = accentColor.copy(alpha = if (isOpen) 0.75f else 0.5f)
                    ),
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (isOpen) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.35f),
                                    if (isOpen) Color(0xFF1E4273).copy(alpha = 0.90f) else Color(0xFF15223D).copy(alpha = 0.85f),
                                    if (isOpen) Color(0xFF0C1B33).copy(alpha = 0.98f) else Color(0xFF090E1B).copy(alpha = 0.95f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                if (isOpen) 2.dp else 1.5.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.95f),
                                        accentColor.copy(alpha = if (isOpen) 1f else 0.85f),
                                        Color(0xFF7C4DFF).copy(alpha = 0.70f),
                                        Color.White.copy(alpha = 0.35f),
                                        Color.White.copy(alpha = 0.95f)
                                    )
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Tune,
                        contentDescription = if (isOpen) "Close Controls" else "Open Controls",
                        tint = if (isOpen) Color(0xFF00E5FF) else Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(iconRotation)
                    )
                }
            }
        }
    }
}

/**
 * Attractive Frosted Glass Blur Backdrop that adapts black or white styling
 * according to the active PDF Theme.
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
                Color.White.copy(alpha = 0.55f),
                Color(0xFFE8EEF5).copy(alpha = 0.65f),
                Color(0xFF090D1A).copy(alpha = 0.58f)
            )
        )
        PaperTheme.WARM_SEPIA -> Brush.verticalGradient(
            listOf(
                Color(0xFF38291E).copy(alpha = 0.68f),
                Color(0xFF241810).copy(alpha = 0.80f),
                Color(0xFF150E09).copy(alpha = 0.88f)
            )
        )
        PaperTheme.DARK_MODE -> Brush.verticalGradient(
            listOf(
                Color(0xFF070B14).copy(alpha = 0.78f),
                Color(0xFF000000).copy(alpha = 0.88f),
                Color(0xFF000000).copy(alpha = 0.94f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimBrush)
            .blur(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
    )
}
