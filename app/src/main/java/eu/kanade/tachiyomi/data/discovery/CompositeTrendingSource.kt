package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class CompositeTrendingSource(
    private val shikimori: DiscoveryTrendingSource = ShikimoriTrendingSource(),
    private val mangadex: DiscoveryTrendingSource = MangaDexTrendingSource(),
    private val jikan: DiscoveryTrendingSource = JikanTrendingSource(),
    private val anilist: DiscoveryTrendingSource = AniListTrendingSource(),
) : DiscoveryTrendingSource {

    override suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason,
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        val providers: List<Pair<String, suspend () -> List<DiscoveryTrendingItem>>> = when (mediaType) {
            DiscoveryMediaType.ANIME -> listOf(
                "shikimori" to suspend { shikimori.fetch(mediaType, season, sort, page) },
                "jikan" to suspend { jikan.fetch(mediaType, season, sort, page) },
                "anilist" to suspend { anilist.fetch(mediaType, season, sort, page) },
            )
            DiscoveryMediaType.MANGA -> listOf(
                "mangadex" to suspend { mangadex.fetch(mediaType, season, sort, page) },
                "shikimori" to suspend { shikimori.fetch(mediaType, season, sort, page) },
                "anilist" to suspend { anilist.fetch(mediaType, season, sort, page) },
            )
            DiscoveryMediaType.NOVEL -> listOf(
                "anilist" to suspend { anilist.fetch(mediaType, season, sort, page) },
            )
        }
        return fetchWithFallback(providers, mediaType)
    }

    private suspend fun fetchWithFallback(
        providers: List<Pair<String, suspend () -> List<DiscoveryTrendingItem>>>,
        mediaType: DiscoveryMediaType,
    ): List<DiscoveryTrendingItem> {
        for ((name, action) in providers) {
            try {
                val results = action()
                if (results.isNotEmpty()) {
                    logcat { "[CompositeTrending] $mediaType: provider '$name' returned ${results.size} items" }
                    return results
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[CompositeTrending] $mediaType: provider '$name' failed: ${e.message}" }
            }
        }
        return emptyList()
    }

    override suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        val providers: List<Pair<String, suspend () -> List<DiscoveryTrendingItem>>> = when (mediaType) {
            DiscoveryMediaType.ANIME -> listOf(
                "shikimori" to suspend { shikimori.fetchByGenres(mediaType, genres, sort, page) },
                "anilist" to suspend { anilist.fetchByGenres(mediaType, genres, sort, page) },
            )
            DiscoveryMediaType.MANGA -> listOf(
                "mangadex" to suspend { mangadex.fetchByGenres(mediaType, genres, sort, page) },
                "shikimori" to suspend { shikimori.fetchByGenres(mediaType, genres, sort, page) },
                "anilist" to suspend { anilist.fetchByGenres(mediaType, genres, sort, page) },
            )
            DiscoveryMediaType.NOVEL -> listOf(
                "anilist" to suspend { anilist.fetchByGenres(mediaType, genres, sort, page) },
            )
        }
        return fetchWithFallback(providers, mediaType)
    }

    override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        val providers: List<Pair<String, suspend () -> DiscoveryMeta?>> = when (mediaType) {
            DiscoveryMediaType.ANIME -> listOf(
                "shikimori" to suspend { shikimori.fetchMeta(title, mediaType) },
                "anilist" to suspend { anilist.fetchMeta(title, mediaType) },
                "jikan" to suspend { jikan.fetchMeta(title, mediaType) },
            )
            DiscoveryMediaType.MANGA -> listOf(
                "mangadex" to suspend { mangadex.fetchMeta(title, mediaType) },
                "shikimori" to suspend { shikimori.fetchMeta(title, mediaType) },
                "anilist" to suspend { anilist.fetchMeta(title, mediaType) },
            )
            DiscoveryMediaType.NOVEL -> listOf(
                "anilist" to suspend { anilist.fetchMeta(title, mediaType) },
                "shikimori" to suspend { shikimori.fetchMeta(title, mediaType) },
            )
        }
        for ((name, action) in providers) {
            try {
                val meta = action()
                if (meta != null && (!meta.description.isNullOrBlank() || meta.genres.isNotEmpty())) {
                    return meta
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[CompositeTrending] fetchMeta '$title' ($mediaType) failed on '$name': ${e.message}" }
            }
        }
        return null
    }
}
