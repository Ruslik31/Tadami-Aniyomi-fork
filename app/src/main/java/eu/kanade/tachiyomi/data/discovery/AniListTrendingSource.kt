package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.data.suggestions.sources.AniListRequestGuard
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

data class DiscoveryTrendingItem(
    val title: String,
    val cleanTitle: String,
    val coverUrl: String?,
    val anilistId: Long,
    val seasonLabel: String?,
    val genres: List<String> = emptyList(),
)

data class DiscoveryMeta(
    val description: String?,
    val genres: List<String>,
    val altTitle: String?,
)

enum class TrendSeason { CURRENT, NEXT, BOTH }
enum class TrendSort(val anilist: String) { POPULARITY("POPULARITY_DESC"), SCORE("SCORE_DESC") }

internal fun resolveSeasonWindow(nowMonth: Int, nowYear: Int, next: Boolean): Pair<String, Int> {
    val order = listOf("WINTER", "SPRING", "SUMMER", "FALL")
    val idx = ((nowMonth - 1) / 3).coerceIn(0, 3)
    val target = if (next) idx + 1 else idx
    return if (target > 3) order[0] to (nowYear + 1) else order[target] to nowYear
}

internal fun parseTrendingPage(page: JsonObject, seasonLabel: String? = null): List<DiscoveryTrendingItem> =
    (((page["data"] as? JsonObject)?.get("Page") as? JsonObject)?.get("media") as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.mapNotNull { media ->
            val titleObj = media["title"] as? JsonObject
            val title = titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("native")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            DiscoveryTrendingItem(
                title = title,
                cleanTitle = normalizeDiscoveryTitle(title),
                coverUrl = (media["coverImage"] as? JsonObject)?.get("large")?.jsonPrimitive?.contentOrNull,
                anilistId = media["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                seasonLabel = seasonLabel,
                genres = (media["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            )
        }
        .orEmpty()

class AniListTrendingSource(
    private val clientProvider: () -> okhttp3.OkHttpClient = { Injekt.get<NetworkHelper>().client },
    private val jsonProvider: () -> Json = { Injekt.get() }, // lazy: конструктор не требует Injekt-реестр (юнит-тесты)
) {

    private val metaCache = mutableMapOf<String, DiscoveryMeta>()

    suspend fun fetch(
        mediaType: DiscoveryMediaType,
        season: TrendSeason,
        sort: TrendSort,
    ): List<DiscoveryTrendingItem> {
        return try {
            when (mediaType) {
                DiscoveryMediaType.ANIME -> {
                    val now = LocalDate.now()
                    val windows = when (season) {
                        TrendSeason.CURRENT -> listOf(false)
                        TrendSeason.NEXT -> listOf(true)
                        TrendSeason.BOTH -> listOf(false, true)
                    }
                    windows.flatMap { next ->
                        val (s, year) = resolveSeasonWindow(now.monthValue, now.year, next)
                        querySeason(s, year, sort, if (next) "next" else "current")
                    }
                }
                DiscoveryMediaType.MANGA -> queryPopular(type = "MANGA", format = null, sort = sort)
                DiscoveryMediaType.NOVEL -> queryPopular(type = "MANGA", format = "NOVEL", sort = sort)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Пробрасываем: координатор пометит ряд failed и НЕ затрёт кэш.
            logcat { "[DiscoveryTrending] FAILED: ${e.message}" }
            throw e
        }
    }

    private suspend fun querySeason(
        season: String,
        year: Int,
        sort: TrendSort,
        label: String,
    ): List<DiscoveryTrendingItem> {
        val query = """
            query (${'$'}season: MediaSeason, ${'$'}year: Int, ${'$'}sort: [MediaSort]) {
              Page(page: 1, perPage: 20) {
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}year, sort: ${'$'}sort) {
                  id title { romaji } coverImage { large }
                }
              }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    put("season", season)
                    put("year", year)
                    put(
                        "sort",
                        buildJsonArray {
                            add(JsonPrimitive(sort.anilist))
                        },
                    )
                },
            )
        }
        return post(payload).let { parseTrendingPage(it, label) }
    }

    private suspend fun queryPopular(type: String, format: String?, sort: TrendSort): List<DiscoveryTrendingItem> {
        val formatArg = if (format != null) ", format: ${'$'}format" else ""
        val query = """
            query (${'$'}type: MediaType, ${'$'}sort: [MediaSort]${if (format != null) ", ${'$'}format: MediaFormat" else ""}) {
              Page(page: 1, perPage: 20) {
                media(type: ${'$'}type, sort: ${'$'}sort$formatArg) {
                  id title { romaji } coverImage { large }
                }
              }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    put("type", type)
                    put(
                        "sort",
                        buildJsonArray {
                            add(JsonPrimitive(sort.anilist))
                        },
                    )
                    if (format != null) put("format", format)
                },
            )
        }
        return post(payload).let { parseTrendingPage(it, null) }
    }

    /** Кандидаты ряда «Твой вкус»: AniList `genre_in` по топ-жанрам профиля. */
    suspend fun fetchByGenres(
        mediaType: DiscoveryMediaType,
        genres: List<String>,
        sort: TrendSort,
    ): List<DiscoveryTrendingItem> {
        if (genres.isEmpty()) return emptyList()
        return try {
            val (type, format) = when (mediaType) {
                DiscoveryMediaType.ANIME -> "ANIME" to null
                DiscoveryMediaType.MANGA -> "MANGA" to null
                DiscoveryMediaType.NOVEL -> "MANGA" to "NOVEL"
            }
            queryByGenres(type, format, genres, sort)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[DiscoveryTrending] genre query FAILED: ${e.message}" }
            throw e
        }
    }

    private suspend fun queryByGenres(
        type: String,
        format: String?,
        genres: List<String>,
        sort: TrendSort,
    ): List<DiscoveryTrendingItem> {
        val formatArg = if (format != null) ", format: ${'$'}format" else ""
        val query = """
            query (${'$'}type: MediaType, ${'$'}genres: [String], ${'$'}sort: [MediaSort]${if (format != null) ", ${'$'}format: MediaFormat" else ""}) {
              Page(page: 1, perPage: 20) {
                media(type: ${'$'}type, genre_in: ${'$'}genres, sort: ${'$'}sort$formatArg) {
                  id title { romaji } coverImage { large } genres
                }
              }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    put("type", type)
                    put("genres", buildJsonArray { genres.forEach { add(JsonPrimitive(it)) } })
                    put(
                        "sort",
                        buildJsonArray {
                            add(JsonPrimitive(sort.anilist))
                        },
                    )
                    if (format != null) put("format", format)
                },
            )
        }
        return post(payload).let { parseTrendingPage(it, null) }
    }

    /** Лёгкие метаданные для превью-листа: описание/жанры/альт-тайтл по тайтлу (кэш в памяти). */
    suspend fun fetchMeta(title: String, mediaType: DiscoveryMediaType): DiscoveryMeta? {
        metaCache[title]?.let { return it }
        return try {
            val type = when (mediaType) {
                DiscoveryMediaType.ANIME -> "ANIME"
                DiscoveryMediaType.MANGA -> "MANGA"
                DiscoveryMediaType.NOVEL -> "MANGA"
            }
            val formatArg = if (mediaType == DiscoveryMediaType.NOVEL) ", format: NOVEL" else ""
            val query = """
                query (${'$'}search: String!, ${'$'}type: MediaType!) {
                  Page(page: 1, perPage: 1) {
                    media(search: ${'$'}search, type: ${'$'}type$formatArg) {
                      description(asHtml: false)
                      genres
                      title { english native }
                    }
                  }
                }
            """.trimIndent()
            val payload = buildJsonObject {
                put("query", query)
                put(
                    "variables",
                    buildJsonObject {
                        put("search", title)
                        put("type", type)
                    },
                )
            }
            val page = post(payload)
            val media = (
                ((page["data"] as? JsonObject)?.get("Page") as? JsonObject)
                    ?.get("media") as? JsonArray
                )?.firstOrNull() as? JsonObject
                ?: return null
            val meta = DiscoveryMeta(
                description = media["description"]?.jsonPrimitive?.contentOrNull
                    ?.replace(Regex("<[^>]*>"), "")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                genres = (media["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive?.contentOrNull }.orEmpty(),
                altTitle = (media["title"] as? JsonObject)?.let { t ->
                    t["english"]?.jsonPrimitive?.contentOrNull ?: t["native"]?.jsonPrimitive?.contentOrNull
                },
            )
            metaCache[title] = meta
            meta
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[DiscoveryTrending] meta FAILED for '$title': ${e.message}" }
            null
        }
    }

    private suspend fun post(payload: JsonObject): JsonObject {
        AniListRequestGuard.ensureClosed()
        AniListRequestGuard.acquire()
        val body = payload.toString().toRequestBody(jsonMime)
        return try {
            clientProvider()
                .newCall(POST("https://graphql.anilist.co/", headers = AniListRequestGuard.headers, body = body))
                .awaitSuccess()
                .parseAs<JsonObject>(jsonProvider())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains("403") == true) AniListRequestGuard.reportForbidden()
            throw e
        }
    }
}
