package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import java.io.IOException

class DiscoveryCoordinatorTest {

    private fun item(title: String, score: Double = 0.0) = DiscoveryRowItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = null,
        seedTitle = null,
        provider = "test",
        score = score,
    )

    private fun builder(type: DiscoveryRowType, items: List<DiscoveryRowItem>, fail: Boolean = false) =
        object : DiscoveryRowBuilder {
            override val rowType = type
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                if (fail) throw IOException("boom")
                return items
            }
        }

    private val baseContext = DiscoveryBuildContext(
        mediaType = DiscoveryMediaType.NOVEL,
        seeds = emptyList(),
        libraryCleanTitles = emptySet(),
        historyCleanTitles = emptySet(),
        hiddenCleanTitles = emptySet(),
    )

    @Test
    fun `library history and hidden titles are filtered out`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, listOf(item("Overlord"), item("Hidden One"), item("In Library")))
        val coordinator = DiscoveryCoordinator(listOf(like))
        val feed = coordinator.buildFeed(
            baseContext.copy(
                libraryCleanTitles = setOf("in library"),
                hiddenCleanTitles = setOf("hidden one"),
            ),
        )
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Overlord")
        feed.failedRows shouldBe emptySet()
    }

    @Test
    fun `cross-row dedup keeps higher priority row`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, listOf(item("Dup")))
        val trend = builder(DiscoveryRowType.TREND, listOf(item("Dup"), item("Unique Trend")))
        val feed = DiscoveryCoordinator(listOf(trend, like)).buildFeed(baseContext)
        feed.rows[DiscoveryRowType.LIKE]?.map { it.title } shouldBe listOf("Dup")
        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Unique Trend")
    }

    @Test
    fun `row limit caps items preserving order`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, (1..25).map { item("T$it") })
        val feed = DiscoveryCoordinator(listOf(like), rowLimit = 20).buildFeed(baseContext)
        feed.rows[DiscoveryRowType.LIKE]?.size shouldBe 20
    }

    @Test
    fun `failing builder yields empty row and failedRows marker, others survive`() = runTest {
        val like = builder(DiscoveryRowType.LIKE, emptyList(), fail = true)
        val trend = builder(DiscoveryRowType.TREND, listOf(item("Ok")))
        val feed = DiscoveryCoordinator(listOf(like, trend)).buildFeed(baseContext)
        feed.failedRows shouldBe setOf(DiscoveryRowType.LIKE)
        feed.rows[DiscoveryRowType.TREND]?.map { it.title } shouldBe listOf("Ok")
    }
}
