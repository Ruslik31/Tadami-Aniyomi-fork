package eu.kanade.tachiyomi.ui.discovery

import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import eu.kanade.tachiyomi.novelsource.online.HttpNovelSource
import okhttp3.Call
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Обложки discovery-карточек приходят из внешних источников (AniList CDN, каталоги
 * расширений). Часть источников отдаёт картинки только со своими заголовками
 * (Referer/UA) или сквозь свои интерсепторы (Cloudflare/WebView), поэтому голый
 * URL через Coil даёт 403/404 и плейсхолдер. Резолвим источник по имени провайдера
 * и передаём в [AuroraPosterRequest] его headers + client + referer.
 */
object DiscoveryCoverSource {

    internal data class Resolved(
        val headers: Map<String, String>?,
        val client: Call.Factory?,
        val referer: String?,
    )

    // ConcurrentHashMap: resolve() зовётся с UI/Coil-потоков; null-значения
    // (источник не найден) храним через Optional — CHM запрещает null.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<Resolved>>()

    private fun okhttpHeadersToMap(headers: okhttp3.Headers): Map<String, String>? {
        val map = mutableMapOf<String, String>()
        for (i in 0 until headers.size) {
            map[headers.name(i)] = headers.value(i)
        }
        return map.ifEmpty { null }
    }

    internal fun resolve(mediaType: DiscoveryMediaType, provider: String?): Resolved? {
        if (provider == null) return null
        val key = mediaType.key + ":" + provider
        cache[key]?.let { return it.orElse(null) }
        val resolved = when (mediaType) {
            DiscoveryMediaType.ANIME ->
                Injekt.get<AnimeSourceManager>().getOnlineSources()
                    .firstOrNull { it.name == provider }
                    ?.let { s -> Resolved(okhttpHeadersToMap(s.headers), s.client, s.baseUrl) }
            DiscoveryMediaType.MANGA ->
                Injekt.get<MangaSourceManager>().getOnlineSources()
                    .firstOrNull { it.name == provider }
                    ?.let { s -> Resolved(okhttpHeadersToMap(s.headers), s.client, s.baseUrl) }
            DiscoveryMediaType.NOVEL ->
                Injekt.get<NovelSourceManager>().getOnlineSources()
                    .firstOrNull { it.name == provider }
                    ?.let { s ->
                        Resolved(
                            okhttpHeadersToMap(s.headers),
                            (s as? HttpNovelSource)?.client,
                            s.baseUrl,
                        )
                    }
        }
        cache[key] = java.util.Optional.ofNullable(resolved)
        return resolved
    }

    fun clearCache() = cache.clear()
}

/** Coil-модель обложки: с заголовками источника, если источник найден, иначе голый URL. */
internal fun discoveryCoverData(
    mediaType: DiscoveryMediaType?,
    provider: String?,
    coverUrl: String?,
): Any? {
    if (coverUrl == null) return null
    val resolved = if (mediaType != null) DiscoveryCoverSource.resolve(mediaType, provider) else null
    return if (resolved != null) {
        AuroraPosterRequest(
            primaryUrl = coverUrl,
            refererUrl = resolved.referer,
            headers = resolved.headers,
            client = resolved.client,
        )
    } else {
        coverUrl
    }
}
