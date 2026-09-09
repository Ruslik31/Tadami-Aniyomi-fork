package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.flow.first
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.normalizeDiscoveryTitle
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Реальные источники данных для сидов: библиотека (через GetLibrary*-интеракторы,
 * те же, что используют Home-модели) и история по медиатипам.
 */
class AppDiscoverySeedSources : DiscoverySeedSources {

    override suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput> =
        when (mediaType) {
            DiscoveryMediaType.ANIME -> animeCandidates()
            DiscoveryMediaType.MANGA -> mangaCandidates()
            DiscoveryMediaType.NOVEL -> novelCandidates()
        }

    private suspend fun novelCandidates(): List<DiscoverySeedInput> =
        Injekt.get<GetLibraryNovel>().await().map { item ->
            val completed = item.totalChapters > 0 && item.readCount >= item.totalChapters
            DiscoverySeedInput(
                entryId = item.novel.id,
                title = item.novel.title,
                description = item.novel.description,
                author = item.novel.author,
                genres = item.novel.genre.orEmpty(),
                dateAdded = item.novel.dateAdded,
                isCompleted = completed,
                completedAt = if (completed) item.lastRead.takeIf { it > 0 } else null,
                lastInteraction = item.lastRead.takeIf { it > 0 },
            )
        }

    private suspend fun mangaCandidates(): List<DiscoverySeedInput> =
        Injekt.get<GetLibraryManga>().await().map { item ->
            val completed = item.totalChapters > 0 && item.readCount >= item.totalChapters
            DiscoverySeedInput(
                entryId = item.manga.id,
                title = item.manga.title,
                description = item.manga.description,
                author = item.manga.author,
                genres = item.manga.genre.orEmpty(),
                dateAdded = item.manga.dateAdded,
                isCompleted = completed,
                completedAt = if (completed) item.lastRead.takeIf { it > 0 } else null,
                lastInteraction = item.lastRead.takeIf { it > 0 },
            )
        }

    private suspend fun animeCandidates(): List<DiscoverySeedInput> =
        Injekt.get<GetLibraryAnime>().await().map { item ->
            val completed = item.totalCount > 0 && item.seenCount >= item.totalCount
            DiscoverySeedInput(
                entryId = item.anime.id,
                title = item.anime.title,
                description = item.anime.description,
                author = item.anime.author,
                genres = item.anime.genre.orEmpty(),
                dateAdded = item.anime.dateAdded,
                isCompleted = completed,
                completedAt = if (completed) item.lastSeen.takeIf { it > 0 } else null,
                lastInteraction = item.lastSeen.takeIf { it > 0 },
            )
        }

    override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType): Set<String> {
        val titles = when (mediaType) {
            DiscoveryMediaType.ANIME -> Injekt.get<AnimeHistoryRepository>()
                .getAnimeHistory("").first().map { it.title }
            DiscoveryMediaType.MANGA -> Injekt.get<MangaHistoryRepository>()
                .getMangaHistory("").first().map { it.title }
            DiscoveryMediaType.NOVEL -> Injekt.get<NovelHistoryRepository>()
                .getNovelHistory("").first().map { it.title }
        }
        return titles.mapTo(HashSet()) { normalizeDiscoveryTitle(it) }
    }
}
