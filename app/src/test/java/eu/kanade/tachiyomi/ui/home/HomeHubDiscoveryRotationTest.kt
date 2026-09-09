package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeroMode
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class HomeHubDiscoveryRotationTest {

    private fun suggestion(title: String, type: DiscoveryRowType = DiscoveryRowType.TREND) = DiscoverySuggestion(
        id = 0L,
        mediaType = DiscoveryMediaType.MANGA,
        rowType = type,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = 1.0,
        position = 0L,
        createdAt = 0L,
    )

    @Test
    fun `composeTeaserItems with offset produces non-overlapping window from large pool`() {
        val items = (1..32).map { suggestion("Item $it") }

        val window1 = composeTeaserItems(items, limit = 16, offset = 0)
        val window2 = composeTeaserItems(items, limit = 16, offset = 16)

        window1.size shouldBe 16
        window2.size shouldBe 16

        val titles1 = window1.map { it.title }.toSet()
        val titles2 = window2.map { it.title }.toSet()

        // Windows should not overlap when pool has enough items
        titles1.intersect(titles2) shouldBe emptySet()
    }

    @Test
    fun `composeTeaserItems offset wraps around safely`() {
        val items = (1..10).map { suggestion("Item $it") }
        val window = composeTeaserItems(items, limit = 5, offset = 12)
        window.size shouldBe 5
        window.map { it.title } shouldBe listOf("Item 3", "Item 4", "Item 5", "Item 6", "Item 7")
    }

    @Test
    fun `resolveHybridDiscoveryStripLayoutSpec aligns with HomeHub recent card metrics across device classes`() {
        resolveHybridDiscoveryStripLayoutSpec(
            eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass.Phone,
        ) shouldBe
            HybridDiscoveryStripLayoutSpec(
                cardWidth = 128,
                sectionHorizontalPadding = 24,
                rowSpacing = 14,
            )

        resolveHybridDiscoveryStripLayoutSpec(
            eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass.TabletCompact,
        ) shouldBe
            HybridDiscoveryStripLayoutSpec(
                cardWidth = 152,
                sectionHorizontalPadding = 28,
                rowSpacing = 16,
            )

        resolveHybridDiscoveryStripLayoutSpec(
            eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass.TabletExpanded,
        ) shouldBe
            HybridDiscoveryStripLayoutSpec(
                cardWidth = 176,
                sectionHorizontalPadding = 32,
                rowSpacing = 18,
            )
    }

    @Test
    fun `shouldRenderHomeHubHeroSlot renders collage even when hero is null and not reserved`() {
        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Collage,
            hasHero = false,
            reserveHeroSlot = false,
            hasDiscovery = true,
        ) shouldBe true

        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Collage,
            hasHero = false,
            reserveHeroSlot = false,
            hasDiscovery = false,
        ) shouldBe false

        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Continue,
            hasHero = false,
            reserveHeroSlot = false,
            hasDiscovery = true,
        ) shouldBe false

        shouldRenderHomeHubHeroSlot(
            heroPresentation = HomeHeroMode.Continue,
            hasHero = true,
            reserveHeroSlot = false,
            hasDiscovery = false,
        ) shouldBe true
    }
}
