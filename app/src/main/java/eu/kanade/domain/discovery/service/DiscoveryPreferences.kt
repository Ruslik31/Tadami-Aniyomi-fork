package eu.kanade.domain.discovery.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class DiscoveryPreferences(private val preferenceStore: PreferenceStore) {

    fun discoveryEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_enabled", true)
    fun homeHeroMode(): Preference<String> = preferenceStore.getString("home_hero_mode", "continue")

    fun rowLikeEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_like", true)
    fun rowTasteEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_taste", true)
    fun rowTrendEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_trend", true)
    fun rowSourceEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_source", true)

    fun seedCount(): Preference<Int> = preferenceStore.getInt("discovery_seed_count", 5)
    fun seedCompleted(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_completed", true)
    fun seedActive14(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_active14", true)
    fun seedAdded(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_added", false)
    fun seedCompletedDays(): Preference<Int> = preferenceStore.getInt("discovery_seed_completed_days", 30)
    fun seedActiveDays(): Preference<Int> = preferenceStore.getInt("discovery_seed_active_days", 14)

    /** Slice 2: требует join с треками; до этого в настройках не показывать. */
    fun seedRated(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_rated", false)

    fun trendSeason(): Preference<String> = preferenceStore.getString("discovery_trend_season", "current")
    fun trendSort(): Preference<String> = preferenceStore.getString("discovery_trend_sort", "popularity")

    fun refreshIntervalHours(): Preference<Int> = preferenceStore.getInt("discovery_refresh_interval", 2)
    fun refreshAfterLibrary(): Preference<Boolean> = preferenceStore.getBoolean("discovery_refresh_after_library", true)
    fun refreshWifiOnly(): Preference<Boolean> = preferenceStore.getBoolean("discovery_refresh_wifi_only", false)

    fun teaserCount(): Preference<Int> = preferenceStore.getInt("discovery_teaser_count", 16)
    fun showReasons(): Preference<Boolean> = preferenceStore.getBoolean("discovery_show_reasons", true)
}
