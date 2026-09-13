package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ReelsFollowingShuffleTest {

    private fun entry(creator: String, id: String): Pair<String, ShortVideoItem> =
        creator to ShortVideoItem(id = id, videoUrl = "https://video/$id", posterUrl = "https://poster/$id")

    private fun adjacencyCount(list: List<Pair<String, ShortVideoItem>>, firstPrev: String? = null): Int {
        var prev = firstPrev
        var count = 0
        for ((creator, _) in list) {
            if (creator == prev) count++
            prev = creator
        }
        return count
    }

    @Test
    fun `equal-sized authors never end up adjacent`() {
        val entries = (1..3).map { entry("A", "a$it") } + (1..3).map { entry("B", "b$it") }
        repeat(25) { seed ->
            val result = shuffleFollowingBatch(entries, prevTailAuthor = null, random = Random(seed))
            adjacencyCount(result) shouldBe 0
            result.map { it.second.id }.sorted() shouldBe entries.map { it.second.id }.sorted()
        }
    }

    @Test
    fun `feasible unequal sizes stay separated`() {
        // A=5, B=3, C=1 -> n=9, max=5 <= ceil(9/2): full separation is possible.
        val entries = (1..5).map { entry("A", "a$it") } +
            (1..3).map { entry("B", "b$it") } +
            listOf(entry("C", "c1"))
        repeat(25) { seed ->
            adjacencyCount(shuffleFollowingBatch(entries, prevTailAuthor = null, random = Random(seed))) shouldBe 0
        }
    }

    @Test
    fun `batch head avoids the previous tail author when possible`() {
        val entries = listOf(entry("A", "a1"), entry("B", "b1"), entry("B", "b2"))
        repeat(25) { seed ->
            shuffleFollowingBatch(entries, prevTailAuthor = "A", random = Random(seed))
                .first().first shouldBe "B"
        }
    }

    @Test
    fun `dominant author keeps the batch complete with minimal adjacency`() {
        // A=4, B=1 -> n=5, max=4 > ceil(5/2): adjacency unavoidable, optimum = 2.
        val entries = (1..4).map { entry("A", "a$it") } + listOf(entry("B", "b1"))
        repeat(25) { seed ->
            val result = shuffleFollowingBatch(entries, prevTailAuthor = null, random = Random(seed))
            result.size shouldBe 5
            result.map { it.second.id }.sorted() shouldBe entries.map { it.second.id }.sorted()
            adjacencyCount(result) shouldBe 2
        }
    }

    @Test
    fun `same seed is deterministic and the input is not mutated`() {
        val entries = (1..12).map { entry("ABC"[it % 3].toString(), "id$it") }
        val copy = entries.toList()
        shuffleFollowingBatch(entries, prevTailAuthor = null, random = Random(42)) shouldBe
            shuffleFollowingBatch(entries, prevTailAuthor = null, random = Random(42))
        entries shouldBe copy
    }

    @Test
    fun `empty and single-entry batches pass through`() {
        shuffleFollowingBatch(emptyList(), prevTailAuthor = null, random = Random(1)) shouldBe emptyList()
        val single = listOf(entry("A", "a1"))
        shuffleFollowingBatch(single, prevTailAuthor = "A", random = Random(1)) shouldBe single
    }

    @Test
    fun `single-author batch keeps every item`() {
        val entries = (1..3).map { entry("A", "a$it") }
        val result = shuffleFollowingBatch(entries, prevTailAuthor = "A", random = Random(3))
        result.map { it.second.id }.sorted() shouldBe listOf("a1", "a2", "a3")
    }
}
