package eu.kanade.presentation.achievement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.achievement.components.AchievementHeatmapCard
import eu.kanade.presentation.achievement.components.AchievementStatsComparison
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.LocalAuroraColors
import org.junit.Rule
import org.junit.Test
import tachiyomi.domain.achievement.model.MonthStats
import java.time.YearMonth

class AchievementScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun statsComparison_displaysCorrectValues() {
        val currentStats = MonthStats(
            chaptersRead = 127,
            episodesWatched = 45,
            timeInAppMinutes = 2340,
            achievementsUnlocked = 8,
        )
        val previousStats = MonthStats(
            chaptersRead = 98,
            episodesWatched = 62,
            timeInAppMinutes = 1890,
            achievementsUnlocked = 5,
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp),
                ) {
                    AchievementStatsComparison(
                        currentMonth = currentStats,
                        previousMonth = previousStats,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Сравнение с прошлым месяцем").assertIsDisplayed()
        composeTestRule.onNodeWithText("127").assertIsDisplayed()
        composeTestRule.onNodeWithText("Глав прочитано").assertIsDisplayed()
    }

    @Test
    fun heatmapCard_displaysTitleAndPeriod() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp),
                ) {
                    AchievementHeatmapCard(
                        activityData = emptyList(),
                        yearlyStats = generateTestYearlyStats(),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("АКТИВНОСТЬ").assertIsDisplayed()
        composeTestRule.onNodeWithText("365 ДНЕЙ").assertIsDisplayed()
    }

    @Test
    fun heatmapCard_displaysSummaryStats() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp),
                ) {
                    AchievementHeatmapCard(
                        activityData = emptyList(),
                        yearlyStats = generateTestYearlyStats(),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("ДНЕЙ АКТИВНОСТИ").assertIsDisplayed()
        composeTestRule.onNodeWithText("ТЕКУЩАЯ СЕРИЯ").assertIsDisplayed()
        composeTestRule.onNodeWithText("РЕКОРДНАЯ СЕРИЯ").assertIsDisplayed()
    }

    @Test
    fun statCards_displayWithDifferentValueLengths() {
        val currentStats = MonthStats(
            chaptersRead = 9999,
            episodesWatched = 1,
            timeInAppMinutes = 9999,
            achievementsUnlocked = 999,
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAuroraColors provides AuroraColors.Dark) {
                Box(
                    modifier = Modifier
                        .background(AuroraColors.Dark.background)
                        .padding(16.dp),
                ) {
                    AchievementStatsComparison(
                        currentMonth = currentStats,
                        previousMonth = currentStats,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("9999").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }

    private fun generateTestYearlyStats(): List<Pair<YearMonth, MonthStats>> {
        val stats = mutableListOf<Pair<YearMonth, MonthStats>>()
        val currentMonth = YearMonth.now()

        for (i in 0..11) {
            val month = currentMonth.minusMonths(i.toLong())
            val monthStats = MonthStats(
                chaptersRead = (0..10).random(),
                episodesWatched = (0..5).random(),
                timeInAppMinutes = (0..300).random(),
                achievementsUnlocked = (0..3).random(),
            )
            stats.add(0, month to monthStats)
        }

        return stats
    }
}
