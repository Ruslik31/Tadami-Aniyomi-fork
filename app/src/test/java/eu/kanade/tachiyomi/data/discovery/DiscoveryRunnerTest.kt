package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import java.io.IOException

class DiscoveryRunnerTest {

    private class FakeRepository : DiscoveryRepository {
        val replaced = mutableListOf<Triple<DiscoveryMediaType, DiscoveryRowType, List<DiscoverySuggestion>>>()
        private val flow = MutableStateFlow<List<DiscoverySuggestion>>(emptyList())
        override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> = flow
        override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun replaceRows(
            mediaType: DiscoveryMediaType,
            rowType: DiscoveryRowType,
            items: List<DiscoverySuggestion>,
        ) {
            replaced += Triple(mediaType, rowType, items)
        }

        override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> = setOf("hidden one")
        override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) {}
        override suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String) {}
        override suspend fun clearHidden(mediaType: DiscoveryMediaType) {}
        override suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long? = null
    }

    private class FakeSeedSources : DiscoverySeedSources {
        override suspend fun candidates(mediaType: DiscoveryMediaType) = listOf(
            DiscoverySeedInput(
                entryId = 1,
                title = "Seed One",
                genres = listOf("Drama"),
                isCompleted = true,
                completedAt = 1L,
                lastInteraction = 1L,
            ),
            // Присутствует в библиотеке → одноимённый совет обязан отфильтроваться.
            DiscoverySeedInput(
                entryId = 2,
                title = "In Library",
                genres = listOf("Drama"),
                isCompleted = true,
                completedAt = 1L,
                lastInteraction = 1L,
            ),
        )

        override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = setOf("in history")
    }

    private fun testSourcePrefs() = SourcePreferences(InMemoryPreferenceStore())

    private fun fakeBuilders(failLike: Boolean) = listOf(
        object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.LIKE
            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
                if (failLike) throw IOException("boom")
                return listOf(
                    DiscoveryRowItem("Fresh Pick", "fresh pick", null, null, "Seed One", "anilist", 1.0),
                    DiscoveryRowItem("In Library", "in library", null, null, "Seed One", "anilist", 0.5),
                    DiscoveryRowItem("Hidden One", "hidden one", null, null, "Seed One", "anilist", 0.4),
                )
            }
        },
    )

    @Test
    fun `runner persists successful rows and filters library and hidden`() = runTest {
        val repo = FakeRepository()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = false)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        val (media, row, items) = repo.replaced.single()
        media shouldBe DiscoveryMediaType.NOVEL
        row shouldBe DiscoveryRowType.LIKE
        items.map { it.title } shouldBe listOf("Fresh Pick")
        items.single().seedTitle shouldBe "Seed One"
    }

    @Test
    fun `failing row is not persisted (cache preserved)`() = runTest {
        val repo = FakeRepository()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = true)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        repo.replaced shouldBe emptyList()
    }

    @Test
    fun `empty successful row does not wipe cache`() = runTest {
        val repo = FakeRepository()
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = DiscoveryPreferences(InMemoryPreferenceStore()),
            seedSources = FakeSeedSources(),
            coordinatorFactory = {
                DiscoveryCoordinator(
                    listOf(
                        object : DiscoveryRowBuilder {
                            override val rowType = DiscoveryRowType.LIKE
                            override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> =
                                emptyList()
                        },
                    ),
                )
            },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        repo.replaced shouldBe emptyList()
    }

    @Test
    fun `disabled discovery performs no work`() = runTest {
        val repo = FakeRepository()
        // InMemoryPreferenceStore не сохраняет set() после выдачи Preference —
        // значение задаётся через initialPreferences конструктора.
        val prefs = DiscoveryPreferences(
            InMemoryPreferenceStore(
                sequenceOf(InMemoryPreferenceStore.InMemoryPreference("discovery_enabled", false, true)),
            ),
        )
        val runner = DiscoveryRunner(
            repository = repo,
            preferences = prefs,
            seedSources = FakeSeedSources(),
            coordinatorFactory = { DiscoveryCoordinator(fakeBuilders(failLike = false)) },
            sourcePreferencesProvider = ::testSourcePrefs,
        )
        runner.run(listOf(DiscoveryMediaType.NOVEL))
        repo.replaced shouldBe emptyList()
    }
}
