package com.example.ui.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Ultra-refined Liquid Glass Capsule control deck that blooms open when the bottom-right
 * floating glass orb is tapped. Features frosted glassmorphism, responsive scrubber,
 * instant steppers, segmented eye-care paper theme selector, and quick document actions.
 */
@Composable
fun LiquidGlassOrbMenuCard(
    currentPageIndex: Int,
    pageCount: Int,
    paperTheme: PaperTheme,
    zoomScale: Float,
    isScrubbing: Boolean,
    scrubbedPage: Int,
    resolvedTitle: String,
    onScrubChange: (Int) -> Unit,
    onScrubFinished: (Int) -> Unit,
    onQuickJumpClick: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpPages: (Int) -> Unit,
    onSelectTheme: (PaperTheme) -> Unit,
    onResetZoom: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = modifier
            .widthIn(max = 500.dp)
            .shadow(
                elevation = 32.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color(0x5500E5FF),
                spotColor = Color(0x7700E5FF)
            )
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF22304E).copy(alpha = 0.95f),
                            Color(0xFF11192D).copy(alpha = 0.96f),
                            Color(0xFF070B16).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.85f),
                                Color(0xFF00E5FF).copy(alpha = 0.65f),
                                Color(0xFF7C4DFF).copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.20f)
                            )
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume internal clicks */ }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ROW 1: HEADER (Page badge + Title + Close button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Quick Jump Pill
                    Surface(
                        onClick = onQuickJumpClick,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.border(
                            BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                            RoundedCornerShape(16.dp)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Page ${currentPageIndex + 1} of $pageCount",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Jump to page",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Document Title
                    Text(
                        text = resolvedTitle,
                        color = Color(0xFF90CAF9),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    )

                    // Close Capsule Button
                    LiquidGlassButton(
                        onClick = onClose,
                        icon = Icons.Default.Close,
                        contentDescription = "Close Capsule",
                        accentColor = Color(0xFF00E5FF)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ROW 2: SCRUBBER SEEK BAR & REALTIME STATUS
                val displayPage = if (isScrubbing) scrubbedPage else currentPageIndex
                val sliderVal = displayPage.toFloat()
                val maxVal = max(1, pageCount - 1).toFloat()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "1",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Slider(
                        value = sliderVal,
                        onValueChange = { newVal ->
                            onScrubChange(newVal.toInt().coerceIn(0, pageCount - 1))
                        },
                        onValueChangeFinished = {
                            onScrubFinished(scrubbedPage.coerceIn(0, pageCount - 1))
                        },
                        valueRange = 0f..maxVal,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "$pageCount",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // STEPPER NAVIGATION BUTTONS (-5, Prev, Next, +5)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                ) {
                    // Jump -5
                    CapsuleMicroButton(
                        onClick = { onJumpPages(-5) },
                        icon = Icons.Default.FastRewind,
                        text = "-5",
                        enabled = currentPageIndex > 0
                    )

                    // Previous Page
                    CapsuleMicroButton(
                        onClick = onPrevPage,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        text = "Prev",
                        enabled = currentPageIndex > 0
                    )

                    // Next Page
                    CapsuleMicroButton(
                        onClick = onNextPage,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        text = "Next",
                        enabled = currentPageIndex < pageCount - 1
                    )

                    // Jump +5
                    CapsuleMicroButton(
                        onClick = { onJumpPages(5) },
                        icon = Icons.Default.FastForward,
                        text = "+5",
                        enabled = currentPageIndex < pageCount - 1
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ROW 3: REFINED CONTROLS (THEME SEGMENTS + ZOOM + ACTIONS)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Segmented Theme Selector (Light | Sepia | Dark)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier.border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                            RoundedCornerShape(20.dp)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            ThemeSegment(
                                label = "Light",
                                icon = Icons.Default.LightMode,
                                isSelected = paperTheme == PaperTheme.CLEAN_WHITE,
                                onClick = { onSelectTheme(PaperTheme.CLEAN_WHITE) }
                            )
                            ThemeSegment(
                                label = "Sepia",
                                icon = Icons.Default.AutoAwesome,
                                isSelected = paperTheme == PaperTheme.WARM_SEPIA,
                                onClick = { onSelectTheme(PaperTheme.WARM_SEPIA) }
                            )
                            ThemeSegment(
                                label = "Dark",
                                icon = Icons.Default.DarkMode,
                                isSelected = paperTheme == PaperTheme.DARK_MODE,
                                onClick = { onSelectTheme(PaperTheme.DARK_MODE) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Zoom Reset / 1x Indicator
                    LiquidGlassButton(
                        onClick = onResetZoom,
                        text = if (zoomScale > 1.05f) "${(zoomScale * 100).toInt()}%" else "1x",
                        icon = Icons.Default.FitScreen,
                        contentDescription = "Reset Zoom to 1x",
                        accentColor = Color(0xFF00E5FF),
                        isPill = true,
                        isActive = zoomScale > 1.05f
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Share Button
                    LiquidGlassButton(
                        onClick = onShare,
                        icon = Icons.Default.Share,
                        contentDescription = "Share PDF",
                        accentColor = Color(0xFF7C4DFF)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Save / Download Button
                    LiquidGlassButton(
                        onClick = onDownload,
                        icon = Icons.Default.FileDownload,
                        contentDescription = "Save PDF to Downloads",
                        accentColor = Color(0xFF00E676)
                    )
                }
            }
        }
    }
}

/**
 * Compact stepper button within the capsule scrubber controls.
 */
@Composable
private fun CapsuleMicroButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    enabled: Boolean
) {
    val alpha = if (enabled) 1f else 0.35f
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = if (enabled) 0.12f else 0.05f),
        enabled = enabled,
        modifier = Modifier.border(
            BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.25f else 0.10f)),
            RoundedCornerShape(14.dp)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color(0xFF00E5FF).copy(alpha = alpha),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = alpha),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Single segmented theme option inside the capsule theme bar.
 */
@Composable
private fun ThemeSegment(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.Transparent,
        modifier = if (isSelected) {
            Modifier.border(
                BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.85f)),
                RoundedCornerShape(16.dp)
            )
        } else {
            Modifier
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.70f),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
