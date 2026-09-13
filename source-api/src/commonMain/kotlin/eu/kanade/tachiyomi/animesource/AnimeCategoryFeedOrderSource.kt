package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage

/**
 * Capability interface (contract v21): a browse-capable source whose category feeds support
 * source-defined ordering (e.g. a niche feed in hot/latest/top order). The host shows
 * [categoryFilters] in the filter sheet while in NICHE mode and passes the selected filters
 * back into [getCategoryFeed].
 *
 * The host detects support with `source is AnimeCategoryFeedOrderSource` (instanceof) — the
 * same marker-interface pattern as [AnimeCreatorFeedSource]/[AnimeReelsFeedbackSource]; no
 * default members are added to existing interfaces. Sources without it keep the plain
 * [AnimeFeedBrowseSource.getCategoryFeed] path and no filter sheet in NICHE mode.
 */
interface AnimeCategoryFeedOrderSource {

    /** Filters offered for category feeds (rendered by the host in NICHE mode). */
    fun categoryFilters(): AnimeFilterList

    /** Category feed with the selected order; replay-safe per the v17 sticky protocol. */
    suspend fun getCategoryFeed(
        categoryId: String,
        page: Int,
        cursor: String?,
        filters: AnimeFilterList,
    ): FeedPage
}
