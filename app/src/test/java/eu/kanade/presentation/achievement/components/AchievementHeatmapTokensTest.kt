package eu.kanade.presentation.achievement.components

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.ActivityType
import tachiyomi.domain.achievement.model.DayActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.test.assertEquals

class AchievementHeatmapTokensTest {

    private val accent = Color(0xFF0095FF)
    private val foreground = Color(0xFFFFFFFF)

    // «Сегодня» из sign-off прототипа: 11.09.2026, пятница
    private val today = LocalDate.of(2026, 9, 11)

    private fun day(daysAgo: Long, level: Int) =
        DayActivity(today.minusDays(daysAgo), level, ActivityType.APP_OPEN)

    @Test
    fun `scale A dark theme uses accent alphas`() {
        assertEquals(
            Color.White.copy(alpha = 0.08f),
            heatmapLevelColor(0, isEInk = false, isDark = true, accent = accent, foreground = foreground),
        )
        assertEquals(accent.copy(alpha = 0.35f), heatmapLevelColor(1, false, true, accent, foreground))
        assertEquals(accent.copy(alpha = 0.55f), heatmapLevelColor(2, false, true, accent, foreground))
        assertEquals(accent.copy(alpha = 0.78f), heatmapLevelColor(3, false, true, accent, foreground))
        assertEquals(accent.copy(alpha = 1f), heatmapLevelColor(4, false, true, accent, foreground))
    }

    @Test
    fun `scale A light theme uses black zero level`() {
        assertEquals(
            Color.Black.copy(alpha = 0.08f),
            heatmapLevelColor(0, isEInk = false, isDark = false, accent = accent, foreground = Color.Black),
        )
        assertEquals(accent.copy(alpha = 0.35f), heatmapLevelColor(1, false, false, accent, Color.Black))
    }

    @Test
    fun `e-ink overrides scale with foreground alphas`() {
        val ink = Color.Black
        assertEquals(
            Color.Black.copy(alpha = 0.10f),
            heatmapLevelColor(0, isEInk = true, isDark = false, accent = accent, foreground = ink),
        )
        assertEquals(ink.copy(alpha = 0.16f), heatmapLevelColor(1, true, false, accent, ink))
        assertEquals(ink.copy(alpha = 0.34f), heatmapLevelColor(2, true, false, accent, ink))
        assertEquals(ink.copy(alpha = 0.60f), heatmapLevelColor(3, true, false, accent, ink))
        assertEquals(ink.copy(alpha = 0.88f), heatmapLevelColor(4, true, false, accent, ink))
    }

    @Test
    fun `e-ink dark zero level is white`() {
        assertEquals(
            Color.White.copy(alpha = 0.10f),
            heatmapLevelColor(0, isEInk = true, isDark = true, accent = accent, foreground = Color.White),
        )
    }

    @Test
    fun `day label follows locale`() {
        val date = LocalDate.of(2026, 9, 11)

        assertEquals("11 сен", formatHeatmapDayLabel(date, Locale.forLanguageTag("ru")))
        assertEquals("11 sep", formatHeatmapDayLabel(date, Locale.ENGLISH))
    }

    @Test
    fun `weekday label follows locale without pattern letters`() {
        val ru = Locale.forLanguageTag("ru")

        assertEquals("пн", formatHeatmapWeekdayLabel(DayOfWeek.MONDAY, ru))
        assertEquals("ср", formatHeatmapWeekdayLabel(DayOfWeek.WEDNESDAY, ru))
        assertEquals("пт", formatHeatmapWeekdayLabel(DayOfWeek.FRIDAY, ru))
        assertEquals("Mon", formatHeatmapWeekdayLabel(DayOfWeek.MONDAY, Locale.ENGLISH))
    }

    @Test
    fun `short month labels follow locale`() {
        val month = YearMonth.of(2024, 1)

        assertEquals("jan", formatMonthShortLabel(month, Locale.ENGLISH))
        assertEquals("янв", formatMonthShortLabel(month, Locale.forLanguageTag("ru")))
    }

    @Test
    fun `full month labels follow locale`() {
        val month = YearMonth.of(2024, 1)

        assertEquals("January 2024", formatMonthYearLabel(month, Locale.ENGLISH))
        assertEquals("Январь 2024", formatMonthYearLabel(month, Locale.forLanguageTag("ru")))
    }

    @Test
    fun `summary counts active days and both streaks over the year`() {
        val data = (0L..4L).map { day(it, 3) } + // хвостовой ран до today: 5 дней
            (10L..19L).map { day(it, 1) } + // рекордный ран: 10 дней
            listOf(day(100, 2), day(200, 4), day(400, 4)) // последнее — вне окна года

        val summary = computeHeatmapSummary(data, today)

        assertEquals(17, summary.activeDays)
        assertEquals(5, summary.currentStreak)
        assertEquals(10, summary.bestStreak)
    }

    @Test
    fun `summary current streak is zero when today is inactive`() {
        val data = (1L..7L).map { day(it, 2) }

        val summary = computeHeatmapSummary(data, today)

        assertEquals(7, summary.activeDays)
        assertEquals(0, summary.currentStreak)
        assertEquals(7, summary.bestStreak)
    }

    @Test
    fun `summary of empty data is all zeros`() {
        assertEquals(
            HeatmapSummary(activeDays = 0, currentStreak = 0, bestStreak = 0),
            computeHeatmapSummary(emptyList(), today),
        )
    }

    @Test
    fun `visible month labels keep at least six columns apart`() {
        val labels = listOf(3, 7, 12, 16, 20, 24, 29, 33, 38, 42, 46, 51)
            .map { HeatmapMonthLabel(column = it, month = YearMonth.of(2026, 1)) }

        assertEquals(
            listOf(3, 12, 20, 29, 38, 46),
            visibleMonthLabels(labels).map { it.column },
        )
        assertEquals(emptyList(), visibleMonthLabels(emptyList()))
    }
}
