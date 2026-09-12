package eu.kanade.presentation.achievement.components

import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.ActivityType
import tachiyomi.domain.achievement.model.DayActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AchievementHeatmapGridTest {

    // «Сегодня» из sign-off прототипа: 11.09.2026, пятница
    private val today = LocalDate.of(2026, 9, 11)

    private fun day(date: LocalDate, level: Int) = DayActivity(date, level, ActivityType.APP_OPEN)

    private fun yearOfActivity(
        anchor: LocalDate,
        levelOf: (LocalDate) -> Int = { 0 },
    ): List<DayActivity> =
        (0 until 365).map { offset -> anchor.minusDays(364L - offset) }.map { day(it, levelOf(it)) }

    @Test
    fun `grid has 53 columns of 7 rows`() {
        val grid = buildHeatmapGrid(yearOfActivity(today), today)

        assertEquals(53, grid.columns.size)
        assertTrue(grid.columns.all { it.size == 7 })
    }

    @Test
    fun `today lands in last column at its weekday row`() {
        val grid = buildHeatmapGrid(yearOfActivity(today), today)
        val lastColumn = grid.columns[52]

        // Недели начинаются с понедельника: пятница → row 4
        assertEquals(today, lastColumn[4]?.date)
        assertTrue(lastColumn[4]!!.isToday)

        // Остаток текущей недели — будущее, ячеек нет
        assertNull(lastColumn[5])
        assertNull(lastColumn[6])
    }

    @Test
    fun `alignment holds for monday and sunday today`() {
        val monday = LocalDate.of(2026, 9, 7)
        val mondayGrid = buildHeatmapGrid(yearOfActivity(monday), monday)
        assertEquals(monday, mondayGrid.columns[52][0]?.date)
        assertTrue(mondayGrid.columns[52][0]!!.isToday)
        assertNull(mondayGrid.columns[52][1])

        val sunday = LocalDate.of(2026, 9, 13)
        val sundayGrid = buildHeatmapGrid(yearOfActivity(sunday), sunday)
        assertEquals(sunday, sundayGrid.columns[52][6]?.date)
        assertTrue(sundayGrid.columns[52][6]!!.isToday)
    }

    @Test
    fun `every week starts on monday`() {
        val grid = buildHeatmapGrid(yearOfActivity(today), today)

        grid.columns.forEach { column ->
            assertEquals(DayOfWeek.MONDAY, column[0]?.date?.dayOfWeek)
        }
    }

    @Test
    fun `empty activity data yields level zero cells`() {
        val grid = buildHeatmapGrid(emptyList(), today)
        val cells = grid.columns.flatten().filterNotNull()

        // 52 полных недели + пн..пт текущей недели
        assertEquals(52 * 7 + today.dayOfWeek.value, cells.size)
        assertTrue(cells.all { it.level == 0 })
        assertEquals(1, cells.count { it.isToday })
        assertEquals(today, cells.first { it.isToday }.date)
    }

    @Test
    fun `levels are taken from activity data`() {
        val data = yearOfActivity(today) { date ->
            when (date) {
                today -> 4
                today.minusDays(100) -> 2
                else -> 0
            }
        } + day(today.minusDays(400), 4) // вне сетки — игнорируется

        val grid = buildHeatmapGrid(data, today)
        val cellsByDate = grid.columns.flatten().filterNotNull().associateBy { it.date }

        assertEquals(4, cellsByDate[today]?.level)
        assertEquals(2, cellsByDate[today.minusDays(100)]?.level)
        assertEquals(0, cellsByDate[today.minusDays(50)]?.level)
        assertNull(cellsByDate[today.minusDays(400)])
    }

    @Test
    fun `exactly one cell is marked as today`() {
        val grid = buildHeatmapGrid(yearOfActivity(today), today)

        assertEquals(1, grid.columns.flatten().filterNotNull().count { it.isToday })
    }

    @Test
    fun `leap year range is covered`() {
        val leapToday = LocalDate.of(2024, 2, 29) // четверг
        val firstDay = leapToday.minusDays(364)
        val grid = buildHeatmapGrid(yearOfActivity(leapToday) { if (it == firstDay) 3 else 0 }, leapToday)

        // Старт сетки — понедельник 27.02.2023
        assertEquals(LocalDate.of(2023, 2, 27), grid.columns[0][0]?.date)
        // Четверг → row 3 последней колонки
        assertEquals(leapToday, grid.columns[52][3]?.date)
        assertTrue(grid.columns[52][3]!!.isToday)
        assertEquals(3, grid.columns.flatten().filterNotNull().first { it.date == firstDay }.level)
    }

    @Test
    fun `month labels point at the first of month and do not overlap`() {
        val grid = buildHeatmapGrid(yearOfActivity(today), today)
        val labels = grid.monthLabels

        assertEquals(12, labels.size)
        val columns = labels.map { it.column }
        assertEquals(columns.sorted(), columns)
        columns.zipWithNext { prev, next -> assertTrue(next - prev >= 4) }

        val gridStart = grid.columns[0][0]!!.date
        labels.forEach { label ->
            val weekDates = (0L until 7L).map { gridStart.plusDays(label.column * 7L + it) }
            assertTrue(weekDates.any { it.dayOfMonth == 1 && YearMonth.from(it) == label.month })
        }
        assertEquals(YearMonth.of(2025, 10), labels.first().month)
        assertEquals(3, labels.first().column)
        assertEquals(YearMonth.of(2026, 9), labels.last().month)
        assertEquals(51, labels.last().column)
    }

    @Test
    fun `future first of month gets no label`() {
        val monday = LocalDate.of(2026, 9, 28)
        val grid = buildHeatmapGrid(yearOfActivity(monday), monday)

        // 1 октября 2026 попадает в последнюю колонку, но это будущее
        assertNull(grid.columns[52][3])
        assertTrue(grid.monthLabels.none { it.month == YearMonth.of(2026, 10) })
    }
}
