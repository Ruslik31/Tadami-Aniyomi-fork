package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class HomeHubForYouCompositionTest {

    private fun suggestion(
        row: DiscoveryRowType,
        title: String,
        position: Long,
        reason: String? = null,
        provider: String = "test",
    ) =
        DiscoverySuggestion(
            id = position,
            mediaType = DiscoveryMediaType.NOVEL,
            rowType = row,
            title = title,
            cleanTitle = title.lowercase(),
            coverUrl = null,
            reason = reason,
            seedTitle = if (row == DiscoveryRowType.LIKE) "Seed" else null,
            provider = provider,
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
    fun `teaser limit coerced into 3 to 20`() {
        val items = (1..25).map { suggestion(DiscoveryRowType.LIKE, "L$it", it.toLong()) }
        composeTeaserItems(items, limit = 99).size shouldBe 20
        composeTeaserItems(items, limit = 1).size shouldBe 3
    }

    @Test
    fun `for you items suppressed in hybrid with hero and in collage, visible in continue`() {
        val dummy = listOf(
            composeTeaserItems(listOf(suggestion(DiscoveryRowType.LIKE, "Title", 0)), 1).first(),
        )
        resolveForYouItems(eu.kanade.domain.ui.model.HomeHeroMode.Hybrid, hasHero = true, discovery = dummy) shouldBe
            emptyList()
        resolveForYouItems(eu.kanade.domain.ui.model.HomeHeroMode.Collage, hasHero = true, discovery = dummy) shouldBe
            emptyList()
        resolveForYouItems(eu.kanade.domain.ui.model.HomeHeroMode.Continue, hasHero = true, discovery = dummy) shouldBe
            dummy
        resolveForYouItems(eu.kanade.domain.ui.model.HomeHeroMode.Hybrid, hasHero = false, discovery = dummy) shouldBe
            dummy
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
    fun `source row shows provider name and blank taste reason is hidden`() {
        val similar = "Similar to “%1\$s”"
        val source = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.SOURCE, "S", 0, provider = "InkStory")),
            6,
        ).single()
        discoveryReasonText(source, similar, "Trending now", "Next season") shouldBe "InkStory"

        val trendFromSource = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.TREND, "T", 0, reason = "source", provider = "RanobeHub")),
            6,
        ).single()
        discoveryReasonText(trendFromSource, similar, "Trending now", "Next season") shouldBe "RanobeHub"

        val tasteBlank = composeTeaserItems(
            listOf(suggestion(DiscoveryRowType.TASTE, "X", 0, reason = "")),
            6,
        ).single()
        discoveryReasonText(tasteBlank, similar, "Trending now", "Next season") shouldBe null
    }

    @Test
    fun `firstBlacklistTag picks first taste genre only`() {
        firstBlacklistTag(DiscoveryRowType.TASTE, "Фэнтези, Драма") shouldBe "Фэнтези"
        firstBlacklistTag(DiscoveryRowType.TASTE, " Фэнтези ") shouldBe "Фэнтези"
        firstBlacklistTag(DiscoveryRowType.TASTE, "") shouldBe null
        firstBlacklistTag(DiscoveryRowType.TASTE, null) shouldBe null
        firstBlacklistTag(DiscoveryRowType.LIKE, "anything") shouldBe null
        firstBlacklistTag(DiscoveryRowType.SOURCE, "anything") shouldBe null
    }

    @Test
    fun `countAffectedTeasers counts taste cards matching tag with translations`() {
        val items = listOf(
            composeTeaserItems(
                listOf(suggestion(DiscoveryRowType.TASTE, "A", 0, reason = "Фэнтези, Драма")),
                6,
            ).single(),
            composeTeaserItems(
                listOf(suggestion(DiscoveryRowType.TASTE, "B", 0, reason = "Экшен")),
                6,
            ).single(),
            composeTeaserItems(
                listOf(suggestion(DiscoveryRowType.LIKE, "C", 0)),
                6,
            ).single(),
        )
        countAffectedTeasers(items, "Fantasy") shouldBe 1
        countAffectedTeasers(items, "Экшен") shouldBe 1
        countAffectedTeasers(items, "Комедия") shouldBe 0
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
