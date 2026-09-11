package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import tachiyomi.domain.discovery.model.DiscoveryMediaType

/**
 * Общий интерфейс провайдера трендовых тайтлов, сезонных подборок
 * и метаданных для экрана «Для тебя» и превью-шторки.
 */
interface DiscoveryTrendingSource {

    suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason = TrendSeason.CURRENT,
        sort: TrendSort = TrendSort.POPULARITY,
        page: Int = 1,
    ): List<DiscoveryTrendingItem>

    suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort = TrendSort.POPULARITY,
        page: Int = 1,
    ): List<DiscoveryTrendingItem>

    suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta?
}

/**
 * fetchMeta ищет тайтл по имени и раньше брал ПЕРВЫЙ результат поиска — при
 * коллизиях названий в превью попадал чужой синопсис. Проверяем совпадение:
 * scoreMatch ≥ [META_MATCH_THRESHOLD] (exact/prefix/contains) по любому из
 * вариантов названия. Нет совпадения → меты нет (лучше пусто, чем чужое).
 */
internal const val META_MATCH_THRESHOLD = 50

internal fun metaMatchesTitle(queryTitle: String, candidateTitles: List<String?>): Boolean =
    candidateTitles.any { candidate ->
        !candidate.isNullOrBlank() &&
            SuggestionTitleResolver.scoreMatch(queryTitle, candidate) >= META_MATCH_THRESHOLD
    }
