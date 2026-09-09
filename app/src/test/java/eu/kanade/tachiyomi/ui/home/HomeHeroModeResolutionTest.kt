package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeroMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHeroModeResolutionTest {

    @Test
    fun `continue always stays continue`() {
        resolveHeroPresentation(HomeHeroMode.Continue, discoveryEnabled = true, discoveryCount = 5) shouldBe
            HomeHeroMode.Continue
        resolveHeroPresentation(HomeHeroMode.Continue, discoveryEnabled = false, discoveryCount = 0) shouldBe
            HomeHeroMode.Continue
    }

    @Test
    fun `hybrid requires enabled discovery with items`() {
        resolveHeroPresentation(HomeHeroMode.Hybrid, discoveryEnabled = true, discoveryCount = 3) shouldBe
            HomeHeroMode.Hybrid
        resolveHeroPresentation(HomeHeroMode.Hybrid, discoveryEnabled = true, discoveryCount = 0) shouldBe
            HomeHeroMode.Continue
        resolveHeroPresentation(HomeHeroMode.Hybrid, discoveryEnabled = false, discoveryCount = 3) shouldBe
            HomeHeroMode.Continue
    }

    @Test
    fun `collage requires enabled discovery with items`() {
        resolveHeroPresentation(HomeHeroMode.Collage, discoveryEnabled = true, discoveryCount = 5) shouldBe
            HomeHeroMode.Collage
        resolveHeroPresentation(HomeHeroMode.Collage, discoveryEnabled = true, discoveryCount = 2) shouldBe
            HomeHeroMode.Continue
        resolveHeroPresentation(HomeHeroMode.Collage, discoveryEnabled = true, discoveryCount = 0) shouldBe
            HomeHeroMode.Continue
        resolveHeroPresentation(HomeHeroMode.Collage, discoveryEnabled = false, discoveryCount = 5) shouldBe
            HomeHeroMode.Continue
    }
}
