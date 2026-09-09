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
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

internal fun parseMangaDexData(
    root: JsonObject,
    isRussianLocale: Boolean = false,
): List<DiscoveryTrendingItem> {
    val data = root["data"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() } ?: return emptyList()
    return data.mapNotNull { item ->
        val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val attributes = runCatching { item["attributes"]?.jsonObject }.getOrNull() ?: return@mapNotNull null
        val titleObj = runCatching { attributes["title"]?.jsonObject }.getOrNull()
        val enTitle = titleObj?.get("en")?.jsonPrimitive?.contentOrNull
        val jaTitle =
            titleObj?.get("ja-ro")?.jsonPrimitive?.contentOrNull ?: titleObj?.get("ja")?.jsonPrimitive?.contentOrNull
        val firstTitle = titleObj?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull

        val altTitles = runCatching { attributes["altTitles"]?.jsonArray?.mapNotNull { it.jsonObject } }.getOrNull()
        val ruTitle = altTitles?.firstNotNullOfOrNull { it["ru"]?.jsonPrimitive?.contentOrNull }

        val primaryTitle = if (isRussianLocale && !ruTitle.isNullOrBlank()) {
            ruTitle
        } else {
            enTitle ?: jaTitle ?: firstTitle ?: ruTitle ?: return@mapNotNull null
        }

        val relationships = runCatching { item["relationships"]?.jsonArray?.mapNotNull { it.jsonObject } }.getOrNull()
        val coverArt = relationships?.find { it["type"]?.jsonPrimitive?.contentOrNull == "cover_art" }
        val fileName = runCatching {
            coverArt?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val coverUrl = if (fileName != null) {
            "https://uploads.mangadex.org/covers/$id/$fileName.512.jpg"
        } else {
            null
        }

        val tags = runCatching {
            attributes["tags"]?.jsonArray?.mapNotNull { tag ->
                tag.jsonObject["attributes"]?.jsonObject?.get(
                    "name",
                )?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull().orEmpty()

        DiscoveryTrendingItem(
            title = primaryTitle,
            cleanTitle = normalizeDiscoveryTitle(primaryTitle),
            coverUrl = coverUrl,
            anilistId = 0L,
            seasonLabel = null,
            genres = tags,
            provider = "mangadex_trend",
        )
    }
}

open class MangaDexTrendingSource(
    private val clientProvider: () -> OkHttpClient = { Injekt.get<NetworkHelper>().client },
    private val jsonProvider: () -> Json = { Injekt.get() },
    private val isRussianLocaleProvider: () -> Boolean = {
        java.util.Locale.getDefault().language == "ru"
    },
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
        if (mediaType != DiscoveryMediaType.MANGA) return emptyList()

        return try {
            val offset = (page - 1).coerceAtLeast(0) * 30
            val orderParam = when (sort) {
                TrendSort.TRENDING -> "order[latestUploadedChapter]=desc"
                TrendSort.SCORE -> "order[rating]=desc"
                TrendSort.POPULARITY -> "order[followedCount]=desc"
            }
            val url = "https://api.mangadex.org/manga?limit=30&offset=$offset&$orderParam" +
                "&includes[]=cover_art&contentRating[]=safe&contentRating[]=suggestive"

            val response = clientProvider().newCall(GET(url, headers = headers))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())

            parseMangaDexData(response, isRussianLocaleProvider())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MangaDexTrending] fetch FAILED: ${e.message}" }
            throw e
        }
    }

    override suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
        page: Int,
    ): List<DiscoveryTrendingItem> {
        if (mediaType != DiscoveryMediaType.MANGA || genres.isEmpty()) return emptyList()
        return try {
            val offset = (page - 1).coerceAtLeast(0) * 30
            val orderParam = when (sort) {
                TrendSort.SCORE -> "order[rating]=desc"
                else -> "order[followedCount]=desc"
            }
            val url = "https://api.mangadex.org/manga?limit=30&offset=$offset&$orderParam" +
                "&includes[]=cover_art&contentRating[]=safe&contentRating[]=suggestive"

            val response = clientProvider().newCall(GET(url, headers = headers))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())

            parseMangaDexData(response, isRussianLocaleProvider())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MangaDexTrending] fetchByGenres FAILED: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        if (mediaType != DiscoveryMediaType.MANGA) return null
        metaCache[title]?.let { return it }

        return try {
            val url = "https://api.mangadex.org/manga?title=${URLEncoder.encode(
                title,
                "UTF-8",
            )}&limit=1&includes[]=cover_art"
            val response = clientProvider().newCall(GET(url, headers = headers))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())

            val items = response["data"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            val first = items?.firstOrNull() ?: return null
            val attributes = first["attributes"]?.jsonObject ?: return null

            val descObj = attributes["description"]?.jsonObject
            val desc = descObj?.get("ru")?.jsonPrimitive?.contentOrNull
                ?: descObj?.get("en")?.jsonPrimitive?.contentOrNull
                ?: descObj?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull

            val tags = attributes["tags"]?.jsonArray?.mapNotNull { tag ->
                tag.jsonObject["attributes"]?.jsonObject?.get(
                    "name",
                )?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull
            }.orEmpty()

            val altTitles = attributes["altTitles"]?.jsonArray?.mapNotNull { it.jsonObject }
            val altTitle = altTitles?.firstNotNullOfOrNull {
                it["en"]?.jsonPrimitive?.contentOrNull
                    ?: it["ru"]?.jsonPrimitive?.contentOrNull
            }

            val meta = DiscoveryMeta(
                description = desc?.trim()?.takeIf { it.isNotEmpty() },
                genres = tags,
                altTitle = altTitle,
            )
            metaCache[title] = meta
            meta
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MangaDexTrending] fetchMeta FAILED for '$title': ${e.message}" }
            null
        }
    }
}
