package eu.kanade.domain.discovery.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.discovery.model.DiscoveryMediaType

class DiscoveryPreferences(private val preferenceStore: PreferenceStore) {

    fun discoveryEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_enabled", true)
    fun homeHeroMode(): Preference<String> = preferenceStore.getString("home_hero_mode", "continue")

    fun rowLikeEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_like", true)
    fun rowTasteEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_taste", true)
    fun rowTrendEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_trend", true)
    fun rowSourceEnabled(): Preference<Boolean> = preferenceStore.getBoolean("discovery_row_source", true)

    fun seedCount(): Preference<Int> = preferenceStore.getInt("discovery_seed_count", 3)
    fun seedCompleted(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_completed", true)
    fun seedActive14(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_active14", true)
    fun seedAdded(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_added", false)
    fun seedCompletedDays(): Preference<Int> = preferenceStore.getInt("discovery_seed_completed_days", 30)
    fun seedActiveDays(): Preference<Int> = preferenceStore.getInt("discovery_seed_active_days", 14)

    /** Slice 2: требует join с треками; до этого в настройках не показывать. */
    fun seedRated(): Preference<Boolean> = preferenceStore.getBoolean("discovery_seed_rated", false)

    fun trendSeason(): Preference<String> = preferenceStore.getString("discovery_trend_season", "current")
    fun trendSort(): Preference<String> = preferenceStore.getString("discovery_trend_sort", "popularity")

    fun refreshIntervalHours(): Preference<Int> = preferenceStore.getInt("discovery_refresh_interval", 12)
    fun refreshAfterLibrary(): Preference<Boolean> = preferenceStore.getBoolean("discovery_refresh_after_library", true)
    fun refreshWifiOnly(): Preference<Boolean> = preferenceStore.getBoolean("discovery_refresh_wifi_only", false)

    /** Момент последнего РУЧНОГО обновления (общий cooldown home/feed, 5 мин от нажатия). */
    fun manualRefreshAt(): Preference<Long> = preferenceStore.getLong("discovery_manual_refresh_at", 0L)

    /**
     * Независимый фильтр контента 18+ во внешних провайдерах подборок.
     * Игнорирует общесистемную NSFW-настройку приложения: включён — фильтрует всегда.
     * Каталоги плагинов (ряд SOURCE) регулируются общими настройками приложения.
     */
    fun filterNsfw(): Preference<Boolean> = preferenceStore.getBoolean("discovery_filter_nsfw", true)

    /** CSV ключей рядов, упавших при последней генерации (для баннера «показан кэш»). */
    fun lastFailedRows(mediaType: DiscoveryMediaType): Preference<String> =
        preferenceStore.getString("discovery_failed_rows_" + mediaType.key, "")

    fun teaserCount(): Preference<Int> = preferenceStore.getInt("discovery_teaser_count", 16)
    fun showReasons(): Preference<Boolean> = preferenceStore.getBoolean("discovery_show_reasons", true)

    fun collageRotationIntervalHours(): Preference<Int> = preferenceStore.getInt("collage_rotation_interval_hours", 2)
    fun collageAnimationSpeed(): Preference<String> = preferenceStore.getString("collage_animation_speed", "normal")
    fun collageLastRotationTime(): Preference<Long> = preferenceStore.getLong("collage_last_rotation_time", 0L)
}
