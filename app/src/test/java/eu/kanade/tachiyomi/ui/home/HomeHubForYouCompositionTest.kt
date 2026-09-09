package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class HomeHubForYouCompositionTest {

    private fun suggestion(row: DiscoveryRowType, title: String, position: Long, reason: String? = null) =
        DiscoverySuggestion(
            id = position,
            mediaType = DiscoveryMediaType.NOVEL,
            rowType = row,
            title = title,
            cleanTitle = title.lowercase(),
            coverUrl = null,
            reason = reason,
            seedTitle = if (row == DiscoveryRowType.LIKE) "Seed" else null,
            provider = "test",
            score = 0.0,
            position = position,
            createdAt = 1L,
        )

    @Test
    fun `teaser is mixed round-robin across signals and respects limit`() {
        val items = listOf(
            suggestion(DiscoveryRowType.TREND, "Trend1", 0),
            suggestion(DiscoveryRowType.LIKE, "Like1", 0),
            suggestion(DiscoveryRowType.LIKE, "Like2", 1),
            suggestion(DiscoveryRowType.TREND, "Trend2", 1),
        )
        val out = composeTeaserItems(items, limit = 3)
        out.map { it.title } shouldBe listOf("Like1", "Trend1", "Like2")
    }

    @Test
    fun `teaser limit coerced into 3 to 10`() {
        val items = (1..12).map { suggestion(DiscoveryRowType.LIKE, "L$it", it.toLong()) }
        composeTeaserItems(items, limit = 99).size shouldBe 10
        composeTeaserItems(items, limit = 1).size shouldBe 3
    }

    @Test
    fun `section shown whenever enabled, hidden only when disabled`() {
        shouldShowForYouSection(enabled = false) shouldBe false
        shouldShowForYouSection(enabled = true) shouldBe true
    }

    @Test
    fun `reason text composed from seed title or season payload`() {
        val similar = "Similar to “%1\$s”"
        val like = composeTeaserItems(listOf(suggestion(DiscoveryRowType.LIKE, "X", 0)), 6).single()
        discoveryReasonText(like, similar, "Trending now", "Next season") shouldBe
            "Similar to “Seed”"
        val trendCurrent = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.TREND, "Y", 0, reason = "current")),
            6,
        ).single()
        discoveryReasonText(trendCurrent, similar, "Trending now", "Next season") shouldBe
            "Trending now"
        val trendNext = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.TREND, "Z", 0, reason = "next")),
            6,
        ).single()
        discoveryReasonText(trendNext, similar, "Trending now", "Next season") shouldBe
            "Next season"
    }

    @Test
    fun `scroll enabled under welcome when discovery present`() {
        shouldEnableHomeHubScroll(
            showWelcome = true,
            historyCount = 0,
            recommendationCount = 0,
            discoveryCount = 2,
        ) shouldBe true
        shouldEnableHomeHubScroll(
            showWelcome = true,
            historyCount = 0,
            recommendationCount = 0,
            discoveryCount = 0,
        ) shouldBe false
    }
}
