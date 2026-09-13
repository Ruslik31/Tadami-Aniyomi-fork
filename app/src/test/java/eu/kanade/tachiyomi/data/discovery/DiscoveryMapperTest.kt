package eu.kanade.tachiyomi.data.discovery

import data.Discovery_suggestions
import io.kotest.matchers.shouldBe
import tachiyomi.data.discovery.DiscoveryMapper
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle

class DiscoveryMapperTest {

    @org.junit.Test
    fun `normalize strips punctuation case and whitespace`() {
        normalizeDiscoveryTitle("  Re:ZERO — Kara  ") shouldBe "re zero kara"
        normalizeDiscoveryTitle("Frieren: Beyond Journey's End") shouldBe "frieren beyond journey s end"
    }

    @org.junit.Test
    fun `mapper converts db row to domain model`() {
        val row = Discovery_suggestions(
            id = 1L, media_type = "novel", row_type = "like", title = "Overlord",
            clean_title = "overlord", cover_url = "http://x/c.jpg", reason = "Похоже на Re:Zero",
            seed_title = "Re:Zero", provider = "anilist", score = 0.9, position = 0, created_at = 42L,
        )
        val mapped: DiscoverySuggestion = DiscoveryMapper.map(row)
        mapped.mediaType shouldBe DiscoveryMediaType.NOVEL
        mapped.rowType shouldBe DiscoveryRowType.LIKE
        mapped.coverUrl shouldBe "http://x/c.jpg"
        mapped.createdAt shouldBe 42L
    }

    @org.junit.Test
    fun `mediaType fromKey returns null for unknown key`() {
        DiscoveryMediaType.fromKey("bogus") shouldBe null
        DiscoveryMediaType.fromKey("anime") shouldBe DiscoveryMediaType.ANIME
    }
}
