package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Настройки ленты «Для тебя» (discovery). Правило адаптивных тумблеров:
 * мастер-выключатель серит все группы; выключенный ряд серит свои подустановки.
 */
object SettingsDiscoveryScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_discovery_title

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current

        val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }

        val enabled by discoveryPreferences.discoveryEnabled().collectAsStateWithLifecycle()
        val rowLike by discoveryPreferences.rowLikeEnabled().collectAsStateWithLifecycle()
        val rowTrend by discoveryPreferences.rowTrendEnabled().collectAsStateWithLifecycle()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_general),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.discoveryEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_enabled),
                        subtitle = stringResource(AYMR.strings.pref_discovery_enabled_summary),
                        onValueChanged = {
                            DiscoveryUpdateJob.setupTask(context)
                            true
                        },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.homeHeroMode(),
                        entries = persistentMapOf(
                            "continue" to stringResource(AYMR.strings.pref_home_hero_mode_continue),
                            "collage" to stringResource(AYMR.strings.pref_home_hero_mode_collage),
                            "hybrid" to stringResource(AYMR.strings.pref_home_hero_mode_hybrid),
                        ),
                        title = stringResource(AYMR.strings.pref_home_hero_mode),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_like),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.rowLikeEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_row_like),
                        subtitle = stringResource(AYMR.strings.pref_discovery_row_like_summary),
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.seedCount(),
                        entries = persistentMapOf(
                            1 to "1",
                            2 to "2",
                            3 to "3",
                            4 to "4",
                            5 to "5",
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_seed_count),
                        subtitleProvider = { value, _ ->
                            stringResource(AYMR.strings.pref_discovery_seed_count_summary, value)
                        },
                        enabled = enabled && rowLike,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.seedCompleted(),
                        title = stringResource(AYMR.strings.pref_discovery_seed_completed),
                        enabled = enabled && rowLike,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.seedActive14(),
                        title = stringResource(AYMR.strings.pref_discovery_seed_active14),
                        enabled = enabled && rowLike,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.seedAdded(),
                        title = stringResource(AYMR.strings.pref_discovery_seed_added),
                        enabled = enabled && rowLike,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_trend),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.rowTrendEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_row_trend),
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.trendSeason(),
                        entries = persistentMapOf(
                            "current" to stringResource(AYMR.strings.pref_discovery_trend_season_current),
                            "next" to stringResource(AYMR.strings.pref_discovery_trend_season_next),
                            "both" to stringResource(AYMR.strings.pref_discovery_trend_season_both),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_trend_season),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled && rowTrend,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.trendSort(),
                        entries = persistentMapOf(
                            "popularity" to stringResource(AYMR.strings.pref_discovery_trend_sort_popularity),
                            "score" to stringResource(AYMR.strings.pref_discovery_trend_sort_score),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_trend_sort),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled && rowTrend,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_updates),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.refreshIntervalHours(),
                        entries = persistentMapOf(
                            12 to stringResource(AYMR.strings.pref_discovery_interval_12),
                            24 to stringResource(AYMR.strings.pref_discovery_interval_24),
                            48 to stringResource(AYMR.strings.pref_discovery_interval_48),
                            168 to stringResource(AYMR.strings.pref_discovery_interval_weekly),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_refresh_interval),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled,
                        onValueChanged = {
                            DiscoveryUpdateJob.setupTask(context)
                            true
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.refreshAfterLibrary(),
                        title = stringResource(AYMR.strings.pref_discovery_refresh_after_library),
                        subtitle = stringResource(AYMR.strings.pref_discovery_refresh_after_library_summary),
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.refreshWifiOnly(),
                        title = stringResource(AYMR.strings.pref_discovery_wifi_only),
                        enabled = enabled,
                        onValueChanged = {
                            DiscoveryUpdateJob.setupTask(context)
                            true
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_display),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.teaserCount(),
                        entries = persistentMapOf(
                            3 to "3",
                            4 to "4",
                            5 to "5",
                            6 to "6",
                            7 to "7",
                            8 to "8",
                            9 to "9",
                            10 to "10",
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_teaser_count),
                        subtitleProvider = { value, _ -> value.toString() },
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.showReasons(),
                        title = stringResource(AYMR.strings.pref_discovery_show_reasons),
                        subtitle = stringResource(AYMR.strings.pref_discovery_show_reasons_summary),
                        enabled = enabled,
                    ),
                ),
            ),
        )
    }
}
