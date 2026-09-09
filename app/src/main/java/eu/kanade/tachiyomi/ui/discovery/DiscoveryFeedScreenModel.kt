package eu.kanade.tachiyomi.ui.discovery

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.discovery.DiscoveryLibraryAdder
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.interleaveMix
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
    val notFoundTitle: String? = null,
    val addingTitles: Set<String> = emptySet(),
)

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

internal fun canManualRefresh(
    lastRefreshAt: Long?,
    now: Long,
    cooldownMs: Long = DISCOVERY_REFRESH_COOLDOWN_MS,
): Boolean = lastRefreshAt == null || now - lastRefreshAt >= cooldownMs

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
    reason = when (provider) {
        "anilist" -> SuggestionReason.EXTERNAL_ANILIST
        "mal" -> SuggestionReason.EXTERNAL_MAL
        "mangaupdates" -> SuggestionReason.EXTERNAL_MU
        "novelupdates" -> SuggestionReason.EXTERNAL_NU
        else -> SuggestionReason.SEARCH_TITLE
    },
)

/**
 * Полный экран «Для тебя» v3: читает только кэш ленты из БД (ноль сети на рендер);
 *_mix_ = интерлив квот сигналов; табы/чипсы фильтруют поток; «+» добавляет в
 * библиотеку через точный поиск в источниках; лонг-пресс скрывает с Undo.
 */
class DiscoveryFeedScreenModel(
    initialMedia: DiscoveryMediaType,
    private val context: Context,
    private val repository: DiscoveryRepository = Injekt.get(),
    private val adder: DiscoveryLibraryAdder = DiscoveryLibraryAdder(),
) : StateScreenModel<DiscoveryFeedUiState>(DiscoveryFeedUiState(mediaType = initialMedia)) {

    private var observeJob: Job? = null
    private var lastHidden: DiscoverySuggestion? = null

    fun start() {
        observeMedia(state.value.mediaType)
    }

    fun selectMedia(mediaType: DiscoveryMediaType) {
        if (state.value.mediaType == mediaType) return
        mutableState.update { it.copy(mediaType = mediaType, isLoading = true) }
        observeMedia(mediaType)
    }

    private fun observeMedia(mediaType: DiscoveryMediaType) {
        observeJob?.cancel()
        observeJob = screenModelScope.launchIO {
            combine(
                repository.subscribe(mediaType),
                refreshingFlow(),
                repository.subscribeHidden(mediaType),
            ) { all, refreshing, hidden -> Triple(all, refreshing, hidden) }
                .collectLatest { (all, refreshing, hidden) ->
                    val visible = all.filterNot { it.cleanTitle in hidden }
                    val rows = groupFeedRows(visible)
                    val mix = interleaveMix(rows.mapValues { (_, items) -> items.map { it.toRowItem() } })
                        .mapNotNull { row -> visible.firstOrNull { it.cleanTitle == row.cleanTitle } }
                    mutableState.update {
                        it.copy(
                            rows = rows,
                            mix = mix,
                            hidden = hidden,
                            lastUpdatedAt = all.maxOfOrNull { s -> s.createdAt },
                            isRefreshing = refreshing,
                            isLoading = false,
                        )
                    }
                }
        }
    }

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

    fun refreshNow() {
        val now = System.currentTimeMillis()
        if (!canManualRefresh(state.value.lastUpdatedAt, now)) return
        DiscoveryUpdateJob.refreshNow(context)
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

    fun addToLibrary(item: DiscoverySuggestion) {
        if (item.title in state.value.addingTitles) return
        mutableState.update { it.copy(addingTitles = it.addingTitles + item.title) }
        screenModelScope.launchIO {
            val ok = try {
                adder.addFirstMatch(state.value.mediaType, item.title)
            } finally {
                mutableState.update { it.copy(addingTitles = it.addingTitles - item.title) }
            }
            mutableState.update {
                it.copy(
                    addedSnackbarTitle = if (ok) item.title else null,
                    notFoundTitle = if (ok) null else item.title,
                )
            }
        }
    }

    fun dismissNotFound() = mutableState.update { it.copy(notFoundTitle = null) }

    fun dismissAddedSnackbar() = mutableState.update { it.copy(addedSnackbarTitle = null) }
}
