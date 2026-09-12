package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

internal const val HEATMAP_COLUMNS = 53
internal const val HEATMAP_ROWS = 7
internal const val HEATMAP_MONTH_LABEL_MIN_COLUMN_GAP = 4
internal const val HEATMAP_MONTH_LABEL_DISPLAY_MIN_COLUMN_GAP = 6

private const val HEATMAP_STAGGER_DURATION_MS = 300
private const val HEATMAP_STAGGER_SPREAD = 0.34f
private const val HEATMAP_SCROLL_END_THRESHOLD_PX = 2

// Геометрия V1 «Скролл-год» из прототипа: проба 9dp, шаг 12dp, без fit-to-width
private val HEATMAP_CELL_SIZE = 9.dp
private val HEATMAP_CELL_GAP = 3.dp
private val HEATMAP_CELL_PITCH = HEATMAP_CELL_SIZE + HEATMAP_CELL_GAP
private val HEATMAP_DAY_LABEL_WIDTH = 20.dp
private val HEATMAP_DAY_LABEL_GAP = 6.dp
private val HEATMAP_MONTH_ROW_HEIGHT = 16.dp
private val HEATMAP_MONTH_ROW_GAP = 4.dp
private val HEATMAP_GRID_WIDTH = HEATMAP_CELL_PITCH * HEATMAP_COLUMNS
private val HEATMAP_GRID_HEIGHT = HEATMAP_CELL_PITCH * HEATMAP_ROWS
private val HEATMAP_FADE_WIDTH = 24.dp

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

internal data class HeatmapSummary(
    val activeDays: Int,
    val currentStreak: Int,
    val bestStreak: Int,
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

/**
 * Прореживание ряда меток месяцев для отображения: в скролл-годе при pitch 12dp
 * подписи 9.5sp требуют min-gap 6 колонок (прототип V1), тогда как
 * [buildHeatmapGrid] хранит полный набор с min-gap [HEATMAP_MONTH_LABEL_MIN_COLUMN_GAP].
 */
internal fun visibleMonthLabels(
    labels: List<HeatmapMonthLabel>,
    minColumnGap: Int = HEATMAP_MONTH_LABEL_DISPLAY_MIN_COLUMN_GAP,
): List<HeatmapMonthLabel> = buildList {
    var lastColumn: Int? = null
    labels.forEach { label ->
        val last = lastColumn
        if (last == null || label.column - last >= minColumnGap) {
            add(label)
            lastColumn = label.column
        }
    }
}

internal fun computeHeatmapSummary(
    activityData: List<DayActivity>,
    today: LocalDate = LocalDate.now(),
): HeatmapSummary {
    val levelByDate = activityData.associate { it.date to it.level }
    val windowStart = today.minusDays(364)

    var activeDays = 0
    var bestStreak = 0
    var run = 0
    var date = windowStart
    while (!date.isAfter(today)) {
        if ((levelByDate[date] ?: 0) > 0) {
            activeDays++
            run++
            if (run > bestStreak) bestStreak = run
        } else {
            run = 0
        }
        date = date.plusDays(1)
    }

    var currentStreak = 0
    var cursor = today
    while (!cursor.isBefore(windowStart) && (levelByDate[cursor] ?: 0) > 0) {
        currentStreak++
        cursor = cursor.minusDays(1)
    }

    return HeatmapSummary(
        activeDays = activeDays,
        currentStreak = currentStreak,
        bestStreak = bestStreak,
    )
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
            0 -> if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
            1 -> foreground.copy(alpha = 0.16f)
            2 -> foreground.copy(alpha = 0.34f)
            3 -> foreground.copy(alpha = 0.60f)
            else -> foreground.copy(alpha = 0.88f)
        }
    }
    return when (level) {
        0 -> if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
        1 -> accent.copy(alpha = 0.35f)
        2 -> accent.copy(alpha = 0.55f)
        3 -> accent.copy(alpha = 0.78f)
        else -> accent.copy(alpha = 1f)
    }
}

internal fun formatHeatmapDayLabel(date: LocalDate, locale: Locale): String {
    return "${date.dayOfMonth} ${formatMonthShortLabel(YearMonth.from(date), locale)}"
}

internal fun formatHeatmapWeekdayLabel(dayOfWeek: DayOfWeek, locale: Locale): String {
    return dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
}

internal fun formatMonthShortLabel(month: YearMonth, locale: Locale): String {
    return month.month.getDisplayName(TextStyle.SHORT, locale)
        .replace(".", "")
        .lowercase(locale)
        .take(3)
}

internal fun formatMonthYearLabel(month: YearMonth, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    return month.format(formatter)
        .replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(locale)
            } else {
                char.toString()
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementHeatmapCard(
    activityData: List<DayActivity>,
    yearlyStats: List<Pair<YearMonth, MonthStats>>,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    val scope = rememberCoroutineScope()
    val animated = !rememberAuroraReducedMotion() && !colors.isEInk

    val today = remember { LocalDate.now() }
    val grid = remember(activityData, today) { buildHeatmapGrid(activityData, today) }
    val summary = remember(activityData, today) { computeHeatmapSummary(activityData, today) }

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
    ) {
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Заголовок: «АКТИВНОСТЬ» + диапазон «365 ДНЕЙ»
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

                    Spacer(modifier = Modifier.height(12.dp))

                    HeatmapSummaryRow(summary = summary)

                    Spacer(modifier = Modifier.height(12.dp))

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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Легенда «Меньше → Больше»
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.achievement_heatmap_less),
                            color = colors.textSecondary,
                            fontSize = 9.5.sp,
                        )
                        repeat(5) { level ->
                            Box(
                                modifier = Modifier
                                    .size(HEATMAP_CELL_SIZE)
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
                            fontSize = 9.5.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapSummaryRow(
    summary: HeatmapSummary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeatmapSummaryStat(
            value = summary.activeDays,
            label = stringResource(MR.strings.achievement_summary_active_days),
            modifier = Modifier.weight(1f),
        )
        HeatmapSummaryDivider()
        HeatmapSummaryStat(
            value = summary.currentStreak,
            label = stringResource(MR.strings.achievement_summary_current_streak),
            modifier = Modifier.weight(1f),
        )
        HeatmapSummaryDivider()
        HeatmapSummaryStat(
            value = summary.bestStreak,
            label = stringResource(MR.strings.achievement_summary_best_streak),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeatmapSummaryStat(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label.uppercase(),
            color = colors.textSecondary.copy(alpha = 0.8f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun HeatmapSummaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(12.dp)
            .background(AuroraTheme.colors.divider),
    )
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
    val scrollState = rememberScrollState()
    val monthLabels = remember(grid) { visibleMonthLabels(grid.monthLabels) }
    val fadeEnd = colors.surface.copy(alpha = 0.32f).compositeOver(colors.background)
    val atRightEdge by remember {
        derivedStateOf { scrollState.value >= scrollState.maxValue - HEATMAP_SCROLL_END_THRESHOLD_PX }
    }
    val fadeAlpha by animateFloatAsState(
        targetValue = if (atRightEdge) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "heatmap_fade",
    )

    // Авто-якорь: «сегодня» (последняя колонка) у правого края
    LaunchedEffect(scrollState.maxValue) { scrollState.scrollTo(scrollState.maxValue) }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Фиксированная колонка подписей пн/ср/пт вне скролла
        Box(
            modifier = Modifier
                .width(HEATMAP_DAY_LABEL_WIDTH)
                .padding(top = HEATMAP_MONTH_ROW_HEIGHT + HEATMAP_MONTH_ROW_GAP)
                .height(HEATMAP_GRID_HEIGHT),
        ) {
            listOf(
                0 to DayOfWeek.MONDAY,
                2 to DayOfWeek.WEDNESDAY,
                4 to DayOfWeek.FRIDAY,
            ).forEach { (row, dayOfWeek) ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(HEATMAP_CELL_PITCH)
                        .offset(y = HEATMAP_CELL_PITCH * row),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = formatHeatmapWeekdayLabel(dayOfWeek, locale),
                        color = colors.textSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(HEATMAP_DAY_LABEL_GAP))

        Box(modifier = Modifier.weight(1f)) {
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
                // Внутри скролла сетка и ряд меток месяцев едут вместе
                Column(modifier = Modifier.horizontalScroll(scrollState)) {
                    Box(
                        modifier = Modifier
                            .width(HEATMAP_GRID_WIDTH)
                            .height(HEATMAP_MONTH_ROW_HEIGHT),
                    ) {
                        monthLabels.forEach { label ->
                            val monthLabel = remember(label.month, locale) {
                                formatMonthShortLabel(label.month, locale)
                            }
                            Text(
                                text = monthLabel,
                                modifier = Modifier.offset(x = HEATMAP_CELL_PITCH * label.column),
                                color = colors.textSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(HEATMAP_MONTH_ROW_GAP))

                    HeatmapCanvas(
                        grid = grid,
                        animationProgress = animationProgress,
                        scrollState = scrollState,
                        onCellSelected = onCellSelected,
                    )
                }
            }

            // Right-edge fade: подсказка, что история продолжается влево
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(HEATMAP_FADE_WIDTH)
                    .height(HEATMAP_MONTH_ROW_HEIGHT + HEATMAP_MONTH_ROW_GAP + HEATMAP_GRID_HEIGHT)
                    .graphicsLayer { alpha = fadeAlpha }
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, fadeEnd),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun HeatmapCanvas(
    grid: HeatmapGrid,
    animationProgress: Float,
    scrollState: ScrollState,
    onCellSelected: (HeatmapCell, IntRect) -> Unit,
) {
    val colors = AuroraTheme.colors

    Canvas(
        modifier = Modifier
            .size(width = HEATMAP_GRID_WIDTH, height = HEATMAP_GRID_HEIGHT)
            .pointerInput(grid) {
                val pitchPx = HEATMAP_CELL_PITCH.toPx()
                val monthOffsetPx = (HEATMAP_MONTH_ROW_HEIGHT + HEATMAP_MONTH_ROW_GAP).toPx()
                detectTapGestures { offset ->
                    val column = (offset.x / pitchPx).toInt()
                    val row = (offset.y / pitchPx).toInt()
                    val cell = grid.columns.getOrNull(column)?.getOrNull(row)
                    if (cell != null) {
                        onCellSelected(
                            cell,
                            IntRect(
                                offset = IntOffset(
                                    x = (column * pitchPx).roundToInt() - scrollState.value,
                                    y = monthOffsetPx.roundToInt() + (row * pitchPx).roundToInt(),
                                ),
                                size = IntSize(pitchPx.roundToInt(), pitchPx.roundToInt()),
                            ),
                        )
                    }
                }
            },
    ) {
        val pitchPx = HEATMAP_CELL_PITCH.toPx()
        val cellPx = HEATMAP_CELL_SIZE.toPx()
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
                        style = Stroke(width = 1.5.dp.toPx()),
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
