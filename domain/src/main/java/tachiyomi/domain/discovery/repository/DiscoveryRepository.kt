package tachiyomi.domain.discovery.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

interface DiscoveryRepository {
    fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>>
    fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>>
    suspend fun replaceRows(mediaType: DiscoveryMediaType, rowType: DiscoveryRowType, items: List<DiscoverySuggestion>)
    suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String>
    suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String)
    suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String)
    suspend fun clearHidden(mediaType: DiscoveryMediaType)
    suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long?

    /** Tag-blacklist «скрыть всё с тегом X» (B2). */
    fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>>
    suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String>
    suspend fun blacklistTag(mediaType: DiscoveryMediaType, tag: String)
    suspend fun unblacklistTag(mediaType: DiscoveryMediaType, tag: String)
    suspend fun clearBlacklist(mediaType: DiscoveryMediaType)
}
