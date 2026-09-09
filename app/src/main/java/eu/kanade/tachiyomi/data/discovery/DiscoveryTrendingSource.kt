package eu.kanade.tachiyomi.data.discovery

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
