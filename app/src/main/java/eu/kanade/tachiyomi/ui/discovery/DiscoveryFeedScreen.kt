package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.discovery.AniListTrendingSource
import eu.kanade.tachiyomi.data.discovery.DiscoveryMeta
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.discoveryReasonText
import eu.kanade.tachiyomi.ui.home.toHomeHubDiscoveryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable
import androidx.compose.foundation.lazy.items as lazyRowItems
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreenInterface
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as prefCollectAsStateWithLifecycle

/**
 * Полный экран ленты «Для тебя» v3 (компоновка V3): Aurora-glass тулбар, табы
 * сигналов [Микс|Похоже|Твой вкус|Свежее|Источник], чипсы провайдеров, сетка 3×N
 * унифицированных карточек (стиль «Недавно добавленные») с чипами-обоснованиями,
 * «+» добавить в библиотеку, лонг-пресс скрыть с Undo.
 */
class DiscoveryFeedScreen(val initialMediaKey: String) : Screen(), Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val appContext = remember(context) { context.applicationContext }
        val initialMedia = remember(initialMediaKey) {
            DiscoveryMediaType.fromKey(initialMediaKey) ?: DiscoveryMediaType.ANIME
        }
        val screenModel = rememberScreenModel {
            DiscoveryFeedScreenModel(initialMedia, context = appContext)
        }
        LaunchedEffect(Unit) { screenModel.start() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var tab by remember { mutableStateOf(FeedSignalTab.MIX) }
        var provider by remember { mutableStateOf<String?>(null) }
        val hazeState = remember { HazeState() }
        var sheetItem by remember { mutableStateOf<DiscoverySuggestion?>(null) }
        var sheetMeta by remember { mutableStateOf<DiscoveryMeta?>(null) }
        var sheetMetaLoading by remember { mutableStateOf(false) }
        val trendingSource = remember { AniListTrendingSource() }

        val navigateFor: (DiscoverySuggestion) -> Unit = { item ->
            scope.launch {
                if (item.rowType == DiscoveryRowType.SOURCE) {
                    // Ряд источника: открываем каталог источника напрямую, без глобального поиска.
                    val sourceId = when (state.mediaType) {
                        DiscoveryMediaType.ANIME -> Injekt.get<GetEnabledAnimeSources>()
                            .subscribe().first().firstOrNull { it.name == item.provider }?.id
                        DiscoveryMediaType.MANGA -> Injekt.get<GetEnabledMangaSources>()
                            .subscribe().first().firstOrNull { it.name == item.provider }?.id
                        DiscoveryMediaType.NOVEL -> Injekt.get<GetEnabledNovelSources>()
                            .subscribe().first().firstOrNull { it.name == item.provider }?.id
                    }
                    if (sourceId != null) {
                        navigator.push(
                            when (state.mediaType) {
                                DiscoveryMediaType.ANIME -> BrowseAnimeSourceScreen(sourceId, null)
                                DiscoveryMediaType.MANGA -> BrowseMangaSourceScreen(sourceId, null)
                                DiscoveryMediaType.NOVEL -> BrowseNovelSourceScreen(sourceId, null)
                            },
                        )
                        return@launch
                    }
                }
                val suggestionItem = item.toSuggestionItem()
                navigator.push(
                    suggestionItem.toDirectEntryScreenOrNull()
                        ?: suggestionItem.toGlobalSearchScreen(),
                )
            }
        }

        LaunchedEffect(sheetItem) {
            val item = sheetItem ?: return@LaunchedEffect
            sheetMeta = null
            sheetMetaLoading = true
            sheetMeta = runCatching { trendingSource.fetchMeta(item.title, state.mediaType) }.getOrNull()
            sheetMetaLoading = false
        }

        Box(Modifier.fillMaxSize().background(AuroraTheme.colors.background)) {
            Column(Modifier.fillMaxSize()) {
                FeedToolbar(
                    state = state,
                    hazeState = hazeState,
                    onBack = { navigator.pop() },
                    onRefresh = { screenModel.refreshNow() },
                )
                FeedTabs(tab = tab, onTab = { tab = it })
                FeedProviderChips(
                    options = providerOptions(state),
                    selected = provider,
                    onSelect = { provider = it },
                )
                FeedBody(
                    state = state,
                    hazeState = hazeState,
                    tab = tab,
                    provider = provider,
                    onItemClick = { sheetItem = it },
                    onItemLongClick = { screenModel.hide(it) },
                    onItemAdd = { screenModel.addToLibrary(it) },
                    onRetry = { screenModel.refreshNow() },
                )
            }
            FeedSnackbar(
                state = state,
                onUndo = { screenModel.undoHide() },
                onDismissHidden = { screenModel.dismissHiddenSnackbar() },
                onDismissAdded = { screenModel.dismissAddedSnackbar() },
                onDismissNotFound = { screenModel.dismissNotFound() },
            )
            sheetItem?.let { item ->
                DiscoveryPreviewSheet(
                    item = item,
                    meta = sheetMeta,
                    isMetaLoading = sheetMetaLoading,
                    onDismiss = { sheetItem = null },
                    onAdd = {
                        screenModel.addToLibrary(item)
                        sheetItem = null
                    },
                    onFind = {
                        sheetItem = null
                        navigateFor(item)
                    },
                    onHide = {
                        screenModel.hide(item)
                        sheetItem = null
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedToolbar(
    state: DiscoveryFeedUiState,
    hazeState: HazeState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val (labelKind, labelValue) = resolveUpdatedLabel(state.lastUpdatedAt, System.currentTimeMillis())
    val updatedLabel = when (labelKind) {
        UpdatedLabelKind.MINUTES ->
            stringResource(AYMR.strings.for_you_updated_minutes, labelValue?.toInt() ?: 0)
        UpdatedLabelKind.HOURS ->
            stringResource(AYMR.strings.for_you_updated_hours, labelValue?.toInt() ?: 0)
        UpdatedLabelKind.NEVER -> stringResource(AYMR.strings.for_you_updated_never)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = colors.background,
                    tint = HazeTint(colors.surface.copy(alpha = if (colors.isDark) 0.72f else 0.82f)),
                    blurRadius = 22.dp,
                    noiseFactor = 0.10f,
                ),
            )
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            appHaptics.tap()
            onBack()
        }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.textPrimary)
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                stringResource(AYMR.strings.aurora_for_you),
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(updatedLabel, color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {
            appHaptics.tap()
            onRefresh()
        }, enabled = !state.isRefreshing) {
            if (state.isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(AYMR.strings.for_you_refresh),
                    tint = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun FeedTabs(tab: FeedSignalTab, onTab: (FeedSignalTab) -> Unit) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedSignalTab.entries.forEach { signal ->
            val label = when (signal) {
                FeedSignalTab.MIX -> stringResource(AYMR.strings.for_you_tab_mix)
                FeedSignalTab.SIMILAR -> stringResource(AYMR.strings.for_you_tab_similar)
                FeedSignalTab.TASTE -> stringResource(AYMR.strings.for_you_tab_taste)
                FeedSignalTab.FRESH -> stringResource(AYMR.strings.for_you_tab_fresh)
                FeedSignalTab.SOURCE -> stringResource(AYMR.strings.for_you_tab_source)
            }
            val active = tab == signal
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) colors.accent else colors.cardBackground)
                    .then(
                        if (!active && (colors.isDark || colors.isEInk)) {
                            Modifier.border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        appHaptics.tap()
                        onTab(signal)
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) colors.textOnAccent else colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FeedProviderChips(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    if (options.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        lazyRowItems(items = listOf(null) + options, key = { it ?: "all" }) { option ->
            val active = option == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) colors.accent else colors.cardBackground)
                    .then(
                        if (!active && (colors.isDark || colors.isEInk)) {
                            Modifier.border(1.dp, colors.divider, RoundedCornerShape(50))
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        appHaptics.tap()
                        onSelect(option)
                    }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    option ?: stringResource(AYMR.strings.home_all_sources),
                    color = if (active) colors.textOnAccent else colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FeedBody(
    state: DiscoveryFeedUiState,
    hazeState: HazeState,
    tab: FeedSignalTab,
    provider: String?,
    onItemClick: (DiscoverySuggestion) -> Unit,
    onItemLongClick: (DiscoverySuggestion) -> Unit,
    onItemAdd: (DiscoverySuggestion) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val showReasons by discoveryPreferences.showReasons().prefCollectAsStateWithLifecycle()
    val similarTemplate = stringResource(AYMR.strings.for_you_reason_similar)
    val trendTemplate = stringResource(AYMR.strings.for_you_reason_trending)
    val nextTemplate = stringResource(AYMR.strings.for_you_reason_season_next)
    val items = remember(state, tab, provider) {
        filterByProvider(itemsForTab(state, tab), provider)
    }

    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent)
        }

        items.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(AYMR.strings.for_you_empty_title),
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(AYMR.strings.for_you_empty_subtitle),
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    appHaptics.tap()
                    onRetry()
                },
                enabled = !state.isRefreshing,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    stringResource(AYMR.strings.for_you_retry),
                    color = colors.textOnAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items = items, key = { it.rowType.key + ":" + it.cleanTitle }) { item ->
                val homeItem = remember(item) { item.toHomeHubDiscoveryItem() }
                val reason = if (showReasons) {
                    discoveryReasonText(homeItem, similarTemplate, trendTemplate, nextTemplate)
                } else {
                    null
                }
                FeedCard(
                    item = item,
                    reason = reason,
                    isAdding = item.title in state.addingTitles,
                    badge = discoveryBadge(item),
                    onClick = {
                        appHaptics.tap()
                        onItemClick(item)
                    },
                    onLongClick = {
                        appHaptics.tap()
                        onItemLongClick(item)
                    },
                    onAdd = {
                        appHaptics.tap()
                        onItemAdd(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedCard(
    item: DiscoverySuggestion,
    reason: String?,
    isAdding: Boolean,
    badge: DiscoveryBadge? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Portrait)
    val containerShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .clip(containerShape)
            .background(if (colors.isDark) colors.glass.copy(alpha = 0.10f) else colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, containerShape)
                } else {
                    Modifier
                },
            )
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(posterShape)
                .background(colors.cardBackground)
                .then(
                    if (colors.isDark || colors.isEInk) {
                        Modifier.border(1.dp, colors.divider, posterShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            AsyncImage(
                model = buildAuroraCoverImageRequest(context, item.coverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                modifier = Modifier.fillMaxSize(),
                error = fallbackPainter,
                fallback = fallbackPainter,
            )
            badge?.let { b ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor(b.colorKind))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        stringResource(b.textRes),
                        color = colors.textOnAccent,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(48.dp)
                    .clickable(enabled = !isAdding, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.textOnAccent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(AYMR.strings.for_you_add_library),
                            tint = colors.textOnAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Text(
            item.title,
            color = colors.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        if (reason != null) {
            Text(
                reason,
                color = colors.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun FeedSnackbar(
    state: DiscoveryFeedUiState,
    onUndo: () -> Unit,
    onDismissHidden: () -> Unit,
    onDismissAdded: () -> Unit,
    onDismissNotFound: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val hiddenTitle = state.hiddenSnackbarTitle
    val addedTitle = state.addedSnackbarTitle
    val notFoundTitle = state.notFoundTitle
    LaunchedEffect(hiddenTitle) {
        if (hiddenTitle != null) {
            delay(4000)
            onDismissHidden()
        }
    }
    LaunchedEffect(addedTitle) {
        if (addedTitle != null) {
            delay(4000)
            onDismissAdded()
        }
    }
    LaunchedEffect(notFoundTitle) {
        if (notFoundTitle != null) {
            delay(4000)
            onDismissNotFound()
        }
    }
    val message = when {
        hiddenTitle != null -> stringResource(AYMR.strings.for_you_hidden_snackbar)
        addedTitle != null -> stringResource(AYMR.strings.for_you_added_snackbar, addedTitle)
        notFoundTitle != null -> stringResource(AYMR.strings.for_you_add_not_found, notFoundTitle)
        else -> null
    } ?: return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = colors.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                if (hiddenTitle != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_undo),
                        color = colors.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            appHaptics.tap()
                            onUndo()
                        },
                    )
                }
            }
        }
    }
}
