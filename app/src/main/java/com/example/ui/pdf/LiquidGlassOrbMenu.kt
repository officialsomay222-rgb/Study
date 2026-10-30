package com.example.ui.pdf

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
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Refined & Enhanced Control Panel revealed when the PDF Opener floating ball is tapped.
 * Designed with clean matte-obsidian aesthetics and zero sparkling icons.
 */
@Composable
fun LiquidGlassOrbMenuCard(
    currentPageIndex: Int = 0,
    pageCount: Int = 1,
    viewMode: PdfViewMode = PdfViewMode.CONTINUOUS_FLOW,
    paperTheme: PaperTheme = PaperTheme.CLEAN_WHITE,
    zoomScale: Float = 1f,
    isScrubbing: Boolean = false,
    scrubbedPage: Int = 0,
    resolvedTitle: String = "",
    onScrubChange: (Int) -> Unit = {},
    onScrubFinished: (Int) -> Unit = {},
    onQuickJumpClick: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onToggleViewMode: () -> Unit = {},
    onCycleTheme: () -> Unit = {},
    onResetZoom: () -> Unit = {},
    onJumpPages: (() -> Unit)? = null,
    onSelectTheme: ((PaperTheme) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activePageDisplay = if (isScrubbing) scrubbedPage + 1 else currentPageIndex + 1
    val zoomPercent = (zoomScale * 100).roundToInt()

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = modifier
            .widthIn(max = 440.dp)
            .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = Color.Black.copy(alpha = 0.75f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1B2436).copy(alpha = 0.97f),
                            Color(0xFF101726).copy(alpha = 0.99f),
                            Color(0xFF0A0F1A)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume clicks inside menu card */ }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. HEADER ROW: Quick Jump Pill + Document Info + Close Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        onClick = onQuickJumpClick,
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF38BDF8).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.38f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Page $activePageDisplay / $pageCount",
                                color = Color(0xFFF8FAFC),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Jump to page",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Text(
                        text = if (zoomScale >= 1.55f) "$zoomPercent% · Ultra-HD"
                        else "$zoomPercent% · ${if (viewMode == PdfViewMode.CONTINUOUS_FLOW) "Flow Mode" else "Swipe Mode"}",
                        color = if (zoomScale >= 1.55f) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Surface(
                        onClick = onClose,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Controls",
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 2. PAGE SCRUBBER CARD WITH FIRST / LAST QUICK JUMP
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.10f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "1",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onScrubFinished(0) }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        val sliderVal = if (isScrubbing) scrubbedPage.toFloat() else currentPageIndex.toFloat()
                        val maxVal = max(1, pageCount - 1).toFloat()

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
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF38BDF8),
                                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "$pageCount",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onScrubFinished(max(0, pageCount - 1)) }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }

                // 3. DIRECT 3-COLUMN PAPER THEME SELECTOR (Original / Sepia / Dark)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PdfThemeChip(
                        icon = Icons.Default.LightMode,
                        label = "Original",
                        selected = paperTheme == PaperTheme.CLEAN_WHITE,
                        onClick = {
                            if (onSelectTheme != null) onSelectTheme(PaperTheme.CLEAN_WHITE)
                            else onCycleTheme()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PdfThemeChip(
                        icon = Icons.Default.MenuBook,
                        label = "Sepia",
                        selected = paperTheme == PaperTheme.WARM_SEPIA,
                        onClick = {
                            if (onSelectTheme != null) onSelectTheme(PaperTheme.WARM_SEPIA)
                            else onCycleTheme()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PdfThemeChip(
                        icon = Icons.Default.DarkMode,
                        label = "Night",
                        selected = paperTheme == PaperTheme.DARK_MODE,
                        onClick = {
                            if (onSelectTheme != null) onSelectTheme(PaperTheme.DARK_MODE)
                            else onCycleTheme()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. NAVIGATION & VIEW MODE CONTROLS ROW
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiquidGlassButton(
                        onClick = onPrevPage,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Page",
                        enabled = currentPageIndex > 0,
                        accentColor = Color(0xFF38BDF8)
                    )

                    LiquidGlassButton(
                        onClick = onToggleViewMode,
                        icon = if (viewMode == PdfViewMode.PAGE_SWIPE) Icons.Default.ViewCarousel else Icons.Default.ViewStream,
                        text = if (viewMode == PdfViewMode.PAGE_SWIPE) "Swipe Mode" else "Flow Mode",
                        contentDescription = "Toggle View Mode",
                        accentColor = Color(0xFF38BDF8),
                        isPill = true,
                        modifier = Modifier.weight(1f)
                    )

                    LiquidGlassButton(
                        onClick = onResetZoom,
                        text = if (abs(zoomScale - 1f) > 0.04f) "Reset 1x" else "100%",
                        icon = Icons.Default.FitScreen,
                        contentDescription = "Reset Zoom",
                        accentColor = Color(0xFF38BDF8),
                        isPill = true,
                        isActive = abs(zoomScale - 1f) > 0.04f
                    )

                    LiquidGlassButton(
                        onClick = onNextPage,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Page",
                        enabled = currentPageIndex < pageCount - 1,
                        accentColor = Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfThemeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF38BDF8).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            if (selected) 1.1.dp else 0.8.dp,
            if (selected) Color(0xFF38BDF8).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f)
        ),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) Color(0xFFF8FAFC) else Color(0xFFCBD5E1),
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun LiquidGlassOrbMenu(
    currentPageIndex: Int = 0,
    pageCount: Int = 1,
    viewMode: PdfViewMode = PdfViewMode.CONTINUOUS_FLOW,
    paperTheme: PaperTheme = PaperTheme.CLEAN_WHITE,
    zoomScale: Float = 1f,
    isScrubbing: Boolean = false,
    scrubbedPage: Int = 0,
    resolvedTitle: String = "",
    onScrubChange: (Int) -> Unit = {},
    onScrubFinished: (Int) -> Unit = {},
    onQuickJumpClick: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onToggleViewMode: () -> Unit = {},
    onCycleTheme: () -> Unit = {},
    onResetZoom: () -> Unit = {},
    onJumpPages: (() -> Unit)? = null,
    onSelectTheme: ((PaperTheme) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LiquidGlassOrbMenuCard(
        currentPageIndex = currentPageIndex,
        pageCount = pageCount,
        viewMode = viewMode,
        paperTheme = paperTheme,
        zoomScale = zoomScale,
        isScrubbing = isScrubbing,
        scrubbedPage = scrubbedPage,
        resolvedTitle = resolvedTitle,
        onScrubChange = onScrubChange,
        onScrubFinished = onScrubFinished,
        onQuickJumpClick = onQuickJumpClick,
        onPrevPage = onPrevPage,
        onNextPage = onNextPage,
        onToggleViewMode = onToggleViewMode,
        onCycleTheme = onCycleTheme,
        onResetZoom = onResetZoom,
        onJumpPages = onJumpPages,
        onSelectTheme = onSelectTheme,
        onShare = onShare,
        onDownload = onDownload,
        onClose = onClose,
        modifier = modifier
    )
}
