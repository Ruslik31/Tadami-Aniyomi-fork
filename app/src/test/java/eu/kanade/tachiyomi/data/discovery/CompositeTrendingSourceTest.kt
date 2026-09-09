package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import java.io.IOException

class CompositeTrendingSourceTest {

    private class MockTrendingSource(
        private val name: String,
        private val items: List<DiscoveryTrendingItem> = emptyList(),
        private val meta: DiscoveryMeta? = null,
        private val shouldFail: Boolean = false,
    ) : DiscoveryTrendingSource {
        var fetchCalled = false
        var fetchMetaCalled = false

        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
        ): List<DiscoveryTrendingItem> {
            fetchCalled = true
            if (shouldFail) throw IOException("$name simulated error")
            return items
        }

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
        ): List<DiscoveryTrendingItem> {
            fetchCalled = true
            if (shouldFail) throw IOException("$name simulated error")
            return items
        }

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
            fetchMetaCalled = true
            if (shouldFail) throw IOException("$name simulated error")
            return meta
        }
    }

    @Test
    fun `anime queries Shikimori first and returns when successful`() = runTest {
        val shikimoriItem =
            DiscoveryTrendingItem(
                "Frieren",
                "frieren",
                "http://shiki/1.jpg",
                1L,
                "current",
                provider = "shikimori_trend",
            )
        val shikimori = MockTrendingSource("shikimori", items = listOf(shikimoriItem))
        val jikan = MockTrendingSource("jikan")
        val anilist = MockTrendingSource("anilist")

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = MockTrendingSource("mangadex"),
            jikan = jikan,
            anilist = anilist,
        )

        val result = composite.fetch(DiscoveryMediaType.ANIME)
        result.size shouldBe 1
        result.first().title shouldBe "Frieren"
        result.first().provider shouldBe "shikimori_trend"
        shikimori.fetchCalled shouldBe true
        jikan.fetchCalled shouldBe false
        anilist.fetchCalled shouldBe false
    }

    @Test
    fun `anime falls back to Jikan and AniList when Shikimori fails`() = runTest {
        val anilistItem =
            DiscoveryTrendingItem("Bleach", "bleach", "http://ani/1.jpg", 2L, "current", provider = "anilist_trend")
        val shikimori = MockTrendingSource("shikimori", shouldFail = true)
        val jikan = MockTrendingSource("jikan", items = emptyList())
        val anilist = MockTrendingSource("anilist", items = listOf(anilistItem))

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = MockTrendingSource("mangadex"),
            jikan = jikan,
            anilist = anilist,
        )

        val result = composite.fetch(DiscoveryMediaType.ANIME)
        result.size shouldBe 1
        result.first().title shouldBe "Bleach"
        shikimori.fetchCalled shouldBe true
        jikan.fetchCalled shouldBe true
        anilist.fetchCalled shouldBe true
    }

    @Test
    fun `manga queries MangaDex first and returns when successful`() = runTest {
        val mangadexItem =
            DiscoveryTrendingItem(
                "Chainsaw Man",
                "chainsaw man",
                "http://md/1.jpg",
                0L,
                null,
                provider = "mangadex_trend",
            )
        val mangadex = MockTrendingSource("mangadex", items = listOf(mangadexItem))
        val shikimori = MockTrendingSource("shikimori")
        val anilist = MockTrendingSource("anilist")

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = anilist,
        )

        val result = composite.fetch(DiscoveryMediaType.MANGA)
        result.size shouldBe 1
        result.first().title shouldBe "Chainsaw Man"
        result.first().provider shouldBe "mangadex_trend"
        mangadex.fetchCalled shouldBe true
        shikimori.fetchCalled shouldBe false
        anilist.fetchCalled shouldBe false
    }

    @Test
    fun `manga falls back to Shikimori when MangaDex fails`() = runTest {
        val shikimoriItem =
            DiscoveryTrendingItem("Berserk", "berserk", "http://shiki/2.jpg", 2L, null, provider = "shikimori_trend")
        val mangadex = MockTrendingSource("mangadex", shouldFail = true)
        val shikimori = MockTrendingSource("shikimori", items = listOf(shikimoriItem))

        val composite = CompositeTrendingSource(
            shikimori = shikimori,
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = MockTrendingSource("anilist"),
        )

        val result = composite.fetch(DiscoveryMediaType.MANGA)
        result.size shouldBe 1
        result.first().title shouldBe "Berserk"
        mangadex.fetchCalled shouldBe true
        shikimori.fetchCalled shouldBe true
    }

    @Test
    fun `fetchMeta returns metadata from available provider`() = runTest {
        val expectedMeta = DiscoveryMeta("Awesome story", listOf("Action", "Fantasy"), "Alt Title")
        val mangadex = MockTrendingSource("mangadex", meta = expectedMeta)

        val composite = CompositeTrendingSource(
            shikimori = MockTrendingSource("shikimori", shouldFail = true),
            mangadex = mangadex,
            jikan = MockTrendingSource("jikan"),
            anilist = MockTrendingSource("anilist"),
        )

        val result = composite.fetchMeta("Solo Leveling", DiscoveryMediaType.MANGA)
        result shouldBe expectedMeta
        mangadex.fetchMetaCalled shouldBe true
    }
}
