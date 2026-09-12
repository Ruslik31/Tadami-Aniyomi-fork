package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import eu.kanade.presentation.easteregg.aurora.rememberAuroraReducedMotion
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.launch
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

internal const val HEATMAP_COLUMNS = 53
internal const val HEATMAP_ROWS = 7
internal const val HEATMAP_MONTH_LABEL_MIN_COLUMN_GAP = 4

private const val HEATMAP_STAGGER_DURATION_MS = 300
private const val HEATMAP_STAGGER_SPREAD = 0.34f

internal data class HeatmapCell(
    val date: LocalDate,
    val level: Int,
    val isToday: Boolean,
)

internal data class HeatmapMonthLabel(
    val column: Int,
    val month: YearMonth,
)

internal data class HeatmapGrid(
    val columns: List<List<HeatmapCell?>>,
    val monthLabels: List<HeatmapMonthLabel>,
)

internal fun buildHeatmapGrid(
    activityData: List<DayActivity>,
    today: LocalDate,
): HeatmapGrid {
    val levelByDate = activityData.associate { it.date to it.level }
    val gridStart = today
        .minusDays((today.dayOfWeek.value - 1).toLong())
        .minusWeeks(HEATMAP_COLUMNS - 1L)

    val columns = (0 until HEATMAP_COLUMNS).map { column ->
        (0 until HEATMAP_ROWS).map { row ->
            val date = gridStart.plusDays(column * 7L + row)
            if (date.isAfter(today)) {
                null
            } else {
                HeatmapCell(
                    date = date,
                    level = (levelByDate[date] ?: 0).coerceIn(0, 4),
                    isToday = date == today,
                )
            }
        }
    }

    val monthLabels = buildList {
        var lastLabelColumn = -HEATMAP_MONTH_LABEL_MIN_COLUMN_GAP
        for (column in 0 until HEATMAP_COLUMNS) {
            if (column - lastLabelColumn < HEATMAP_MONTH_LABEL_MIN_COLUMN_GAP) continue
            for (row in 0 until HEATMAP_ROWS) {
                val date = gridStart.plusDays(column * 7L + row)
                if (date.isAfter(today)) break
                if (date.dayOfMonth == 1) {
                    add(HeatmapMonthLabel(column = column, month = YearMonth.from(date)))
                    lastLabelColumn = column
                    break
                }
            }
        }
    }

    return HeatmapGrid(columns = columns, monthLabels = monthLabels)
}

internal fun heatmapLevelColor(
    level: Int,
    isEInk: Boolean,
    isDark: Boolean,
    accent: Color,
    foreground: Color,
): Color {
    if (isEInk) {
        return when (level) {
            0 -> if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            1 -> foreground.copy(alpha = 0.12f)
            2 -> foreground.copy(alpha = 0.30f)
            3 -> foreground.copy(alpha = 0.55f)
            else -> foreground.copy(alpha = 0.85f)
        }
    }
    return when (level) {
        0 -> if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.06f)
        1 -> accent.copy(alpha = 0.25f)
        2 -> accent.copy(alpha = 0.45f)
        3 -> accent.copy(alpha = 0.70f)
        else -> accent.copy(alpha = 1f)
    }
}

internal fun formatHeatmapDayLabel(date: LocalDate, locale: Locale): String {
    return "${date.dayOfMonth} ${formatMonthShortLabel(YearMonth.from(date), locale)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementHeatmapCard(
    activityData: List<DayActivity>,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    val scope = rememberCoroutineScope()
    val animated = !rememberAuroraReducedMotion() && !colors.isEInk

    val today = remember { LocalDate.now() }
    val grid = remember(activityData, today) { buildHeatmapGrid(activityData, today) }

    var animationStarted by remember { mutableStateOf(!animated) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = HEATMAP_STAGGER_DURATION_MS),
        label = "heatmap_stagger",
    )
    LaunchedEffect(grid) { animationStarted = true }

    var selectedCell by remember { mutableStateOf<HeatmapCell?>(null) }
    var selectedCellBounds by remember { mutableStateOf<IntRect?>(null) }
    val tooltipState = rememberTooltipState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Заголовок с индикатором периода
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.achievement_heatmap_title).uppercase(),
                color = colors.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Text(
                text = stringResource(MR.strings.achievement_heatmap_period).uppercase(),
                color = colors.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
        }

        // Bento Shell Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.surface.copy(alpha = 0.5f),
                                colors.surface.copy(alpha = 0.3f),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
            ) {
                HeatmapBody(
                    grid = grid,
                    animationProgress = animationProgress,
                    locale = locale,
                    selectedCell = selectedCell,
                    selectedCellBounds = selectedCellBounds,
                    tooltipState = tooltipState,
                    onCellSelected = { cell, bounds ->
                        selectedCell = cell
                        selectedCellBounds = bounds
                        scope.launch { tooltipState.show() }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeatmapBody(
    grid: HeatmapGrid,
    animationProgress: Float,
    locale: Locale,
    selectedCell: HeatmapCell?,
    selectedCellBounds: IntRect?,
    tooltipState: TooltipState,
    onCellSelected: (HeatmapCell, IntRect) -> Unit,
) {
    val colors = AuroraTheme.colors
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val dayLabelWidth = 16.dp
        val bodyGap = 6.dp
        val cellGap = 2.dp
        val monthRowHeight = 12.dp
        val monthRowGap = 4.dp
        val cellSize = ((maxWidth - dayLabelWidth - bodyGap - cellGap * (HEATMAP_COLUMNS - 1)) / HEATMAP_COLUMNS)
            .coerceAtLeast(1.dp)
        val gridHeight = cellSize * HEATMAP_ROWS + cellGap * (HEATMAP_ROWS - 1)
        val weekdayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEEEE", locale) }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(bodyGap)) {
                // Подписи пн/ср/пт
                Column(modifier = Modifier.width(dayLabelWidth)) {
                    Spacer(modifier = Modifier.height(monthRowHeight + monthRowGap))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                    ) {
                        listOf(
                            0 to DayOfWeek.MONDAY,
                            2 to DayOfWeek.WEDNESDAY,
                            4 to DayOfWeek.FRIDAY,
                        ).forEach { (row, dayOfWeek) ->
                            Text(
                                text = weekdayFormatter.format(dayOfWeek),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(y = (cellSize + cellGap) * row + cellSize / 2 - 4.dp),
                                color = colors.textSecondary,
                                fontSize = 8.sp,
                                lineHeight = 8.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Подписи месяцев
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(monthRowHeight),
                    ) {
                        grid.monthLabels.forEach { label ->
                            val monthLabel = remember(label.month, locale) {
                                formatMonthShortLabel(label.month, locale)
                            }
                            Text(
                                text = monthLabel,
                                modifier = Modifier.offset(x = (cellSize + cellGap) * label.column),
                                color = colors.textSecondary,
                                fontSize = 8.sp,
                                lineHeight = 8.sp,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(monthRowGap))

                    TooltipBox(
                        positionProvider = rememberHeatmapTooltipPositionProvider(selectedCellBounds, density),
                        tooltip = {
                            PlainTooltip(
                                containerColor = colors.surface,
                                contentColor = colors.textPrimary,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.border(
                                    width = 1.dp,
                                    color = colors.divider,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            ) {
                                if (selectedCell != null) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = formatHeatmapDayLabel(selectedCell.date, locale),
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                        )
                                        if (selectedCell.level == 0) {
                                            Text(
                                                text = stringResource(MR.strings.achievement_no_activity),
                                                color = colors.textSecondary.copy(alpha = 0.7f),
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        state = tooltipState,
                        enableUserInput = false,
                    ) {
                        HeatmapCanvas(
                            grid = grid,
                            animationProgress = animationProgress,
                            cellSize = cellSize,
                            cellGap = cellGap,
                            gridHeight = gridHeight,
                            onCellSelected = onCellSelected,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Легенда «Меньше → Больше»
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MR.strings.achievement_heatmap_less),
                    color = colors.textSecondary,
                    fontSize = 8.sp,
                    letterSpacing = 0.2.sp,
                )
                val legendSquare = cellSize.coerceIn(4.dp, 9.dp)
                repeat(5) { level ->
                    Box(
                        modifier = Modifier
                            .size(legendSquare)
                            .background(
                                color = heatmapLevelColor(
                                    level = level,
                                    isEInk = colors.isEInk,
                                    isDark = colors.isDark,
                                    accent = colors.accent,
                                    foreground = colors.textPrimary,
                                ),
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                }
                Text(
                    text = stringResource(MR.strings.achievement_heatmap_more),
                    color = colors.textSecondary,
                    fontSize = 8.sp,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

@Composable
private fun HeatmapCanvas(
    grid: HeatmapGrid,
    animationProgress: Float,
    cellSize: Dp,
    cellGap: Dp,
    gridHeight: Dp,
    onCellSelected: (HeatmapCell, IntRect) -> Unit,
) {
    val colors = AuroraTheme.colors

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight)
            .pointerInput(grid, cellSize, cellGap) {
                val pitchPx = (cellSize + cellGap).toPx()
                val cellPx = cellSize.toPx()
                detectTapGestures { offset ->
                    val column = (offset.x / pitchPx).toInt()
                    val row = (offset.y / pitchPx).toInt()
                    val cell = grid.columns.getOrNull(column)?.getOrNull(row)
                    if (cell != null &&
                        offset.x - column * pitchPx <= cellPx &&
                        offset.y - row * pitchPx <= cellPx
                    ) {
                        onCellSelected(
                            cell,
                            IntRect(
                                offset = IntOffset(
                                    x = (column * pitchPx).roundToInt(),
                                    y = (row * pitchPx).roundToInt(),
                                ),
                                size = IntSize(cellPx.roundToInt(), cellPx.roundToInt()),
                            ),
                        )
                    }
                }
            },
    ) {
        val pitchPx = (cellSize + cellGap).toPx()
        val cellPx = cellSize.toPx()
        val cornerRadius = CornerRadius(min(2.dp.toPx(), cellPx / 3f))
        val cellArea = Size(cellPx, cellPx)

        grid.columns.forEachIndexed { column, rows ->
            val staggerStart = column.toFloat() / (HEATMAP_COLUMNS - 1) * HEATMAP_STAGGER_SPREAD
            val columnAlpha = ((animationProgress - staggerStart) / (1f - HEATMAP_STAGGER_SPREAD))
                .coerceIn(0f, 1f)
            if (columnAlpha <= 0f) return@forEachIndexed
            rows.forEachIndexed { row, cell ->
                if (cell == null) return@forEachIndexed
                val topLeft = Offset(column * pitchPx, row * pitchPx)
                val color = heatmapLevelColor(
                    level = cell.level,
                    isEInk = colors.isEInk,
                    isDark = colors.isDark,
                    accent = colors.accent,
                    foreground = colors.textPrimary,
                )
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * columnAlpha),
                    topLeft = topLeft,
                    size = cellArea,
                    cornerRadius = cornerRadius,
                )
                if (cell.isToday) {
                    drawRoundRect(
                        color = colors.accent.copy(alpha = columnAlpha),
                        topLeft = topLeft,
                        size = cellArea,
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberHeatmapTooltipPositionProvider(
    cellBounds: IntRect?,
    density: Density,
): PopupPositionProvider {
    return remember(cellBounds, density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val bounds = cellBounds ?: return IntOffset(anchorBounds.left, anchorBounds.top)
                val marginPx = with(density) { 8.dp.roundToPx() }
                val spacingPx = with(density) { 8.dp.roundToPx() }
                val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
                val x = (anchorBounds.left + bounds.center.x - popupContentSize.width / 2)
                    .coerceIn(marginPx, maxX)
                var y = anchorBounds.top + bounds.top - popupContentSize.height - spacingPx
                if (y < marginPx) {
                    y = anchorBounds.top + bounds.bottom + spacingPx
                }
                return IntOffset(x, y)
            }
        }
    }
}
