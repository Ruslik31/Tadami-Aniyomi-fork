package eu.kanade.presentation.achievement.components

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals

class AchievementHeatmapTokensTest {

    private val accent = Color(0xFF0095FF)
    private val foreground = Color(0xFFFFFFFF)

    @Test
    fun `scale A dark theme uses accent alphas`() {
        assertEquals(
            Color.White.copy(alpha = 0.05f),
            heatmapLevelColor(0, isEInk = false, isDark = true, accent = accent, foreground = foreground),
        )
        assertEquals(accent.copy(alpha = 0.25f), heatmapLevelColor(1, false, true, accent, foreground))
        assertEquals(accent.copy(alpha = 0.45f), heatmapLevelColor(2, false, true, accent, foreground))
        assertEquals(accent.copy(alpha = 0.70f), heatmapLevelColor(3, false, true, accent, foreground))
        assertEquals(accent.copy(alpha = 1f), heatmapLevelColor(4, false, true, accent, foreground))
    }

    @Test
    fun `scale A light theme uses black zero level`() {
        assertEquals(
            Color.Black.copy(alpha = 0.06f),
            heatmapLevelColor(0, isEInk = false, isDark = false, accent = accent, foreground = Color.Black),
        )
        assertEquals(accent.copy(alpha = 0.25f), heatmapLevelColor(1, false, false, accent, Color.Black))
    }

    @Test
    fun `e-ink overrides scale with foreground alphas`() {
        val ink = Color.Black
        assertEquals(
            Color.Black.copy(alpha = 0.06f),
            heatmapLevelColor(0, isEInk = true, isDark = false, accent = accent, foreground = ink),
        )
        assertEquals(ink.copy(alpha = 0.12f), heatmapLevelColor(1, true, false, accent, ink))
        assertEquals(ink.copy(alpha = 0.30f), heatmapLevelColor(2, true, false, accent, ink))
        assertEquals(ink.copy(alpha = 0.55f), heatmapLevelColor(3, true, false, accent, ink))
        assertEquals(ink.copy(alpha = 0.85f), heatmapLevelColor(4, true, false, accent, ink))
    }

    @Test
    fun `e-ink dark zero level is white`() {
        assertEquals(
            Color.White.copy(alpha = 0.08f),
            heatmapLevelColor(0, isEInk = true, isDark = true, accent = accent, foreground = Color.White),
        )
    }

    @Test
    fun `day label follows locale`() {
        val date = LocalDate.of(2026, 9, 11)

        assertEquals("11 сен", formatHeatmapDayLabel(date, Locale.forLanguageTag("ru")))
        assertEquals("11 sep", formatHeatmapDayLabel(date, Locale.ENGLISH))
    }
}
