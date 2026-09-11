package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.io.IOException

class DiscoveryTasteRowBuilderTest {

    private class FakeTrending(
        private val genreItems: List<DiscoveryTrendingItem> = emptyList(),
        private val shouldFail: Boolean = false,
    ) : DiscoveryTrendingSource {
        override suspend fun fetch(
            mediaType: DiscoveryMediaType,
            season: TrendSeason,
            sort: TrendSort,
            page: Int,
        ): List<DiscoveryTrendingItem> = emptyList()

        override suspend fun fetchByGenres(
            mediaType: DiscoveryMediaType,
            genres: List<String>,
            sort: TrendSort,
            page: Int,
        ): List<DiscoveryTrendingItem> {
            if (shouldFail) throw IOException("trending boom")
            return genreItems
        }

        override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? = null
    }

    private class FakeCatalog(
        private val genreItems: List<DiscoveryRowItem> = emptyList(),
        private val shouldFail: Boolean = false,
    ) : DiscoverySourceCatalog {
        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
        ): List<DiscoveryRowItem> = emptyList()

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
        ): List<DiscoveryRowItem> {
            if (shouldFail) throw IOException("catalog boom")
            return genreItems
        }

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
        ): List<DiscoveryRowItem> = emptyList()
    }

    private fun context(profile: List<Pair<String, Double>> = listOf("Fantasy" to 2.0)) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.MANGA,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        tasteProfile = profile,
        sourceId = 7L,
    )

    private fun builder(trending: DiscoveryTrendingSource, catalog: DiscoverySourceCatalog) =
        DiscoveryTasteRowBuilder(trending, catalog, sortProvider = { TrendSort.POPULARITY })

    @Test
    fun `empty taste profile yields empty row without calls`() = runTest {
        val result = builder(FakeTrending(shouldFail = true), FakeCatalog(shouldFail = true))
            .build(context(profile = emptyList()))
        result shouldBe emptyList()
    }

    @Test
    fun `trending items are scored by genre overlap and combined with filtered source items`() = runTest {
        val trending = FakeTrending(
            genreItems = listOf(
                DiscoveryTrendingItem("Matched", "matched", null, 1L, null, genres = listOf("Fantasy")),
                DiscoveryTrendingItem("Unmatched", "unmatched", null, 2L, null, genres = listOf("Comedy")),
            ),
        )
        val catalog = FakeCatalog(
            genreItems = listOf(DiscoveryRowItem("FromSource", "fromsource", null, null, null, "MangaHub", 1.0)),
        )
        val result = builder(trending, catalog).build(context())
        result.map { it.title } shouldBe listOf("Matched", "FromSource", "Unmatched")
        result.first { it.title == "Matched" }.score shouldBe 2.0
        result.first { it.title == "FromSource" }.reason shouldBe "Fantasy"
        result.first { it.title == "FromSource" }.score shouldBe 0.6
        result.first { it.title == "Unmatched" }.score shouldBe 0.0
    }

    @Test
    fun `both providers failed without results throws so cache is preserved`() = runTest {
        val result = runCatching {
            builder(FakeTrending(shouldFail = true), FakeCatalog(shouldFail = true)).build(context())
        }
        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe IOException("taste sources failed without results")
    }

    @Test
    fun `failed trending with successful catalog does not throw`() = runTest {
        val catalog = FakeCatalog(
            genreItems = listOf(DiscoveryRowItem("OnlySource", "onlysource", null, null, null, "MangaHub", 0.9)),
        )
        val result = builder(FakeTrending(shouldFail = true), catalog).build(context())
        result.map { it.title } shouldBe listOf("OnlySource")
    }

    @Test
    fun `builder row type is TASTE`() {
        builder(FakeTrending(), FakeCatalog()).rowType shouldBe DiscoveryRowType.TASTE
    }
}
