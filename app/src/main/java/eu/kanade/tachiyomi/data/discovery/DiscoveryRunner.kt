package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface DiscoverySeedSources {
    suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput>
    suspend fun historyCleanTitles(mediaType: DiscoveryMediaType): Set<String>
}

/**
 * Оркестратор фонового обновления ленты «Для тебя»:
 * сиды → строители рядов → [DiscoveryCoordinator] → персист успешных рядов.
 * Провалившийся ряд НЕ затирает свой кэш (в [DiscoveryFeed.rows] его нет).
 */
class DiscoveryRunner(
    private val repository: DiscoveryRepository,
    private val preferences: DiscoveryPreferences,
    private val seedSources: DiscoverySeedSources,
    private val coordinatorFactory: (List<DiscoveryRowBuilder>) -> DiscoveryCoordinator = { DiscoveryCoordinator(it) },
    private val seedSelector: DiscoverySeedSelector = DiscoverySeedSelector(),
    private val trendingFactory: () -> DiscoveryTrendingSource = { CompositeTrendingSource() },
    private val sourcePreferencesProvider: () -> SourcePreferences = { Injekt.get() },
    private val suggestionCoordinatorFactory: () -> SuggestionCoordinator = {
        SuggestionCoordinator(sourcePreferencesProvider())
    },
    private val sourceCatalog: DiscoverySourceCatalog = AppDiscoverySourceCatalog(),
    /** Сигнал о провале рядов для UI-баннера «показан кэш»; дефолт персистит CSV в prefs. */
    private val failedRowsSink: suspend (DiscoveryMediaType, Set<DiscoveryRowType>) -> Unit = { media, rows ->
        preferences.lastFailedRows(media).set(rows.joinToString(",") { it.key })
    },
    private val fallbackSourceIdProvider: (DiscoveryMediaType) -> Long = { mediaType ->
        runCatching {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>()
                    .getOnlineSources().firstOrNull()?.id ?: -1L
                DiscoveryMediaType.MANGA -> Injekt.get<tachiyomi.domain.source.manga.service.MangaSourceManager>()
                    .getOnlineSources().firstOrNull()?.id ?: -1L
                DiscoveryMediaType.NOVEL -> Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>()
                    .getOnlineSources().firstOrNull()?.id ?: -1L
            }
        }.getOrDefault(-1L)
    },
) {

    suspend fun run(
        mediaTypes: List<DiscoveryMediaType> = DiscoveryMediaType.entries,
        isManualRefresh: Boolean = false,
    ) {
        if (!preferences.discoveryEnabled().get()) return
        for (mediaType in mediaTypes) {
            try {
                runFor(mediaType, isManualRefresh = isManualRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[DiscoveryRunner] $mediaType FAILED: ${e.message}" }
            }
        }
    }

    private suspend fun trackTitles(mediaType: DiscoveryMediaType, entryId: Long): List<String> =
        when (mediaType) {
            DiscoveryMediaType.ANIME ->
                Injekt.get<tachiyomi.domain.track.anime.repository.AnimeTrackRepository>()
                    .getTracksByAnimeId(entryId).map { it.title }
            DiscoveryMediaType.MANGA ->
                Injekt.get<tachiyomi.domain.track.manga.repository.MangaTrackRepository>()
                    .getTracksByMangaId(entryId).map { it.title }
            DiscoveryMediaType.NOVEL ->
                Injekt.get<tachiyomi.domain.track.novel.repository.NovelTrackRepository>()
                    .getTracksByNovelId(entryId).map { it.title }
        }

    private suspend fun runFor(mediaType: DiscoveryMediaType, isManualRefresh: Boolean = false) {
        val candidates = seedSources.candidates(mediaType)
        val seedOffset = if (isManualRefresh) 2 else 0
        val seeds = seedSelector.select(
            candidates,
            SeedSettings(
                maxSeeds = preferences.seedCount().get(),
                useCompleted = preferences.seedCompleted().get(),
                useActive14 = preferences.seedActive14().get(),
                useAdded = preferences.seedAdded().get(),
                completedWindowDays = preferences.seedCompletedDays().get().toLong(),
                activeWindowDays = preferences.seedActiveDays().get().toLong(),
            ),
            offset = seedOffset,
        )
        val sourcePreferences = sourcePreferencesProvider()
        val preferredSourceId = when (mediaType) {
            DiscoveryMediaType.ANIME -> sourcePreferences.lastUsedAnimeSource().get()
            DiscoveryMediaType.MANGA -> sourcePreferences.lastUsedMangaSource().get()
            DiscoveryMediaType.NOVEL -> sourcePreferences.lastUsedNovelSource().get()
        }
        val sourceId = if (preferredSourceId > 0) preferredSourceId else fallbackSourceIdProvider(mediaType)

        val seedsWithTracks = seeds.map { seed ->
            val trackTitles = runCatching { trackTitles(mediaType, seed.entryId) }.getOrDefault(emptyList())
            if (trackTitles.isEmpty()) seed else seed.copy(altTitles = (seed.altTitles + trackTitles).distinct())
        }

        val currentSuggestions = runCatching { repository.subscribe(mediaType).firstOrNull() }.getOrNull().orEmpty()
        val recentCleanTitles = if (isManualRefresh) {
            currentSuggestions.mapTo(HashSet()) { it.cleanTitle }
        } else {
            emptySet()
        }
        val pageOffset = if (isManualRefresh) 2 else 1

        val context = DiscoveryBuildContext(
            mediaType = mediaType,
            seeds = seedsWithTracks,
            libraryCleanTitles = candidates.mapTo(HashSet()) { normalizeDiscoveryTitle(it.title) },
            historyCleanTitles = seedSources.historyCleanTitles(mediaType),
            hiddenCleanTitles = repository.getHiddenTitles(mediaType),
            tasteProfile = buildTasteProfile(candidates),
            blacklistedTags = repository.getBlacklistedTags(mediaType),
            sourceId = sourceId,
            // C1: ряд SOURCE строится из топ-3 источников по весу библиотеки.
            sourceIds = rankSourceIds(candidates),
            recentCleanTitles = recentCleanTitles,
            pageOffset = pageOffset,
        )
        // Выключенные в настройках ряды: чистим их записи в БД, чтобы UI не показывал «зомби».
        if (!preferences.rowLikeEnabled().get()) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.LIKE, emptyList())
        }
        if (!preferences.rowTasteEnabled().get()) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.TASTE, emptyList())
        }
        if (!preferences.rowTrendEnabled().get()) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.TREND, emptyList())
        }
        if (!preferences.rowSourceEnabled().get()) {
            repository.replaceRows(mediaType, tachiyomi.domain.discovery.model.DiscoveryRowType.SOURCE, emptyList())
        }
        val builders = buildList {
            if (preferences.rowLikeEnabled().get()) {
                add(DiscoveryLikeRowBuilder(suggestionCoordinatorFactory()))
            }
            if (preferences.rowTasteEnabled().get()) {
                add(
                    DiscoveryTasteRowBuilder(
                        trending = trendingFactory(),
                        catalog = sourceCatalog,
                        sortProvider = {
                            if (preferences.trendSort().get() == "score") TrendSort.SCORE else TrendSort.POPULARITY
                        },
                    ),
                )
            }
            if (preferences.rowTrendEnabled().get()) {
                add(
                    DiscoveryTrendRowBuilder(
                        trending = trendingFactory(),
                        catalog = sourceCatalog,
                        seasonProvider = {
                            when (preferences.trendSeason().get()) {
                                "next" -> TrendSeason.NEXT
                                "both" -> TrendSeason.BOTH
                                else -> TrendSeason.CURRENT
                            }
                        },
                        sortProvider = {
                            if (preferences.trendSort().get() == "score") TrendSort.SCORE else TrendSort.POPULARITY
                        },
                    ),
                )
            }
            if (preferences.rowSourceEnabled().get()) {
                add(DiscoverySourceRowBuilder(sourceCatalog))
            }
        }
        if (builders.isEmpty()) {
            failedRowsSink(mediaType, emptySet())
            return
        }

        val coordinator = coordinatorFactory(builders)
        val feed = coordinator.streamFeed(context) { rowType, items ->
            // Пустой ряд НЕ перезаписывает кэш: прежняя подборка живёт до следующего
            // успешного непустого результата (защита от «всё пропало» при деградации провайдеров).
            if (items.isEmpty()) {
                logcat { "[DiscoveryRunner] $mediaType row $rowType empty — cache preserved" }
                return@streamFeed
            }
            repository.replaceRows(
                mediaType = mediaType,
                rowType = rowType,
                items = items.map { item ->
                    DiscoverySuggestion(
                        id = 0L,
                        mediaType = mediaType,
                        rowType = rowType,
                        title = item.title,
                        cleanTitle = item.cleanTitle,
                        coverUrl = item.coverUrl,
                        reason = item.reason,
                        seedTitle = item.seedTitle,
                        provider = item.provider,
                        score = item.score,
                        // Перезаписывается индексом списка внутри replaceRows.
                        position = 0L,
                        createdAt = System.currentTimeMillis(),
                    )
                },
            )
        }
        logcat {
            "[DiscoveryRunner] $mediaType done: rows=${feed.rows.mapValues { it.value.size }} failed=${feed.failedRows}"
        }
        failedRowsSink(mediaType, feed.failedRows)
    }
}
