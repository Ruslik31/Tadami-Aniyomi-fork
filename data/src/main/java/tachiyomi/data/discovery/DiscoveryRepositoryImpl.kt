package tachiyomi.data.discovery

import data.Discovery_suggestions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository

object DiscoveryMapper {
    fun map(row: Discovery_suggestions): DiscoverySuggestion = DiscoverySuggestion(
        id = row.id,
        mediaType = DiscoveryMediaType.fromKey(row.media_type) ?: DiscoveryMediaType.ANIME,
        rowType = DiscoveryRowType.fromKey(row.row_type) ?: DiscoveryRowType.LIKE,
        title = row.title,
        cleanTitle = row.clean_title,
        coverUrl = row.cover_url,
        reason = row.reason,
        seedTitle = row.seed_title,
        provider = row.provider,
        score = row.score,
        position = row.position,
        createdAt = row.created_at,
    )
}

class DiscoveryRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : DiscoveryRepository {

    override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> =
        handler.subscribeToList { db ->
            db.discovery_suggestionsQueries.selectAllByMedia(mediaType.key)
        }.map { rows -> rows.map(DiscoveryMapper::map) }

    override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> =
        handler.subscribeToList { db ->
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, _ -> clean_title }
        }.map { titles -> titles.toSet() }

    override suspend fun replaceRows(
        mediaType: DiscoveryMediaType,
        rowType: DiscoveryRowType,
        items: List<DiscoverySuggestion>,
    ) {
        handler.await(inTransaction = true) { db ->
            db.discovery_suggestionsQueries.deleteByMediaAndRow(mediaType.key, rowType.key)
            items.forEachIndexed { index, item ->
                db.discovery_suggestionsQueries.insert(
                    media_type = mediaType.key,
                    row_type = rowType.key,
                    title = item.title,
                    clean_title = item.cleanTitle,
                    cover_url = item.coverUrl,
                    reason = item.reason,
                    seed_title = item.seedTitle,
                    provider = item.provider,
                    score = item.score,
                    position = index.toLong(),
                    created_at = item.createdAt,
                )
            }
        }
    }

    override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> =
        handler.awaitList { db ->
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, _ -> clean_title }
        }.toSet()

    override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) {
        handler.await { db ->
            db.discovery_hiddenQueries.insertOrIgnore(mediaType.key, cleanTitle, System.currentTimeMillis())
        }
    }

    override suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String) {
        handler.await { db -> db.discovery_hiddenQueries.delete(mediaType.key, cleanTitle) }
    }

    override suspend fun clearHidden(mediaType: DiscoveryMediaType) {
        handler.await { db -> db.discovery_hiddenQueries.deleteAllByMedia(mediaType.key) }
    }

    override fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>> =
        handler.subscribeToList { db ->
            db.discovery_blacklist_tagsQueries.selectAllByMedia(mediaType.key) { _, tag, _ -> tag }
        }.map { tags -> tags.toSet() }

    override suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String> =
        handler.awaitList { db ->
            db.discovery_blacklist_tagsQueries.selectAllByMedia(mediaType.key) { _, tag, _ -> tag }
        }.toSet()

    override suspend fun blacklistTag(mediaType: DiscoveryMediaType, tag: String) {
        handler.await { db ->
            db.discovery_blacklist_tagsQueries.insertOrIgnore(mediaType.key, tag, System.currentTimeMillis())
        }
    }

    override suspend fun unblacklistTag(mediaType: DiscoveryMediaType, tag: String) {
        handler.await { db -> db.discovery_blacklist_tagsQueries.delete(mediaType.key, tag) }
    }

    override suspend fun clearBlacklist(mediaType: DiscoveryMediaType) {
        handler.await { db -> db.discovery_blacklist_tagsQueries.deleteAllByMedia(mediaType.key) }
    }

    override suspend fun getHiddenEntries(mediaType: DiscoveryMediaType): List<DiscoveryHiddenEntry> =
        handler.awaitList { db ->
            db.discovery_hiddenQueries.selectAllByMedia(mediaType.key) { _, clean_title, hidden_at ->
                DiscoveryHiddenEntry(clean_title, hidden_at)
            }
        }

    override suspend fun getBlacklistEntries(mediaType: DiscoveryMediaType): List<DiscoveryBlacklistEntry> =
        handler.awaitList { db ->
            db.discovery_blacklist_tagsQueries.selectAllByMedia(mediaType.key) { _, tag, added_at ->
                DiscoveryBlacklistEntry(tag, added_at)
            }
        }

    override suspend fun restoreHiddenEntries(
        mediaType: DiscoveryMediaType,
        entries: List<DiscoveryHiddenEntry>,
    ) {
        handler.await(inTransaction = true) { db ->
            entries.forEach {
                db.discovery_hiddenQueries.insertOrIgnore(mediaType.key, it.cleanTitle, it.hiddenAt)
            }
        }
    }

    override suspend fun restoreBlacklistEntries(
        mediaType: DiscoveryMediaType,
        entries: List<DiscoveryBlacklistEntry>,
    ) {
        handler.await(inTransaction = true) { db ->
            entries.forEach {
                db.discovery_blacklist_tagsQueries.insertOrIgnore(mediaType.key, it.tag, it.addedAt)
            }
        }
    }

    override suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long? =
        handler.awaitOne { db -> db.discovery_suggestionsQueries.lastUpdatedAt(mediaType.key) }.takeIf { it > 0L }
}
