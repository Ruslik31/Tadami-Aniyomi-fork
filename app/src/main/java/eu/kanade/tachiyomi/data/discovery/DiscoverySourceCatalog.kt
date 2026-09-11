package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Ряд «Источник»: popular-витрина выбранного источника расширения
 * (catalogue API источника), опционально с жанровым фильтром (best-effort:
 * только если источник exposes группу жанровых чекбоксов).
 */
interface DiscoverySourceCatalog {
    suspend fun popular(mediaType: DiscoveryMediaType, sourceId: Long): List<DiscoveryRowItem>
    suspend fun popularWithGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
    ): List<DiscoveryRowItem>
    suspend fun latest(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int = 1,
    ): List<DiscoveryRowItem>
}

class AppDiscoverySourceCatalog : DiscoverySourceCatalog {

    override suspend fun popular(mediaType: DiscoveryMediaType, sourceId: Long): List<DiscoveryRowItem> =
        fetch(mediaType, sourceId, genres = null)

    override suspend fun popularWithGenres(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>,
    ): List<DiscoveryRowItem> = fetch(mediaType, sourceId, genres = genres)

    override suspend fun latest(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        page: Int,
    ): List<DiscoveryRowItem> = try {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrElse { source.getPopularManga(page) }
                } else {
                    source.getPopularManga(page)
                }
                pageData.mangas.mapIndexed { idx, m -> rowItem(m.title, m.thumbnail_url, source.name, idx) }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrElse { source.getPopularAnime(page) }
                } else {
                    source.getPopularAnime(page)
                }
                pageData.animes.mapIndexed { idx, a -> rowItem(a.title, a.thumbnail_url, source.name, idx) }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return emptyList()
                val pageData = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(page) }.getOrElse { source.getPopularNovels(page) }
                } else {
                    source.getPopularNovels(page)
                }
                pageData.novels.mapIndexed { idx, n -> rowItem(n.title, n.thumbnail_url, source.name, idx) }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logcat { "[DiscoverySourceCatalog] latest FAILED source=$sourceId: ${e.message}" }
        emptyList()
    }

    private suspend fun fetch(
        mediaType: DiscoveryMediaType,
        sourceId: Long,
        genres: List<String>?,
    ): List<DiscoveryRowItem> = try {
        when (mediaType) {
            DiscoveryMediaType.MANGA -> {
                val source = Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
                    ?: return emptyList()
                val filters = if (genres != null) withMangaGenreFilters(source.getFilterList(), genres) else null
                // Жанровый запрос без применимого фильтра — честный пустой результат,
                // а не popular-выдача под видом «твоего вкуса».
                if (genres != null && filters == null) return emptyList()
                val page = if (filters == null) source.getPopularManga(1) else source.getSearchManga(1, "", filters)
                page.mangas.mapIndexed { idx, m -> rowItem(m.title, m.thumbnail_url, source.name, idx) }
            }
            DiscoveryMediaType.ANIME -> {
                val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId) as? AnimeCatalogueSource
                    ?: return emptyList()
                val filters = if (genres != null) withAnimeGenreFilters(source.getFilterList(), genres) else null
                if (genres != null && filters == null) return emptyList()
                val page = if (filters == null) source.getPopularAnime(1) else source.getSearchAnime(1, "", filters)
                page.animes.mapIndexed { idx, a -> rowItem(a.title, a.thumbnail_url, source.name, idx) }
            }
            DiscoveryMediaType.NOVEL -> {
                val source = Injekt.get<NovelSourceManager>().getOrStub(sourceId) as? NovelCatalogueSource
                    ?: return emptyList()
                val filters = if (genres != null) withNovelGenreFilters(source.getFilterList(), genres) else null
                if (genres != null && filters == null) return emptyList()
                val page = if (filters == null) source.getPopularNovels(1) else source.getSearchNovels(1, "", filters)
                page.novels.mapIndexed { idx, n -> rowItem(n.title, n.thumbnail_url, source.name, idx) }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Пробрасываем: ряд SOURCE помечается failed, кэш не затирается.
        logcat { "[DiscoverySourceCatalog] FAILED source=$sourceId: ${e.message}" }
        throw e
    }

    private fun rowItem(title: String, thumbnailUrl: String?, sourceName: String, index: Int) = DiscoveryRowItem(
        title = title,
        cleanTitle = normalizeDiscoveryTitle(title),
        coverUrl = thumbnailUrl,
        reason = null,
        seedTitle = null,
        provider = sourceName,
        score = 1.0 - index * 0.01,
    )

    private fun withMangaGenreFilters(filters: FilterList, genres: List<String>): FilterList? {
        val wanted = expandGenreSet(genres)
        val group = filters.filterIsInstance<Filter.Group<*>>()
            .firstOrNull { it.name.contains("genre", true) || it.name.contains("жанр", true) }
            ?: return null
        val boxes = group.state.filterIsInstance<Filter.CheckBox>()
        val applied = boxes.count { box ->
            (box.name.trim().lowercase() in wanted).also { if (it) box.state = true }
        }
        return if (applied > 0) filters else null
    }

    private fun withAnimeGenreFilters(filters: AnimeFilterList, genres: List<String>): AnimeFilterList? {
        val wanted = expandGenreSet(genres)
        val group = filters.filterIsInstance<AnimeFilter.Group<*>>()
            .firstOrNull { it.name.contains("genre", true) || it.name.contains("жанр", true) }
            ?: return null
        val boxes = group.state.filterIsInstance<AnimeFilter.CheckBox>()
        val applied = boxes.count { box ->
            (box.name.trim().lowercase() in wanted).also { if (it) box.state = true }
        }
        return if (applied > 0) filters else null
    }

    private fun withNovelGenreFilters(filters: NovelFilterList, genres: List<String>): NovelFilterList? {
        val wanted = expandGenreSet(genres)
        val group = filters.filterIsInstance<NovelFilter.Group<*>>()
            .firstOrNull { it.name.contains("genre", true) || it.name.contains("жанр", true) }
            ?: return null
        val boxes = group.state.filterIsInstance<NovelFilter.CheckBox>()
        val applied = boxes.count { box ->
            (box.name.trim().lowercase() in wanted).also { if (it) box.state = true }
        }
        return if (applied > 0) filters else null
    }
}
