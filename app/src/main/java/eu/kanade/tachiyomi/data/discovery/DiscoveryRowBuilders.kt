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
 * Ряд «Тренды и сезон»: AniList GraphQL (без ключа), сезон для аниме и
 * популярность для манги/новелл.
 */
class DiscoveryTrendRowBuilder(
    private val trending: AniListTrendingSource,
    private val seasonProvider: () -> TrendSeason,
    private val sortProvider: () -> TrendSort,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.TREND

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> =
        trending.fetch(context.mediaType, seasonProvider(), sortProvider()).map { item ->
            DiscoveryRowItem(
                title = item.title,
                cleanTitle = item.cleanTitle,
                coverUrl = item.coverUrl,
                // Payload "current"/"next"/null — шаблон строки выбирается на рендере.
                reason = item.seasonLabel,
                seedTitle = null,
                provider = "anilist_trend",
                score = 0.0,
            )
        }
}

/**
 * Ряд «Твой вкус»: жанровый профиль библиотеки/истории → AniList `genre_in`
 * + жанровый фильтр каталога выбранного источника; скор = совпадение жанров.
 */
class DiscoveryTasteRowBuilder(
    private val trending: AniListTrendingSource,
    private val catalog: DiscoverySourceCatalog,
    private val sortProvider: () -> TrendSort,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.TASTE

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        val profile = context.tasteProfile
        if (profile.isEmpty()) return emptyList()
        val genreNames = profile.take(4).map { it.first }
        val aniListResult = runCatching { trending.fetchByGenres(context.mediaType, genreNames, sortProvider()) }
        val sourceResult = if (context.sourceId > 0) {
            runCatching { catalog.popularWithGenres(context.mediaType, context.sourceId, genreNames) }
        } else {
            null
        }
        val fromAniList = aniListResult.getOrNull().orEmpty().map { item ->
            DiscoveryRowItem(
                title = item.title,
                cleanTitle = item.cleanTitle,
                coverUrl = item.coverUrl,
                reason = matchedGenres(item.genres, profile).joinToString(", "),
                seedTitle = null,
                provider = "anilist",
                score = tasteScore(item.genres, profile),
            )
        }
        val fromSource = sourceResult?.getOrNull().orEmpty().map { item ->
            item.copy(
                reason = genreNames.take(2).joinToString(", "),
                score = 0.5 + item.score * 0.1,
            )
        }
        val combined = (fromAniList + fromSource)
        val anyFailed = aniListResult.isFailure || (sourceResult?.isFailure ?: false)
        if (combined.isEmpty() && anyFailed) {
            throw IOException("taste sources failed without results")
        }
        return combined
            .sortedByDescending { it.score }
            .take(8)
    }
}

/** Ряд «Источник»: popular-витрина выбранного источника, топ-3. */
class DiscoverySourceRowBuilder(
    private val catalog: DiscoverySourceCatalog,
) : DiscoveryRowBuilder {

    override val rowType = DiscoveryRowType.SOURCE

    override suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem> {
        if (context.sourceId <= 0) return emptyList()
        return catalog.popular(context.mediaType, context.sourceId).take(3)
    }
}
