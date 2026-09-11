package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

@Serializable
internal data class ShikimoriMediaItem(
    val id: Long,
    val name: String,
    val russian: String? = null,
    val image: ShikimoriCover? = null,
    val score: Double = 0.0,
    val status: String? = null,
    val kind: String? = null,
    val description: String? = null,
    val genres: List<ShikimoriGenre>? = null,
    val url: String? = null,
)

@Serializable
internal data class ShikimoriCover(
    val original: String? = null,
    val preview: String? = null,
)

@Serializable
internal data class ShikimoriGenre(
    val id: Long = 0L,
    val name: String = "",
    val russian: String? = null,
)

@Serializable
internal data class ShikimoriGenreDto(
    val id: Long = 0L,
    val name: String = "",
    val russian: String? = null,
    val kind: String? = null,
)

/** Жанры профиля → id жанров Shikimori (матч по name/russian с учётом RU↔EN переводов). */
internal fun mapGenresToIds(
    profileGenres: List<String>,
    catalog: List<ShikimoriGenreDto>,
    mediaKind: String,
): List<Long> {
    val wanted = expandGenreSet(profileGenres)
    return catalog.asSequence()
        .filter { it.kind == null || it.kind == mediaKind }
        .filter { g ->
            g.name.trim().lowercase() in wanted ||
                g.russian?.trim()?.lowercase()?.let { it.isNotEmpty() && it in wanted } == true
        }
        .map { it.id }
        .distinct()
        .toList()
}

internal fun parseShikimoriItems(
    items: List<ShikimoriMediaItem>,
    seasonLabel: String? = null,
    isRussianLocale: Boolean = false,
): List<DiscoveryTrendingItem> = items.mapNotNull { item ->
    val title = if (isRussianLocale && !item.russian.isNullOrBlank()) {
        item.russian
    } else {
        item.name
    }
    if (title.isBlank()) return@mapNotNull null
    val coverUrl = item.image?.original?.let { "https://shikimori.one$it" }
        ?: item.image?.preview?.let { "https://shikimori.one$it" }
    val genreList = item.genres?.map { it.russian?.takeIf { r -> r.isNotBlank() } ?: it.name }.orEmpty()

    DiscoveryTrendingItem(
        title = title,
        cleanTitle = normalizeDiscoveryTitle(title),
        coverUrl = coverUrl,
        anilistId = item.id,
        seasonLabel = seasonLabel,
        genres = genreList,
        provider = "shikimori_trend",
    )
}

/**
 * URL списка Shikimori. `censored=true` скрывает hentai/yaoi/yuri (официальный
 * параметр API) — используем для независимого NSFW-фильтра подборок.
 */
internal fun shikimoriListUrl(
    endpoint: String,
    page: Int,
    order: String,
    status: String? = null,
    genreIds: List<Long>? = null,
    censored: Boolean? = null,
    limit: Int = 30,
): String = buildString {
    append("https://shikimori.one/api/$endpoint?limit=$limit&page=$page&order=$order")
    if (status != null) append("&status=$status")
    if (!genreIds.isNullOrEmpty()) append("&genre=${genreIds.joinToString(",")}")
    if (censored != null) append("&censored=$censored")
}

open class ShikimoriTrendingSource(
    private val clientProvider: () -> OkHttpClient = { Injekt.get<NetworkHelper>().client },
    private val jsonProvider: () -> Json = { Injekt.get() },
    private val isRussianLocaleProvider: () -> Boolean = {
        java.util.Locale.getDefault().language == "ru"
    },
    private val nsfwFilterProvider: () -> Boolean = { discoveryNsfwFilterEnabled() },
) : DiscoveryTrendingSource {

    private val metaCache = mutableMapOf<String, DiscoveryMeta>()

    private val headers = Headers.Builder()
        .add("User-Agent", "Tadami/1.0 (Android)")
        .build()

    override suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason,
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        if (mediaType == DiscoveryMediaType.NOVEL) return emptyList()

        return try {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> fetchAnime(season, sort, page)
                DiscoveryMediaType.MANGA -> fetchManga(sort, page)
                DiscoveryMediaType.NOVEL -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[ShikimoriTrending] fetch FAILED for $mediaType: ${e.message}" }
            throw e
        }
    }

    private suspend fun fetchAnime(
        season: TrendSeason,
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        val order = if (sort == TrendSort.SCORE) "ranked" else "popularity"
        val status = when (season) {
            TrendSeason.CURRENT -> "ongoing"
            TrendSeason.NEXT -> "anons"
            TrendSeason.BOTH -> "ongoing"
        }
        val seasonLabel = when (season) {
            TrendSeason.CURRENT -> "current"
            TrendSeason.NEXT -> "next"
            TrendSeason.BOTH -> "current"
        }
        val url = shikimoriListUrl(
            endpoint = "animes",
            page = page,
            order = order,
            status = status,
            censored = nsfwFilterProvider(),
        )
        ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
        val response = clientProvider().newCall(GET(url, headers = headers))
            .awaitSuccess()
            .parseAs<List<ShikimoriMediaItem>>(jsonProvider())
        return parseShikimoriItems(response, seasonLabel, isRussianLocaleProvider())
    }

    private suspend fun fetchManga(
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        val order = if (sort == TrendSort.SCORE) "ranked" else "popularity"
        val url = shikimoriListUrl(
            endpoint = "mangas",
            page = page,
            order = order,
            censored = nsfwFilterProvider(),
        )
        ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
        val response = clientProvider().newCall(GET(url, headers = headers))
            .awaitSuccess()
            .parseAs<List<ShikimoriMediaItem>>(jsonProvider())
        return parseShikimoriItems(response, null, isRussianLocaleProvider())
    }

    override suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        if (mediaType == DiscoveryMediaType.NOVEL || genres.isEmpty()) return emptyList()
        return try {
            val endpoint = if (mediaType == DiscoveryMediaType.ANIME) "animes" else "mangas"
            val kind = if (mediaType == DiscoveryMediaType.ANIME) "anime" else "manga"
            val genreIds = mapGenresToIds(genres, genresCatalog(), kind)
            // Нет ids — честный пустой результат (Composite фолбэчится на AniList genre_in),
            // а не popularity-выдача без фильтра под видом «твоего вкуса».
            if (genreIds.isEmpty()) return emptyList()
            val order = if (sort == TrendSort.SCORE) "ranked" else "popularity"
            val url = shikimoriListUrl(
                endpoint = endpoint,
                page = page,
                order = order,
                genreIds = genreIds,
                censored = nsfwFilterProvider(),
            )
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
            val response = clientProvider().newCall(GET(url, headers = headers))
                .awaitSuccess()
                .parseAs<List<ShikimoriMediaItem>>(jsonProvider())
            parseShikimoriItems(response, null, isRussianLocaleProvider())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[ShikimoriTrending] fetchByGenres FAILED for $mediaType: ${e.message}" }
            emptyList()
        }
    }

    private suspend fun genresCatalog(): List<ShikimoriGenreDto> {
        val now = System.currentTimeMillis()
        genresCache?.let { (at, cached) -> if (now - at < GENRES_TTL_MS) return cached }
        ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
        val fresh = clientProvider()
            .newCall(GET("https://shikimori.one/api/genres", headers = headers))
            .awaitSuccess()
            .parseAs<List<ShikimoriGenreDto>>(jsonProvider())
        genresCache = now to fresh
        return fresh
    }

    private companion object {
        const val GENRES_TTL_MS = 24 * 60 * 60 * 1000L

        @Volatile
        var genresCache: Pair<Long, List<ShikimoriGenreDto>>? = null
    }

    override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        if (mediaType == DiscoveryMediaType.NOVEL) return null
        metaCache[title]?.let { return it }

        return try {
            val endpoint = if (mediaType == DiscoveryMediaType.ANIME) "animes" else "mangas"
            val searchUrl = "https://shikimori.one/api/$endpoint?search=${URLEncoder.encode(title, "UTF-8")}&limit=5"
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
            val searchResults = clientProvider().newCall(GET(searchUrl, headers = headers))
                .awaitSuccess()
                .parseAs<List<ShikimoriMediaItem>>(jsonProvider())

            val first = searchResults.firstOrNull { metaMatchesTitle(title, listOf(it.name, it.russian)) }
                ?: return null
            val detailUrl = "https://shikimori.one/api/$endpoint/${first.id}"
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.SHIKIMORI)
            val detail = clientProvider().newCall(GET(detailUrl, headers = headers))
                .awaitSuccess()
                .parseAs<ShikimoriMediaItem>(jsonProvider())

            val desc = detail.description
                ?.replace(Regex("\\[[^\\]]*]"), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val genreNames = detail.genres?.map {
                if (isRussianLocaleProvider() && !it.russian.isNullOrBlank()) it.russian else it.name
            }.orEmpty()
            val altTitle = if (title == detail.russian) detail.name else detail.russian

            val meta = DiscoveryMeta(
                description = desc,
                genres = genreNames,
                altTitle = altTitle,
            )
            metaCache[title] = meta
            meta
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[ShikimoriTrending] fetchMeta FAILED for '$title': ${e.message}" }
            null
        }
    }
}
