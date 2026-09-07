package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (contract v20): a feed source whose service stores a per-account
 * blocked-tag list (content the user never wants to see), editable from the host.
 *
 * The host detects support with `source is AnimeBlockedTagsSource` (instanceof) — the same
 * marker-interface pattern as [AnimeCreatorFeedSource]/[AnimeReelsFeedbackSource]; no default
 * members are added to existing interfaces. Login-gated: logged-out sources answer with an
 * empty list / false and the host hides the editor.
 */
interface AnimeBlockedTagsSource {

    /** Current blocked tags of the account; empty when logged out or none. */
    suspend fun getBlockedTags(): List<String>

    /** Replaces the blocked-tag set. True on success. */
    suspend fun setBlockedTags(tags: List<String>): Boolean
}
