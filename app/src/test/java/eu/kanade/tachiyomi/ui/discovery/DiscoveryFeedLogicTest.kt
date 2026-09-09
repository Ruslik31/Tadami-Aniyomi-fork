package eu.kanade.tachiyomi.ui.discovery

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryFeedLogicTest {

    private fun suggestion(row: DiscoveryRowType, title: String) = DiscoverySuggestion(
        id = 1,
        mediaType = DiscoveryMediaType.ANIME,
        rowType = row,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "anilist",
        score = 0.0,
        position = 0,
        createdAt = 100L,
    )

    @Test
    fun `manual refresh blocked during cooldown and allowed after`() {
        val now = 1_000_000L
        canManualRefresh(lastRefreshAt = now - 60_000, now = now) shouldBe false
        canManualRefresh(lastRefreshAt = now - 6 * 60_000, now = now) shouldBe true
        canManualRefresh(lastRefreshAt = null, now = now) shouldBe true
    }

    @Test
    fun `remaining cooldown seconds calculated correctly`() {
        val now = 1_000_000L
        val cooldownMs = 300_000L
        remainingCooldownSeconds(lastRefreshAt = now - 60_000L, now = now, cooldownMs = cooldownMs) shouldBe 240L
        remainingCooldownSeconds(lastRefreshAt = now - 270_000L, now = now, cooldownMs = cooldownMs) shouldBe 30L
        remainingCooldownSeconds(lastRefreshAt = now - 300_000L, now = now, cooldownMs = cooldownMs) shouldBe 0L
        remainingCooldownSeconds(lastRefreshAt = now - 350_000L, now = now, cooldownMs = cooldownMs) shouldBe 0L
        remainingCooldownSeconds(lastRefreshAt = null, now = now, cooldownMs = cooldownMs) shouldBe 0L
    }

    @Test
    fun `rows grouped by type keeping like before trend`() {
        val grouped = groupFeedRows(
            listOf(
                suggestion(DiscoveryRowType.TREND, "T1"),
                suggestion(DiscoveryRowType.LIKE, "L1"),
                suggestion(DiscoveryRowType.LIKE, "L2"),
            ),
        )
        grouped.keys.toList() shouldBe listOf(DiscoveryRowType.LIKE, DiscoveryRowType.TREND)
        grouped[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("L1", "L2")
    }

    @Test
    fun `updated label resolves minutes hours and never`() {
        val now = 60L * 60 * 1000 * 10
        resolveUpdatedLabel(lastUpdatedAt = now - 5 * 60_000, now = now) shouldBe (UpdatedLabelKind.MINUTES to 5L)
        resolveUpdatedLabel(lastUpdatedAt = now - 3 * 60 * 60_000, now = now) shouldBe (UpdatedLabelKind.HOURS to 3L)
        resolveUpdatedLabel(lastUpdatedAt = null, now = now) shouldBe (UpdatedLabelKind.NEVER to null)
    }

    @Test
    fun `db row maps to suggestion item with global-search-ready queries`() {
        val item = suggestion(DiscoveryRowType.LIKE, "Some Title").toSuggestionItem()
        item.title shouldBe "Some Title"
        item.searchQueries shouldBe listOf("Some Title")
        item.nativeSourceTarget shouldBe null // slice 1: всегда global search fallback
    }
}
