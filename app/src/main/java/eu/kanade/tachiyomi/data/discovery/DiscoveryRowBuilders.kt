package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import java.io.IOException

/**
 * Ряд «Похоже на X»: переиспользует существующий [SuggestionCoordinator]
 * (AniList / MAL-Jikan / MangaUpdates / NovelUpdates) на каждый выбранный сид.
 */
class DiscoveryLikeRowBuilder(
    private val suggestionCoordinator: SuggestionCoordinator,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.LIKE

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val mediaType = when (context.mediaType) {
            DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
            DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
            DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
        }
        var fullyFailedSeeds = 0
        val perSeed = context.seeds.map { seed ->
            val suggestionSeed = SuggestionSeed(
                mediaType = mediaType,
                primaryTitle = seed.title,
                candidateTitles = (listOf(seed.title) + seed.altTitles).distinct(),
                description = seed.description,
                author = seed.author,
                genres = seed.genres.ifEmpty { null },
            )
            val result = suggestionCoordinator.fetchSuggestions(suggestionSeed, limit = 10)
            if (result.items.isEmpty() && result.attemptedSources > 0 &&
                result.failedSources == result.attemptedSources
            ) {
                fullyFailedSeeds++
            }
            result.items.map { item ->
                DiscoveryRowItem(
                    title = item.title,
                    cleanTitle = normalizeDiscoveryTitle(item.title),
                    coverUrl = item.thumbnailUrl,
                    // Локализованный текст «Похоже на „X“» композится на рендере из seedTitle.
                    reason = null,
                    seedTitle = seed.title,
                    provider = item.providerName,
                    score = item.bestMatchScoreFor(suggestionSeed).toDouble(),
                )
            }
        }
        val merged = mergeSeedResults(perSeed)
        if (merged.isEmpty() && context.seeds.isNotEmpty() && fullyFailedSeeds == context.seeds.size) {
            throw IOException("all suggestion providers failed for every seed")
        }
        return merged
    }
}

/**
 * Ряд «Тренды и сезон»: агрегатор трендов (Shikimori, MangaDex, Jikan, AniList)
 * и свежие обновления установленных источников.
 */
class DiscoveryTrendRowBuilder(
    private val trending: DiscoveryTrendingSource,
    private val catalog: DiscoverySourceCatalog,
    private val seasonProvider: () -> TrendSeason,
    private val sortProvider: () -> TrendSort,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.TREND

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val effectiveSort = when (context.mediaType) {
            DiscoveryMediaType.ANIME -> sortProvider()
            // Для манги и новелл запрашиваем реальные тренды (TRENDING_DESC),
            // чтобы не дублировать статический all-time топ ряда TASTE
            DiscoveryMediaType.MANGA, DiscoveryMediaType.NOVEL -> TrendSort.TRENDING
        }

        // Для новелл приоритет отдаём установленному источнику пользователя (InkStory, Ranobe и др.),
        // где новеллы реально можно читать прямо в приложении.
        if (context.mediaType == DiscoveryMediaType.NOVEL && context.sourceId > 0) {
            val fromSource = runCatching {
                catalog.latest(context.mediaType, context.sourceId, page = context.pageOffset)
            }.getOrNull().orEmpty().map { item ->
                item.copy(
                    reason = "source",
                    score = 0.0,
                )
            }
            if (fromSource.isNotEmpty()) {
                return fromSource
            }
        }

        val fromTrending = runCatching {
            trending.fetch(
                mediaType = context.mediaType,
                season = seasonProvider(),
                sort = effectiveSort,
                page = context.pageOffset,
            )
        }.getOrNull().orEmpty().map { item ->
            DiscoveryRowItem(
                title = item.title,
                cleanTitle = item.cleanTitle,
                coverUrl = item.coverUrl,
                // Payload "current"/"next"/"source"/null — шаблон строки выбирается на рендере.
                reason = item.seasonLabel,
                seedTitle = null,
                provider = item.provider,
                score = 0.0,
            )
        }

        if (fromTrending.isNotEmpty()) {
            return fromTrending
        }

        // Фолбэк при недоступности трендов:
        // Запрашиваем «Свежее» (latest updates) из активного/установленного источника пользователя!
        val sourceId = context.sourceId
        if (sourceId > 0) {
            val fromSource = runCatching {
                catalog.latest(context.mediaType, sourceId, page = context.pageOffset)
            }.getOrNull().orEmpty().map { item ->
                item.copy(
                    reason = "source",
                    score = 0.0,
                )
            }
            if (fromSource.isNotEmpty()) {
                return fromSource
            }
        }

        return emptyList()
    }
}

/**
 * Ряд «Твой вкус»: жанровый профиль библиотеки/истории → тренды по жанрам
 * + жанровый фильтр каталога выбранного источника; скор = совпадение жанров.
 */
class DiscoveryTasteRowBuilder(
    private val trending: DiscoveryTrendingSource,
    private val catalog: DiscoverySourceCatalog,
    private val sortProvider: () -> TrendSort,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.TASTE

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val profile = context.tasteProfile
        if (profile.isEmpty()) return emptyList()
        val genreNames = profile.take(4).map { it.first }
        val trendingResult = runCatching {
            trending.fetchByGenres(context.mediaType, genreNames, sortProvider(), page = context.pageOffset)
        }
        val sourceResult = if (context.sourceId > 0) {
            runCatching { catalog.popularWithGenres(context.mediaType, context.sourceId, genreNames) }
        } else {
            null
        }
        val fromTrending = trendingResult.getOrNull().orEmpty().map { item ->
            DiscoveryRowItem(
                title = item.title,
                cleanTitle = item.cleanTitle,
                coverUrl = item.coverUrl,
                reason = matchedGenres(item.genres, profile).joinToString(", "),
                seedTitle = null,
                provider = item.provider,
                score = tasteScore(item.genres, profile),
            )
        }
        val fromSource = sourceResult?.getOrNull().orEmpty().map { item ->
            item.copy(
                reason = genreNames.take(2).joinToString(", "),
                score = 0.5 + item.score * 0.1,
            )
        }
        val combined = (fromTrending + fromSource)
        val anyFailed = trendingResult.isFailure || (sourceResult?.isFailure ?: false)
        if (combined.isEmpty() && anyFailed) {
            throw IOException("taste sources failed without results")
        }
        return combined
            .sortedByDescending { it.score }
            .take(20)
    }
}

/** Ряд «Источник»: popular-витрина выбранного источника, топ-20. */
class DiscoverySourceRowBuilder(
    private val catalog: DiscoverySourceCatalog,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.SOURCE

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        if (context.sourceId <= 0) return emptyList()
        return catalog.popular(context.mediaType, context.sourceId).take(20)
    }
}
