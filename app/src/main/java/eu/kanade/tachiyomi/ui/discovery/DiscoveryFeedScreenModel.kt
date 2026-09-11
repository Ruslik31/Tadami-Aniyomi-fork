package eu.kanade.tachiyomi.ui.discovery

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryLibraryAdder
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.dedupeCrossRow
import eu.kanade.tachiyomi.data.discovery.expandGenreSet
import eu.kanade.tachiyomi.data.discovery.interleaveMix
import eu.kanade.tachiyomi.data.discovery.isBlacklisted
import eu.kanade.tachiyomi.data.discovery.rrfScores
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.util.system.isRunningFlow
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class DiscoveryFeedUiState(
    val mediaType: DiscoveryMediaType = DiscoveryMediaType.ANIME,
    val rows: Map<DiscoveryRowType, List<DiscoverySuggestion>> = emptyMap(),
    val mix: List<DiscoverySuggestion> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val lastUpdatedAt: Long? = null,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true,
    val hiddenSnackbarTitle: String? = null,
    val addedSnackbarTitle: String? = null,
    // «+» промахнулся (внешний провайдер или нет точного совпадения): экран
    // открывает каталог источника/глобальный поиск вместо тупика.
    val searchFallbackItem: DiscoverySuggestion? = null,
    val addingTitles: Set<String> = emptySet(),
    // Ряды, упавшие при последней генерации: в ленте показан устаревший кэш —
    // surfaced баннером вместо молчаливой стужи.
    val failedRows: Set<DiscoveryRowType> = emptySet(),
    // B2: «Скрыть всё с тегом X» — tag к числу затронутых подборок (undo-snackbar).
    val tagSnackbar: Pair<String, Int>? = null,
)

/** CSV ключей упавших рядов из prefs → набор [DiscoveryRowType]. */
internal fun parseFailedRows(csv: String): Set<DiscoveryRowType> = csv
    .splitToSequence(",")
    .filter { it.isNotBlank() }
    .mapNotNull { DiscoveryRowType.fromKey(it) }
    .toSet()

/** Сколько текущих подборок скроется при блэклисте [tag] (для undo-snackbar «Скрыто N»). */
internal fun countBlacklistImpact(items: List<DiscoverySuggestion>, tag: String): Int {
    val expanded = expandGenreSet(listOf(tag))
    return items.count { isBlacklisted(it, expanded) }
}

internal enum class FeedSignalTab { MIX, SIMILAR, TASTE, FRESH, SOURCE }

internal fun FeedSignalTab.rowType(): DiscoveryRowType? = when (this) {
    FeedSignalTab.MIX -> null
    FeedSignalTab.SIMILAR -> DiscoveryRowType.LIKE
    FeedSignalTab.TASTE -> DiscoveryRowType.TASTE
    FeedSignalTab.FRESH -> DiscoveryRowType.TREND
    FeedSignalTab.SOURCE -> DiscoveryRowType.SOURCE
}

internal fun itemsForTab(state: DiscoveryFeedUiState, tab: FeedSignalTab): List<DiscoverySuggestion> =
    if (tab == FeedSignalTab.MIX) state.mix else state.rows[tab.rowType()].orEmpty()

internal fun filterByProvider(items: List<DiscoverySuggestion>, provider: String?): List<DiscoverySuggestion> =
    if (provider.isNullOrEmpty()) items else items.filter { it.provider == provider }

internal fun providerOptions(state: DiscoveryFeedUiState): List<String> =
    state.rows.values.flatten().map { it.provider }.distinct().sorted()

internal const val DISCOVERY_REFRESH_COOLDOWN_MS = 5 * 60_000L

internal fun remainingCooldownSeconds(
    lastRefreshAt: Long?,
    now: Long,
    cooldownMs: Long = DISCOVERY_REFRESH_COOLDOWN_MS,
): Long {
    if (lastRefreshAt == null) return 0L
    val elapsed = now - lastRefreshAt
    return if (elapsed in 0 until cooldownMs) {
        val remainingMs = cooldownMs - elapsed
        (remainingMs + 999L) / 1000L
    } else {
        0L
    }
}

internal fun canManualRefresh(
    lastRefreshAt: Long?,
    now: Long,
    cooldownMs: Long = DISCOVERY_REFRESH_COOLDOWN_MS,
): Boolean = remainingCooldownSeconds(lastRefreshAt, now, cooldownMs) == 0L

internal enum class UpdatedLabelKind { MINUTES, HOURS, NEVER }

internal fun resolveUpdatedLabel(lastUpdatedAt: Long?, now: Long): Pair<UpdatedLabelKind, Long?> {
    if (lastUpdatedAt == null) return UpdatedLabelKind.NEVER to null
    val diff = (now - lastUpdatedAt).coerceAtLeast(0)
    val minutes = diff / 60_000
    return when {
        minutes < 60 -> UpdatedLabelKind.MINUTES to minutes
        else -> UpdatedLabelKind.HOURS to (minutes / 60)
    }
}

internal fun groupFeedRows(items: List<DiscoverySuggestion>): Map<DiscoveryRowType, List<DiscoverySuggestion>> =
    items.groupBy { it.rowType }
        .toSortedMap(compareBy { it.ordinal })

internal fun DiscoverySuggestion.toSuggestionItem(): SuggestionItem = SuggestionItem(
    title = title,
    searchQueries = listOf(title),
    thumbnailUrl = coverUrl,
    providerName = provider,
    providerUrl = "",
    providerId = null,
    mediaType = when (mediaType) {
        DiscoveryMediaType.ANIME -> SuggestionMediaType.ANIME
        DiscoveryMediaType.MANGA -> SuggestionMediaType.MANGA
        DiscoveryMediaType.NOVEL -> SuggestionMediaType.NOVEL
    },
    reason = when (provider.lowercase()) {
        "anilist" -> SuggestionReason.EXTERNAL_ANILIST
        "myanimelist", "mal" -> SuggestionReason.EXTERNAL_MAL
        "mangaupdates" -> SuggestionReason.EXTERNAL_MU
        "novelupdates" -> SuggestionReason.EXTERNAL_NU
        "shikimori" -> SuggestionReason.EXTERNAL_SHIKIMORI
        else -> SuggestionReason.SEARCH_TITLE
    },
)

/**
 * Полный экран «Для тебя» v3: читает только кэш ленты из БД (ноль сети на рендер);
 *_mix_ = интерлив квот сигналов; табы/чипсы фильтруют поток; «+» добавляет в
 * библиотеку точным поиском в источнике рекомендации (для внешних провайдеров
 * и при промахе открывает поиск); лонг-пресс скрывает с Undo.
 */
class DiscoveryFeedScreenModel(
    initialMedia: DiscoveryMediaType,
    private val context: Context,
    private val repository: DiscoveryRepository = Injekt.get(),
    private val adder: DiscoveryLibraryAdder = DiscoveryLibraryAdder(),
    private val preferences: DiscoveryPreferences = Injekt.get(),
) : StateScreenModel<DiscoveryFeedUiState>(DiscoveryFeedUiState(mediaType = initialMedia)) {

    private var observeJob: Job? = null
    private var lastHidden: DiscoverySuggestion? = null
    private var lastBlacklistedTag: String? = null

    fun start() {
        observeMedia(state.value.mediaType)
    }

    private fun observeMedia(mediaType: DiscoveryMediaType) {
        observeJob?.cancel()
        observeJob = screenModelScope.launchIO {
            combine(
                repository.subscribe(mediaType),
                refreshingFlow(),
                repository.subscribeHidden(mediaType),
                preferences.lastFailedRows(mediaType).changes(),
                repository.subscribeBlacklist(mediaType),
            ) { all, refreshing, hidden, failedCsv, blacklist ->
                FeedSources(all, refreshing, hidden, failedCsv, blacklist)
            }
                .collectLatest { (all, refreshing, hidden, failedCsv, blacklist) ->
                    // Кросс-рядовой дедуп: упавший ряд живёт старым кэшем и может
                    // содержать тайтлы свежих рядов — приоритет у rowType.ordinal.
                    val expandedBlacklist = expandGenreSet(blacklist.toList())
                    val visible = dedupeCrossRow(
                        all.filterNot { it.cleanTitle in hidden || isBlacklisted(it, expandedBlacklist) },
                    )
                    val rows = groupFeedRows(visible)
                    val rowItems = rows.mapValues { (_, items) -> items.map { it.toRowItem() } }
                    // RRF для fill-фазы: сырые скоры сигналов несопоставимы (LIKE 0..100 vs TREND 0.0).
                    val mix = interleaveMix(rowItems, rrf = rrfScores(rowItems))
                        .mapNotNull { row -> visible.firstOrNull { it.cleanTitle == row.cleanTitle } }
                    mutableState.update {
                        it.copy(
                            rows = rows,
                            mix = mix,
                            hidden = hidden,
                            lastUpdatedAt = all.maxOfOrNull { s -> s.createdAt },
                            isRefreshing = refreshing,
                            isLoading = false,
                            failedRows = parseFailedRows(failedCsv),
                        )
                    }
                }
        }
    }

    private data class FeedSources(
        val all: List<DiscoverySuggestion>,
        val refreshing: Boolean,
        val hidden: Set<String>,
        val failedCsv: String,
        val blacklist: Set<String>,
    )

    private fun DiscoverySuggestion.toRowItem() = DiscoveryRowItem(
        title = title,
        cleanTitle = cleanTitle,
        coverUrl = coverUrl,
        reason = reason,
        seedTitle = seedTitle,
        provider = provider,
        score = score,
    )

    private fun refreshingFlow(): Flow<Boolean> =
        context.workManager.isRunningFlow(DiscoveryUpdateJob.TAG_MANUAL)

    fun refreshNow(): Long {
        val now = System.currentTimeMillis()
        // Cooldown считается от нажатия (общий pref с home-рефрешем), а не от возраста контента:
        // фоновое автообновление больше не блокирует ручное.
        val remaining = remainingCooldownSeconds(
            preferences.manualRefreshAt().get().takeIf { it > 0L },
            now,
        )
        if (remaining > 0L) return remaining
        preferences.manualRefreshAt().set(now)
        DiscoveryUpdateJob.refreshNow(context, state.value.mediaType)
        return 0L
    }

    fun hide(item: DiscoverySuggestion) {
        lastHidden = item
        mutableState.update { it.copy(hiddenSnackbarTitle = item.title) }
        screenModelScope.launchIO {
            repository.hide(state.value.mediaType, item.cleanTitle)
        }
    }

    fun undoHide() {
        val item = lastHidden ?: return
        mutableState.update { it.copy(hiddenSnackbarTitle = null) }
        screenModelScope.launchIO {
            repository.unhide(state.value.mediaType, item.cleanTitle)
        }
    }

    fun dismissHiddenSnackbar() = mutableState.update { it.copy(hiddenSnackbarTitle = null) }

    /** B2: теговый блэклист — мгновенно фильтрует TASTE-ряд (read-time) и будущие генерации. */
    fun blacklistTag(tag: String) {
        val impacted = countBlacklistImpact(state.value.rows.values.flatten(), tag)
        lastBlacklistedTag = tag
        mutableState.update { it.copy(tagSnackbar = tag to impacted) }
        screenModelScope.launchIO {
            repository.blacklistTag(state.value.mediaType, tag)
        }
    }

    fun undoBlacklistTag() {
        val tag = lastBlacklistedTag ?: return
        mutableState.update { it.copy(tagSnackbar = null) }
        screenModelScope.launchIO {
            repository.unblacklistTag(state.value.mediaType, tag)
        }
    }

    fun dismissTagSnackbar() = mutableState.update { it.copy(tagSnackbar = null) }

    fun addToLibrary(item: DiscoverySuggestion) {
        if (item.title in state.value.addingTitles) return
        mutableState.update { it.copy(addingTitles = it.addingTitles + item.title) }
        screenModelScope.launchIO {
            val ok = try {
                adder.addFromProvider(state.value.mediaType, item.title, item.provider)
            } finally {
                mutableState.update { it.copy(addingTitles = it.addingTitles - item.title) }
            }
            mutableState.update {
                if (ok) {
                    it.copy(addedSnackbarTitle = item.title, searchFallbackItem = null)
                } else {
                    it.copy(searchFallbackItem = item)
                }
            }
        }
    }

    fun dismissSearchFallback() = mutableState.update { it.copy(searchFallbackItem = null) }

    fun dismissAddedSnackbar() = mutableState.update { it.copy(addedSnackbarTitle = null) }
}
