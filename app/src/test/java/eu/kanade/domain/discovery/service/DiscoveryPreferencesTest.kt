package eu.kanade.domain.discovery.service

import eu.kanade.domain.ui.model.HomeHeroMode
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class DiscoveryPreferencesTest {

    @Test
    fun `defaults are discovery-on, hero-continue, interval 24, teaser 6`() {
        val prefs = DiscoveryPreferences(InMemoryPreferenceStore())
        prefs.discoveryEnabled().get() shouldBe true
        prefs.homeHeroMode().get() shouldBe "continue"
        prefs.refreshIntervalHours().get() shouldBe 2
        prefs.teaserCount().get() shouldBe 16
        prefs.seedCount().get() shouldBe 5
        prefs.seedCompleted().get() shouldBe true
        prefs.seedActive14().get() shouldBe true
        prefs.seedAdded().get() shouldBe false
        prefs.rowLikeEnabled().get() shouldBe true
        prefs.rowTasteEnabled().get() shouldBe true
        prefs.rowTrendEnabled().get() shouldBe true
        prefs.rowSourceEnabled().get() shouldBe true
        prefs.seedCompletedDays().get() shouldBe 30
        prefs.seedActiveDays().get() shouldBe 14
        prefs.refreshWifiOnly().get() shouldBe false
        prefs.refreshAfterLibrary().get() shouldBe true
        prefs.showReasons().get() shouldBe true
        prefs.trendSeason().get() shouldBe "current"
        prefs.trendSort().get() shouldBe "popularity"
        prefs.collageRotationIntervalHours().get() shouldBe 2
        prefs.collageAnimationSpeed().get() shouldBe "normal"
        prefs.collageLastRotationTime().get() shouldBe 0L
    }

    @Test
    fun `hero mode fromKey falls back to Continue`() {
        HomeHeroMode.fromKey("hybrid") shouldBe HomeHeroMode.Hybrid
        HomeHeroMode.fromKey("collage") shouldBe HomeHeroMode.Collage
        HomeHeroMode.fromKey(null) shouldBe HomeHeroMode.Continue
        HomeHeroMode.fromKey("bogus") shouldBe HomeHeroMode.Continue
    }
}
