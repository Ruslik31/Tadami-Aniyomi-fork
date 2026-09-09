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
}
