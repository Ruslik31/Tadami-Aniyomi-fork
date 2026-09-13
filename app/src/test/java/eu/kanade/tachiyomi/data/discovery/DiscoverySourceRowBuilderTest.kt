package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.io.IOException

class DiscoverySourceRowBuilderTest {

    private class FakeCatalog(
        private val itemsBySource: Map<Long, List<DiscoveryRowItem>> = emptyMap(),
        private val failing: Set<Long> = emptySet(),
    ) : DiscoverySourceCatalog {
        val requested = mutableListOf<Long>()

        override suspend fun popular(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
        ): List<DiscoveryRowItem> {
            requested += sourceId
            if (sourceId in failing) throw IOException("boom $sourceId")
            return itemsBySource[sourceId].orEmpty()
        }

        override suspend fun popularWithGenres(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            genres: List<String>,
        ): List<DiscoveryRowItem> = emptyList()

        override suspend fun latest(
            mediaType: DiscoveryMediaType,
            sourceId: Long,
            page: Int,
        ): List<DiscoveryRowItem> = emptyList()
    }

    private fun items(source: String, n: Int) = (1..n).map {
        DiscoveryRowItem("$source Item$it", "$source item$it".lowercase(), null, null, null, source, 1.0 - it * 0.01)
    }

    private fun context(
        sourceIds: List<Long> = emptyList(),
        sourceId: Long = -1L,
        pageOffset: Int = 1,
    ) = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.MANGA,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
        sourceId = sourceId,
        sourceIds = sourceIds,
        pageOffset = pageOffset,
    )

    @Test
    fun `single weighted source fills up to row cap`() = runTest {
        val catalog = FakeCatalog(mapOf(1L to items("S1", 25)))
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L)))
        result.size shouldBe 20
        result.all { it.provider == "S1" } shouldBe true
    }

    @Test
    fun `three sources interleave round-robin and cap at 20`() = runTest {
        val catalog = FakeCatalog(
            mapOf(
                1L to items("S1", 10),
                2L to items("S2", 10),
                3L to items("S3", 10),
            ),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L, 2L, 3L)))
        result.size shouldBe 20
        result.take(3).map { it.provider } shouldBe listOf("S1", "S2", "S3")
        result.count { it.provider == "S1" } shouldBe 7
        result.count { it.provider == "S2" } shouldBe 7
        result.count { it.provider == "S3" } shouldBe 6
    }

    @Test
    fun `failing source is skipped without failing the row`() = runTest {
        val catalog = FakeCatalog(
            itemsBySource = mapOf(2L to items("S2", 10)),
            failing = setOf(1L),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L, 2L)))
        result.size shouldBe 10
        result.all { it.provider == "S2" } shouldBe true
    }

    @Test
    fun `manual refresh rotates source order`() = runTest {
        val catalog = FakeCatalog(
            mapOf(
                1L to items("S1", 10),
                2L to items("S2", 10),
                3L to items("S3", 10),
            ),
        )
        val result = DiscoverySourceRowBuilder(catalog).build(
            context(sourceIds = listOf(1L, 2L, 3L), pageOffset = 2),
        )
        result.take(3).map { it.provider } shouldBe listOf("S2", "S3", "S1")
    }

    @Test
    fun `empty weighted list falls back to legacy single sourceId`() = runTest {
        val catalog = FakeCatalog(mapOf(7L to items("Legacy", 5)))
        val result = DiscoverySourceRowBuilder(catalog).build(context(sourceId = 7L))
        result.size shouldBe 5
        catalog.requested shouldBe listOf(7L)
    }

    @Test
    fun `all sources failing throws so cache is preserved`() = runTest {
        val catalog = FakeCatalog(failing = setOf(1L, 2L))
        val result = runCatching {
            DiscoverySourceRowBuilder(catalog).build(context(sourceIds = listOf(1L, 2L)))
        }
        result.isFailure shouldBe true
    }

    @Test
    fun `no sources at all yields empty row`() = runTest {
        val result = DiscoverySourceRowBuilder(FakeCatalog()).build(context())
        result shouldBe emptyList()
    }

    @Test
    fun `builder row type is SOURCE`() {
        DiscoverySourceRowBuilder(FakeCatalog()).rowType shouldBe DiscoveryRowType.SOURCE
    }
}
