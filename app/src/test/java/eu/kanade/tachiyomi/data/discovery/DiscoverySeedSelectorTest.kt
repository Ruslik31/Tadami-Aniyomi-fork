package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test

class DiscoverySeedSelectorTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val selector = DiscoverySeedSelector(nowMs = { now })

    private fun seed(id: Long) = DiscoverySeedInput(entryId = id, title = "T$id")

    @Test
    fun `completed within window outrank active and added`() {
        val completed = seed(1).copy(isCompleted = true, completedAt = now - 2 * day, lastInteraction = now - 2 * day)
        val active = seed(2).copy(lastInteraction = now - 1 * day)
        val added = seed(3).copy(dateAdded = now - 1 * day)
        val out = selector.select(
            listOf(added, active, completed),
            SeedSettings(maxSeeds = 3, useCompleted = true, useActive14 = true, useAdded = true),
        )
        out.map { it.entryId } shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `stale completed outside window is skipped`() {
        val old = seed(1).copy(isCompleted = true, completedAt = now - 40 * day)
        val active = seed(2).copy(lastInteraction = now - 3 * day)
        val out = selector.select(listOf(old, active), SeedSettings(maxSeeds = 3))
        out.map { it.entryId } shouldBe listOf(2L)
    }

    @Test
    fun `custom completed and active windows are respected`() {
        val completedAt40Days = seed(1).copy(
            isCompleted = true,
            completedAt = now - 40 * day,
            lastInteraction =
            now - 40 * day,
        )
        val activeAt20Days = seed(2).copy(lastInteraction = now - 20 * day)

        val outDefault = selector.select(
            listOf(completedAt40Days, activeAt20Days),
            SeedSettings(maxSeeds = 3),
        )
        outDefault shouldBe emptyList()

        val outCustom = selector.select(
            listOf(completedAt40Days, activeAt20Days),
            SeedSettings(maxSeeds = 3, completedWindowDays = 60, activeWindowDays = 30),
        )
        outCustom.map { it.entryId } shouldBe listOf(1L, 2L)
    }

    @Test
    fun `maxSeeds caps and dedupes by entryId`() {
        val a = seed(1).copy(isCompleted = true, completedAt = now)
        val b = seed(1).copy(lastInteraction = now) // тот же entry
        val c = seed(2).copy(lastInteraction = now - day)
        val d = seed(3).copy(dateAdded = now)
        val out = selector.select(
            listOf(a, b, c, d),
            SeedSettings(maxSeeds = 2, useCompleted = true, useActive14 = true, useAdded = true),
        )
        out.map { it.entryId } shouldBe listOf(1L, 2L)
    }

    @Test
    fun `disabled types are ignored and blank titles dropped`() {
        // blank-кандидат намеренно «активен» (lastInteraction = now): без isNotBlank-фильтра
        // он был бы выбран tier-2 → тест дискриминирует и blank-drop, и useAdded=false.
        val blank = DiscoverySeedInput(entryId = 9, title = " ", lastInteraction = now)
        val added = seed(3).copy(dateAdded = now)
        val out = selector.select(
            listOf(blank, added),
            SeedSettings(maxSeeds = 3, useCompleted = true, useActive14 = true, useAdded = false),
        )
        out shouldBe emptyList()
    }

    @Test
    fun `offset shifts selected seeds circularly`() {
        val seeds = (1..6).map { seed(it.toLong()).copy(isCompleted = true, completedAt = now) }
        val out0 = selector.select(seeds, SeedSettings(maxSeeds = 3), offset = 0)
        out0.map { it.entryId } shouldBe listOf(1L, 2L, 3L)

        val out2 = selector.select(seeds, SeedSettings(maxSeeds = 3), offset = 2)
        out2.map { it.entryId } shouldBe listOf(3L, 4L, 5L)

        val out5 = selector.select(seeds, SeedSettings(maxSeeds = 3), offset = 5)
        out5.map { it.entryId } shouldBe listOf(6L, 1L, 2L)
    }

    @Test
    fun `rankSourceIds orders by library weight with deterministic id tiebreak`() {
        val candidates = listOf(
            seed(1).copy(sourceId = 5L),
            seed(2).copy(sourceId = 5L),
            seed(3).copy(sourceId = 5L),
            seed(4).copy(sourceId = 3L),
            seed(5).copy(sourceId = 3L),
            seed(6).copy(sourceId = 9L),
            seed(7).copy(sourceId = 9L),
            seed(8).copy(sourceId = 7L),
            seed(9).copy(sourceId = 7L),
            seed(10).copy(sourceId = -1L), // локальный/неизвестный — не ранжируется
        )
        // 5×3; ничья 3/7/9 по 2 → tie-break по возрастанию id
        rankSourceIds(candidates, limit = 3) shouldBe listOf(5L, 3L, 7L)
        rankSourceIds(candidates, limit = 10).size shouldBe 4
        rankSourceIds(emptyList()) shouldBe emptyList()
    }
}
