package eu.kanade.tachiyomi.data.discovery

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * «+» на карточке подборки: ищет точное совпадение тайтла в установленных
 * источниках (search API, ≤ [MAX_SOURCES] источников, таймаут на источник),
 * создаёт локальную запись и сразу добавляет в библиотеку (autoFavorite).
 * При отсутствии точного совпадения возвращает false — UI открывает глобальный поиск.
 */
class DiscoveryLibraryAdder {

    companion object {
        const val MAX_SOURCES = 5
        const val SOURCE_TIMEOUT_MS = 8_000L
    }

    suspend fun addFirstMatch(mediaType: DiscoveryMediaType, title: String): Boolean =
        withContext(Dispatchers.IO) {
            val clean = normalizeDiscoveryTitle(title)
            try {
                when (mediaType) {
                    DiscoveryMediaType.MANGA -> addManga(clean, title)
                    DiscoveryMediaType.ANIME -> addAnime(clean, title)
                    DiscoveryMediaType.NOVEL -> addNovel(clean, title)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat { "[DiscoveryAdd] FAILED '$title': ${e.message}" }
                false
            }
        }

    private suspend fun addManga(clean: String, query: String): Boolean = coroutineScope {
        val ids = Injekt.get<GetEnabledMangaSources>().subscribe().first().map { it.id }.take(MAX_SOURCES)
        if (ids.isEmpty()) return@coroutineScope false
        val mutex = Mutex()
        var added = false
        val channel = Channel<Boolean>(ids.size)
        ids.forEach { id ->
            launch {
                val ok = try {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        if (added) return@withTimeoutOrNull false
                        val source = Injekt.get<MangaSourceManager>().getOrStub(id) as? CatalogueSource
                            ?: return@withTimeoutOrNull false
                        val page = source.getSearchManga(1, query, source.getFilterList())
                        val match = page.mangas.firstOrNull { normalizeDiscoveryTitle(it.title) == clean }
                            ?: return@withTimeoutOrNull false
                        mutex.withLock {
                            if (added) return@withTimeoutOrNull false
                            val result = Injekt.get<NetworkToLocalManga>()
                                .await(listOf(match.toDomainManga(id)), autoFavorite = true)
                            if (result.isNotEmpty()) {
                                added = true
                                true
                            } else {
                                false
                            }
                        }
                    } ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                channel.send(ok)
            }
        }
        var finished = 0
        while (finished < ids.size) {
            val result = channel.receive()
            if (result) {
                coroutineContext.cancelChildren()
                return@coroutineScope true
            }
            finished++
        }
        false
    }

    private suspend fun addAnime(clean: String, query: String): Boolean = coroutineScope {
        val ids = Injekt.get<GetEnabledAnimeSources>().subscribe().first().map { it.id }.take(MAX_SOURCES)
        if (ids.isEmpty()) return@coroutineScope false
        val mutex = Mutex()
        var added = false
        val channel = Channel<Boolean>(ids.size)
        ids.forEach { id ->
            launch {
                val ok = try {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        if (added) return@withTimeoutOrNull false
                        val source = Injekt.get<AnimeSourceManager>().getOrStub(id) as? AnimeCatalogueSource
                            ?: return@withTimeoutOrNull false
                        val page = source.getSearchAnime(1, query, source.getFilterList())
                        val match = page.animes.firstOrNull { normalizeDiscoveryTitle(it.title) == clean }
                            ?: return@withTimeoutOrNull false
                        mutex.withLock {
                            if (added) return@withTimeoutOrNull false
                            val result = Injekt.get<NetworkToLocalAnime>()
                                .await(listOf(match.toDomainAnime(id)), autoFavorite = true)
                            if (result.isNotEmpty()) {
                                added = true
                                true
                            } else {
                                false
                            }
                        }
                    } ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                channel.send(ok)
            }
        }
        var finished = 0
        while (finished < ids.size) {
            val result = channel.receive()
            if (result) {
                coroutineContext.cancelChildren()
                return@coroutineScope true
            }
            finished++
        }
        false
    }

    private suspend fun addNovel(clean: String, query: String): Boolean = coroutineScope {
        val ids = Injekt.get<GetEnabledNovelSources>().subscribe().first().map { it.id }.take(MAX_SOURCES)
        if (ids.isEmpty()) return@coroutineScope false
        val mutex = Mutex()
        var added = false
        val channel = Channel<Boolean>(ids.size)
        ids.forEach { id ->
            launch {
                val ok = try {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        if (added) return@withTimeoutOrNull false
                        val source = Injekt.get<NovelSourceManager>().getOrStub(id) as? NovelCatalogueSource
                            ?: return@withTimeoutOrNull false
                        val page = source.getSearchNovels(1, query, source.getFilterList())
                        val match = page.novels.firstOrNull { normalizeDiscoveryTitle(it.title) == clean }
                            ?: return@withTimeoutOrNull false
                        mutex.withLock {
                            if (added) return@withTimeoutOrNull false
                            val result = Injekt.get<NetworkToLocalNovel>()
                                .await(listOf(match.toDomainNovel(id)), autoFavorite = true)
                            if (result.isNotEmpty()) {
                                added = true
                                true
                            } else {
                                false
                            }
                        }
                    } ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                channel.send(ok)
            }
        }
        var finished = 0
        while (finished < ids.size) {
            val result = channel.receive()
            if (result) {
                coroutineContext.cancelChildren()
                return@coroutineScope true
            }
            finished++
        }
        false
    }
}
