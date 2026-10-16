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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Clean & Ultra-Attractive Expanded Control Panel revealing all document controls
 * when the floating liquid glass orb is tapped.
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
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        modifier = modifier
            .widthIn(max = 480.dp)
            .shadow(32.dp, RoundedCornerShape(30.dp), spotColor = Color(0x6600E5FF))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color(0xFF141D35).copy(alpha = 0.88f),
                            Color(0xFF080D1D).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.85f),
                                Color(0xFF00E5FF).copy(alpha = 0.55f),
                                Color(0xFF7C4DFF).copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.20f)
                            )
                        )
                    ),
                    RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume clicks inside menu card */ }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // HEADER ROW: Page jump pill + Document title + Dismiss button
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
                        modifier = Modifier
                            .border(
                                BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
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
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Truncated title
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

                    // Close icon
                    LiquidGlassButton(
                        onClick = onClose,
                        icon = Icons.Default.Close,
                        contentDescription = "Close Controls",
                        accentColor = Color(0xFF00E5FF)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // SLIDER / SCRUBBER ROW
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

                Spacer(modifier = Modifier.height(10.dp))

                // ACTION BUTTONS ROW
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Previous Page Button
                    LiquidGlassButton(
                        onClick = onPrevPage,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Page",
                        enabled = currentPageIndex > 0,
                        accentColor = Color(0xFF2979FF)
                    )

                    // Swipe / Flow Toggle
                    LiquidGlassButton(
                        onClick = onToggleViewMode,
                        icon = if (viewMode == PdfViewMode.PAGE_SWIPE) Icons.Default.ViewCarousel else Icons.Default.ViewStream,
                        text = if (viewMode == PdfViewMode.PAGE_SWIPE) "Swipe" else "Flow",
                        contentDescription = "Toggle Mode",
                        accentColor = Color(0xFF00E5FF),
                        isPill = true
                    )

                    // Paper Theme
                    LiquidGlassButton(
                        onClick = onCycleTheme,
                        icon = when (paperTheme) {
                            PaperTheme.CLEAN_WHITE -> Icons.Default.LightMode
                            PaperTheme.WARM_SEPIA -> Icons.Default.AutoAwesome
                            PaperTheme.DARK_MODE -> Icons.Default.DarkMode
                        },
                        text = when (paperTheme) {
                            PaperTheme.CLEAN_WHITE -> "Normal"
                            PaperTheme.WARM_SEPIA -> "Sepia"
                            PaperTheme.DARK_MODE -> "Dark"
                        },
                        contentDescription = "Paper Theme",
                        accentColor = Color(0xFFFFA000),
                        isPill = true
                    )

                    // Zoom 1x Reset
                    LiquidGlassButton(
                        onClick = onResetZoom,
                        text = if (zoomScale > 1.05f) "${(zoomScale * 100).toInt()}%" else "1x",
                        icon = Icons.Default.FitScreen,
                        contentDescription = "Reset Zoom",
                        accentColor = Color(0xFF00E5FF),
                        isPill = true,
                        isActive = zoomScale > 1.05f
                    )

                    // Next Page Button
                    LiquidGlassButton(
                        onClick = onNextPage,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Page",
                        enabled = currentPageIndex < pageCount - 1,
                    )
                }
            }
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

