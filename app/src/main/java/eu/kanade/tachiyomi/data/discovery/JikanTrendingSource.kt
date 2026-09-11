package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

internal fun parseJikanAnimePage(
    page: JsonObject,
    seasonLabel: String? = null,
    dropRx: Boolean = false,
): List<DiscoveryTrendingItem> =
    page["data"]?.jsonArray
        ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        ?.mapNotNull { item ->
            // NSFW-фильтр: MAL-рейтинг «Rx - Hentai» — единственный взрослый сигнал в выдаче Jikan.
            if (dropRx) {
                val rating = runCatching { item["rating"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                if (rating != null && rating.startsWith("Rx", ignoreCase = true)) return@mapNotNull null
            }
            val title = item["title"]?.jsonPrimitive?.contentOrNull
                ?: item["title_english"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val coverUrl = runCatching {
                item["images"]?.jsonObject
                    ?.get("jpg")?.jsonObject
                    ?.get("large_image_url")?.jsonPrimitive?.contentOrNull
                    ?: item["images"]?.jsonObject
                        ?.get("jpg")?.jsonObject
                        ?.get("image_url")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val malId = item["mal_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val genres = runCatching {
                item["genres"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            }.getOrNull().orEmpty()
            DiscoveryTrendingItem(
                title = title,
                cleanTitle = normalizeDiscoveryTitle(title),
                coverUrl = coverUrl,
                anilistId = malId,
                seasonLabel = seasonLabel,
                genres = genres,
                provider = "jikan_trend",
            )
        }.orEmpty()

open class JikanTrendingSource(
    private val clientProvider: () -> OkHttpClient = { Injekt.get<NetworkHelper>().client },
    private val jsonProvider: () -> Json = { Injekt.get() },
    private val nsfwFilterProvider: () -> Boolean = { discoveryNsfwFilterEnabled() },
) : DiscoveryTrendingSource {

    private val metaCache = mutableMapOf<String, DiscoveryMeta>()

    override suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason,
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        if (mediaType != DiscoveryMediaType.ANIME) return emptyList()

        return try {
            val url = if (sort == TrendSort.SCORE) {
                "https://api.jikan.moe/v4/top/anime?page=$page&limit=25"
            } else {
                when (season) {
                    TrendSeason.NEXT -> "https://api.jikan.moe/v4/seasons/upcoming?page=$page&limit=25"
                    else -> "https://api.jikan.moe/v4/seasons/now?page=$page&limit=25"
                }
            }
            val seasonLabel = when (season) {
                TrendSeason.NEXT -> "next"
                else -> "current"
            }
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.JIKAN)
            val response = clientProvider().newCall(GET(url))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())

            parseJikanAnimePage(response, seasonLabel, dropRx = nsfwFilterProvider())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[JikanTrending] fetch FAILED: ${e.message}" }
            throw e
        }
    }

    override suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        if (mediaType != DiscoveryMediaType.ANIME || genres.isEmpty()) return emptyList()
        return try {
            val url = "https://api.jikan.moe/v4/top/anime?page=$page&limit=25&filter=bypopularity"
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.JIKAN)
            val response = clientProvider().newCall(GET(url))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())
            parseJikanAnimePage(response, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[JikanTrending] fetchByGenres FAILED: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        if (mediaType != DiscoveryMediaType.ANIME) return null
        metaCache[title]?.let { return it }

        return try {
            val url = "https://api.jikan.moe/v4/anime?q=${URLEncoder.encode(title, "UTF-8")}&limit=5"
            ExternalApiThrottle.acquire(ExternalApiThrottle.Api.JIKAN)
            val response = clientProvider().newCall(GET(url))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())

            val items = response["data"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            val first = items?.firstOrNull { item ->
                metaMatchesTitle(
                    title,
                    listOf(
                        runCatching { item["title"]?.jsonPrimitive?.contentOrNull }.getOrNull(),
                        runCatching { item["title_english"]?.jsonPrimitive?.contentOrNull }.getOrNull(),
                        runCatching { item["title_japanese"]?.jsonPrimitive?.contentOrNull }.getOrNull(),
                    ),
                )
            } ?: return null

            val desc = first["synopsis"]?.jsonPrimitive?.contentOrNull
            val genres = runCatching {
                first["genres"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            }.getOrNull().orEmpty()
            val altTitle = first["title_english"]?.jsonPrimitive?.contentOrNull
                ?: first["title_japanese"]?.jsonPrimitive?.contentOrNull

            val meta = DiscoveryMeta(
                description = desc?.trim()?.takeIf { it.isNotEmpty() },
                genres = genres,
                altTitle = altTitle,
            )
            metaCache[title] = meta
            meta
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[JikanTrending] fetchMeta FAILED for '$title': ${e.message}" }
            null
        }
    }
}
