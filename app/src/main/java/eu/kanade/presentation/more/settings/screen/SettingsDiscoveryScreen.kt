package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

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
        val scope = rememberCoroutineScope()
        val repository = remember { Injekt.get<DiscoveryRepository>() }
        var showResetHiddenDialog by remember { mutableStateOf(false) }
        var showBlacklistDialog by remember { mutableStateOf(false) }
        var blacklistTags by remember { mutableStateOf<List<Pair<DiscoveryMediaType, String>>>(emptyList()) }
        val reloadBlacklist: suspend () -> Unit = {
            blacklistTags = DiscoveryMediaType.entries.flatMap { media ->
                repository.getBlacklistedTags(media).sorted().map { media to it }
            }
        }

        val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
        val sourcePreferences = remember { Injekt.get<eu.kanade.domain.source.service.SourcePreferences>() }

        val enabled by discoveryPreferences.discoveryEnabled().collectAsStateWithLifecycle()
        val rowLike by discoveryPreferences.rowLikeEnabled().collectAsStateWithLifecycle()
        val rowTaste by discoveryPreferences.rowTasteEnabled().collectAsStateWithLifecycle()
        val rowTrend by discoveryPreferences.rowTrendEnabled().collectAsStateWithLifecycle()
        val rowSource by discoveryPreferences.rowSourceEnabled().collectAsStateWithLifecycle()
        val seedCompleted by discoveryPreferences.seedCompleted().collectAsStateWithLifecycle()
        val seedActive14 by discoveryPreferences.seedActive14().collectAsStateWithLifecycle()
        val homeHeroMode by discoveryPreferences.homeHeroMode().collectAsStateWithLifecycle()
        val isCollageMode = homeHeroMode == "collage"

        if (showResetHiddenDialog) {
            AlertDialog(
                onDismissRequest = { showResetHiddenDialog = false },
                title = { Text(stringResource(AYMR.strings.pref_discovery_clear_hidden_dialog_title)) },
                text = { Text(stringResource(AYMR.strings.pref_discovery_clear_hidden_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetHiddenDialog = false
                            scope.launchIO {
                                DiscoveryMediaType.entries.forEach { repository.clearHidden(it) }
                                withUIContext {
                                    context.toast(
                                        context.contextStringResource(AYMR.strings.pref_discovery_clear_hidden_success),
                                    )
                                }
                            }
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetHiddenDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        if (showBlacklistDialog) {
            AlertDialog(
                onDismissRequest = { showBlacklistDialog = false },
                title = { Text(stringResource(AYMR.strings.pref_discovery_blacklist_tags)) },
                text = {
                    if (blacklistTags.isEmpty()) {
                        Text(stringResource(AYMR.strings.pref_discovery_blacklist_empty))
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            blacklistTags.forEach { (media, tag) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launchIO {
                                                repository.unblacklistTag(media, tag)
                                                reloadBlacklist()
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "$tag · ${media.key}",
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                DiscoveryMediaType.entries.forEach { repository.clearBlacklist(it) }
                                showBlacklistDialog = false
                            }
                        },
                    ) {
                        Text(stringResource(AYMR.strings.pref_discovery_blacklist_reset))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBlacklistDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_general),
                preferenceItems = buildList {
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = discoveryPreferences.discoveryEnabled(),
                            title = stringResource(AYMR.strings.pref_discovery_enabled),
                            subtitle = stringResource(AYMR.strings.pref_discovery_enabled_summary),
                            onValueChanged = {
                                DiscoveryUpdateJob.setupTask(context)
                                true
                            },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = discoveryPreferences.filterNsfw(),
                            title = stringResource(AYMR.strings.pref_discovery_nsfw_filter),
                            subtitle = stringResource(AYMR.strings.pref_discovery_nsfw_filter_summary),
                            enabled = enabled,
                        ),
                    )
                    add(
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
                    )
                    if (isCollageMode) {
                        add(
                            Preference.PreferenceItem.ListPreference(
                                preference = discoveryPreferences.collageRotationIntervalHours(),
                                entries = persistentMapOf(
                                    0 to stringResource(AYMR.strings.pref_collage_rotation_interval_0),
                                    1 to stringResource(AYMR.strings.pref_collage_rotation_interval_1),
                                    2 to stringResource(AYMR.strings.pref_collage_rotation_interval_2),
                                    4 to stringResource(AYMR.strings.pref_collage_rotation_interval_4),
                                    6 to stringResource(AYMR.strings.pref_collage_rotation_interval_6),
                                    12 to stringResource(AYMR.strings.pref_collage_rotation_interval_12),
                                    24 to stringResource(AYMR.strings.pref_collage_rotation_interval_24),
                                ),
                                title = stringResource(AYMR.strings.pref_collage_rotation_interval),
                                subtitleProvider = { value, entries -> entries[value] },
                                enabled = enabled,
                            ),
                        )
                        add(
                            Preference.PreferenceItem.ListPreference(
                                preference = discoveryPreferences.collageAnimationSpeed(),
                                entries = persistentMapOf(
                                    "fast" to stringResource(AYMR.strings.pref_collage_animation_speed_fast),
                                    "normal" to stringResource(AYMR.strings.pref_collage_animation_speed_normal),
                                    "smooth" to stringResource(AYMR.strings.pref_collage_animation_speed_smooth),
                                ),
                                title = stringResource(AYMR.strings.pref_collage_animation_speed),
                                subtitleProvider = { value, entries -> entries[value] },
                                enabled = enabled,
                            ),
                        )
                    }
                }.toPersistentList(),
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
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.seedCompletedDays(),
                        entries = persistentMapOf(
                            7 to stringResource(AYMR.strings.pref_discovery_days_7),
                            14 to stringResource(AYMR.strings.pref_discovery_days_14),
                            30 to stringResource(AYMR.strings.pref_discovery_days_30),
                            60 to stringResource(AYMR.strings.pref_discovery_days_60),
                            90 to stringResource(AYMR.strings.pref_discovery_days_90),
                            180 to stringResource(AYMR.strings.pref_discovery_days_180),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_seed_completed_days),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled && rowLike && seedCompleted,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.seedActive14(),
                        title = stringResource(AYMR.strings.pref_discovery_seed_active14),
                        enabled = enabled && rowLike,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.seedActiveDays(),
                        entries = persistentMapOf(
                            7 to stringResource(AYMR.strings.pref_discovery_days_7),
                            14 to stringResource(AYMR.strings.pref_discovery_days_14),
                            30 to stringResource(AYMR.strings.pref_discovery_days_30),
                            60 to stringResource(AYMR.strings.pref_discovery_days_60),
                        ),
                        title = stringResource(AYMR.strings.pref_discovery_seed_active_days),
                        subtitleProvider = { value, entries -> entries[value] },
                        enabled = enabled && rowLike && seedActive14,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.seedAdded(),
                        title = stringResource(AYMR.strings.pref_discovery_seed_added),
                        enabled = enabled && rowLike,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.suggestionsUseShikimori(),
                        title = stringResource(AYMR.strings.pref_discovery_provider_shikimori),
                        subtitle = stringResource(AYMR.strings.pref_discovery_provider_shikimori_summary),
                        enabled = enabled && rowLike,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.suggestionsUseMangaUpdatesNovel(),
                        title = stringResource(AYMR.strings.pref_discovery_provider_mangaupdates),
                        subtitle = stringResource(AYMR.strings.pref_discovery_provider_mangaupdates_summary),
                        enabled = enabled && rowLike,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.suggestionsUseNovelUpdates(),
                        title = stringResource(AYMR.strings.pref_discovery_provider_novelupdates),
                        subtitle = stringResource(AYMR.strings.pref_discovery_provider_novelupdates_summary),
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
                title = stringResource(AYMR.strings.pref_discovery_group_signals),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.rowTasteEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_row_taste),
                        subtitle = stringResource(AYMR.strings.pref_discovery_row_taste_summary),
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = discoveryPreferences.rowSourceEnabled(),
                        title = stringResource(AYMR.strings.pref_discovery_row_source),
                        subtitle = stringResource(AYMR.strings.pref_discovery_row_source_summary),
                        enabled = enabled,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_discovery_group_updates),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = discoveryPreferences.refreshIntervalHours(),
                        entries = persistentMapOf(
                            2 to stringResource(AYMR.strings.pref_discovery_interval_2),
                            6 to stringResource(AYMR.strings.pref_discovery_interval_6),
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
                            12 to "12",
                            16 to "16",
                            20 to "20",
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
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_clear_hidden_title),
                        subtitle = stringResource(AYMR.strings.pref_discovery_clear_hidden_summary),
                        onClick = { showResetHiddenDialog = true },
                        enabled = enabled,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_discovery_blacklist_tags),
                        subtitle = stringResource(AYMR.strings.pref_discovery_blacklist_tags_summary),
                        onClick = {
                            showBlacklistDialog = true
                            scope.launchIO { reloadBlacklist() }
                        },
                        enabled = enabled,
                    ),
                ),
            ),
        )
    }
}
