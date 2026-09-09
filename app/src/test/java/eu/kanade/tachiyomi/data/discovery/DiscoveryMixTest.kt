package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryRowType

class DiscoveryMixTest {

    private fun item(
        type: DiscoveryRowType,
        title: String,
        score: Double = 1.0,
        seed: String? = null,
    ) = DiscoveryRowItem(title, title.lowercase(), null, null, seed, "p", score)

    @Test
    fun `interleave respects quotas round-robin`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to (1..6).map { item(DiscoveryRowType.LIKE, "L$it", 6.0 - it) },
            DiscoveryRowType.TASTE to (1..4).map { item(DiscoveryRowType.TASTE, "T$it", 4.0 - it) },
            DiscoveryRowType.TREND to (1..3).map { item(DiscoveryRowType.TREND, "F$it", 3.0 - it) },
            DiscoveryRowType.SOURCE to (1..3).map { item(DiscoveryRowType.SOURCE, "S$it", 2.0 - it) },
        )
        val mix = interleaveMix(rows)
        mix.size shouldBe 16
        mix.take(4).map { it.title } shouldBe listOf("L1", "T1", "F1", "S1")
        mix.count { it.title.startsWith("L") } shouldBe 6
        mix.count { it.title.startsWith("T") } shouldBe 4
    }

    @Test
    fun `sparse rows yield fewer items without crash`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to listOf(item(DiscoveryRowType.LIKE, "L1", 1.0)),
            DiscoveryRowType.TREND to (1..3).map { item(DiscoveryRowType.TREND, "F$it", 3.0 - it) },
        )
        val mix = interleaveMix(rows)
        mix.size shouldBe 4
        mix.map { it.title } shouldBe listOf("L1", "F1", "F2", "F3")
    }

    @Test
    fun `fill remainder by score across signals`() {
        val rows = mapOf(
            DiscoveryRowType.LIKE to (1..2).map { item(DiscoveryRowType.LIKE, "L$it", it.toDouble()) },
            DiscoveryRowType.TREND to (1..10).map { item(DiscoveryRowType.TREND, "T$it", (11 - it).toDouble()) },
        )
        val mix = interleaveMix(rows)
        // round-robin: L1,T1,L2,T2,T3,T4 (квоты L=2,T=4 исчерпаны) = 6, добивка по скору: T5..T10
        mix.size shouldBe 12
        mix.take(6).map { it.title } shouldBe listOf("L1", "T1", "L2", "T2", "T3", "T4")
        mix.last().title shouldBe "T10"
    }

    @Test
    fun `overlap of two seeds boosts score and joins reasons`() {
        val seedA = listOf(
            item(DiscoveryRowType.LIKE, "Dup", 2.0, seed = "A"),
            item(DiscoveryRowType.LIKE, "OnlyA", 1.0, seed = "A"),
        )
        val seedB = listOf(item(DiscoveryRowType.LIKE, "Dup", 1.5, seed = "B"))
        val merged = mergeSeedResults(listOf(seedA, seedB))
        val dup = merged.first { it.cleanTitle == "dup" }
        dup.score shouldBe 2.0 * 1.5 // k=2 → maxScore * (1 + 0.5)
        dup.seedTitle shouldBe "A, B"
        merged.first().cleanTitle shouldBe "dup"
    }

    @Test
    fun `per-seed cap limits cards from one seed`() {
        val seedA = (1..5).map { item(DiscoveryRowType.LIKE, "A$it", 5.0 - it, seed = "A") }
        val merged = mergeSeedResults(listOf(seedA), perSeedCap = 2)
        merged.size shouldBe 2
        merged.map { it.title } shouldBe listOf("A1", "A2")
    }

    @Test
    fun `taste profile weights genres by recency and window`() {
        val now = 1_800_000_000_000L
        val day = 86_400_000L
        val candidates = listOf(
            DiscoverySeedInput(
                entryId = 1,
                title = "X",
                genres = listOf("Drama", "Action"),
                lastInteraction = now - 10 * day,
            ),
            // вне окна 90 дней — не участвует
            DiscoverySeedInput(
                entryId = 2,
                title = "Y",
                genres = listOf("Drama"),
                lastInteraction = now - 200 * day,
            ),
            DiscoverySeedInput(
                entryId = 3,
                title = "Z",
                genres = listOf("Action"),
                lastInteraction = now - 2 * day,
            ),
        )
        val profile = buildTasteProfile(candidates, nowMs = now)
        profile.map { it.first } shouldBe listOf("Action", "Drama")
        (tasteScore(listOf("Action", "Comedy"), profile) > tasteScore(listOf("Drama"), profile)) shouldBe true
        matchedGenres(listOf("Drama", "Action"), profile) shouldBe listOf("Drama", "Action")
    }
}
