package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.interleaveMix
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.ui.discovery.BadgeColorKind
import eu.kanade.tachiyomi.ui.discovery.DiscoveryBadge
import eu.kanade.tachiyomi.ui.discovery.badgeColor
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// ============================ Чистые функции (тестируются) ============================

/** Тизер = топ-N смешанного потока (интерлив квот сигналов), а не только LIKE-ряд. */
internal fun composeTeaserItems(
    items: List<DiscoverySuggestion>,
    limit: Int,
): List<HomeHubDiscoveryItem> {
    val capped = limit.coerceIn(3, 10)
    val rows = items.groupBy { it.rowType }
        .mapValues { (_, row) ->
            row.map { s ->
                DiscoveryRowItem(s.title, s.cleanTitle, s.coverUrl, s.reason, s.seedTitle, s.provider, s.score)
            }
        }
    val mixed = interleaveMix(rows, total = capped)
    return mixed.mapNotNull { row ->
        items.firstOrNull { it.cleanTitle == row.cleanTitle }?.toHomeHubDiscoveryItem()
    }
}

/** Секция видна всегда при включённом discovery: при пустой ленте рендерит карточку-вход на полный экран. */
internal fun shouldShowForYouSection(enabled: Boolean): Boolean = enabled

/**
 * Локализованная подпись-обоснование. Шаблоны строк передаются параметрами,
 * чтобы функция оставалась чистой (тестируемой без Compose).
 */
internal fun discoveryReasonText(
    item: HomeHubDiscoveryItem,
    similarTemplate: String,
    trendTemplate: String,
    nextSeasonTemplate: String,
): String? = when (item.rowType) {
    DiscoveryRowType.LIKE -> item.seedTitle?.let { similarTemplate.replace("%1\$s", it) }
    DiscoveryRowType.TREND -> if (item.reasonPayload == "next") nextSeasonTemplate else trendTemplate
    DiscoveryRowType.TASTE -> item.reasonPayload
    DiscoveryRowType.SOURCE -> null
}

/**
 * Режим hero с деградацией: Collage/Hybrid требуют включённый discovery с непустой лентой.
 */
internal fun resolveHeroPresentation(
    prefMode: HomeHeroMode,
    discoveryEnabled: Boolean,
    discoveryCount: Int,
): HomeHeroMode = when (prefMode) {
    HomeHeroMode.Continue -> HomeHeroMode.Continue
    HomeHeroMode.Collage ->
        if (discoveryEnabled && discoveryCount >= 3) HomeHeroMode.Collage else HomeHeroMode.Continue
    HomeHeroMode.Hybrid ->
        if (discoveryEnabled && discoveryCount > 0) HomeHeroMode.Hybrid else HomeHeroMode.Continue
}

internal fun DiscoverySuggestion.toHomeHubDiscoveryItem() = HomeHubDiscoveryItem(
    title = title,
    cleanTitle = cleanTitle,
    coverUrl = coverUrl,
    seedTitle = seedTitle,
    reasonPayload = reason,
    provider = provider,
    rowType = rowType,
    mediaType = mediaType,
)

internal fun HomeHubDiscoveryItem.toSuggestionItem(): SuggestionItem = SuggestionItem(
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

// ============================ UI ============================

/** Микро-бейдж сигнала для home-карточки (маппинг HomeHubDiscoveryItem → бейдж). */
internal fun discoveryBadgeOf(item: HomeHubDiscoveryItem): DiscoveryBadge? = when (item.rowType) {
    DiscoveryRowType.TASTE -> DiscoveryBadge(AYMR.strings.for_you_badge_taste, BadgeColorKind.TASTE)
    DiscoveryRowType.TREND ->
        DiscoveryBadge(
            if (item.reasonPayload == "next") AYMR.strings.for_you_badge_season else AYMR.strings.for_you_badge_trend,
            BadgeColorKind.FRESH,
        )
    DiscoveryRowType.SOURCE -> DiscoveryBadge(AYMR.strings.for_you_badge_source, BadgeColorKind.SOURCE)
    DiscoveryRowType.LIKE -> null
}

/** Карточка discovery: постер 2:3 (радиус 16dp) + тайтл + обоснование. Обложка — удалённый URL. */
@Composable
internal fun DiscoveryPosterCard(
    title: String,
    coverUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: DiscoveryBadge? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Portrait)
    val posterShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier.then(
            if (onLongClick != null) {
                Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            } else {
                Modifier.clickable(onClick = onClick)
            },
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(posterShape)
                .background(colors.cardBackground)
                .then(
                    if (colors.isDark || colors.isEInk) {
                        Modifier.border(
                            width = 1.dp,
                            color = if (colors.isDark) {
                                Color.White.copy(
                                    alpha = 0.06f,
                                )
                            } else {
                                Color.Black.copy(alpha = 0.04f)
                            },
                            shape = posterShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            AsyncImage(
                model = coverUrl,
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
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = colors.accent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 3.dp),
            )
        }
    }
}

@Composable
private fun discoveryReasonOrNull(item: HomeHubDiscoveryItem): String? {
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val showReasons by discoveryPreferences.showReasons().collectAsStateWithLifecycle()
    if (!showReasons) return null
    val similarTemplate = stringResource(AYMR.strings.for_you_reason_similar)
    val trendTemplate = stringResource(AYMR.strings.for_you_reason_trending)
    val nextTemplate = stringResource(AYMR.strings.for_you_reason_season_next)
    return discoveryReasonText(item, similarTemplate, trendTemplate, nextTemplate)
}

/** Тизер-секция «Для тебя» на Home Hub: заголовок + «Ещё» + горизонтальный рельс карточек. */
@Composable
internal fun ForYouSection(
    items: List<HomeHubDiscoveryItem>,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    if (items.isEmpty()) {
        EmptyForYouCard(onMoreClick = onMoreClick)
        return
    }
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val sectionHorizontalPadding = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 24.dp
        AuroraDeviceClass.TabletCompact -> 28.dp
        AuroraDeviceClass.TabletExpanded -> 32.dp
    }
    val cardWidth = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 128.dp
        AuroraDeviceClass.TabletCompact -> 152.dp
        AuroraDeviceClass.TabletExpanded -> 176.dp
    }
    val rowSpacing = when (auroraAdaptiveSpec.deviceClass) {
        AuroraDeviceClass.Phone -> 14.dp
        AuroraDeviceClass.TabletCompact -> 16.dp
        AuroraDeviceClass.TabletExpanded -> 18.dp
    }

    Column(modifier = Modifier.padding(top = 32.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(contentMaxWidthDp)
                .padding(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(AYMR.strings.aurora_for_you),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                stringResource(AYMR.strings.aurora_more),
                color = colors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    appHaptics.tap()
                    onMoreClick()
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(contentMaxWidthDp),
            contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(
                items = items,
                key = { it.rowType.key + ":" + it.cleanTitle },
                contentType = { "home_hub_discovery_card" },
            ) { item ->
                DiscoveryPosterCard(
                    modifier = Modifier.width(cardWidth),
                    title = item.title,
                    coverUrl = item.coverUrl,
                    subtitle = discoveryReasonOrNull(item),
                    badge = discoveryBadgeOf(item),
                    onLongClick = onLongClick?.let { { it(item) } },
                    onClick = {
                        appHaptics.tap()
                        onItemClick(item)
                    },
                )
            }
        }
    }
}

/** Полоса из 3 плиток под compact-hero в гибридном режиме (прототип: variant 4). */
@Composable
internal fun HybridDiscoveryStrip(
    items: List<HomeHubDiscoveryItem>,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Wide)
    val tileShape = RoundedCornerShape(16.dp)
    val stripAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val stripMaxWidthDp = stripAdaptiveSpec.updatesMaxWidthDp ?: stripAdaptiveSpec.entryMaxWidthDp

    Column(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(stripMaxWidthDp)
            .padding(top = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(AYMR.strings.aurora_for_you),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(AYMR.strings.for_you_all_picks),
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    appHaptics.tap()
                    onMoreClick()
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { item ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(150.dp)
                        .clip(tileShape)
                        .background(colors.cardBackground)
                        .then(
                            if (colors.isDark || colors.isEInk) {
                                Modifier.border(1.dp, colors.divider, tileShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable {
                            appHaptics.tap()
                            onItemClick(item)
                        },
                ) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = rememberAuroraPosterColorFilter(),
                        modifier = Modifier.fillMaxSize(),
                        error = fallbackPainter,
                        fallback = fallbackPainter,
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    if (colors.isEInk) Color.White.copy(alpha = 0.95f) else Color(0xE004060A),
                                ),
                            ),
                        ),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text(
                            item.title,
                            color = if (colors.isEInk) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp,
                        )
                        discoveryReasonOrNull(item)?.let { reason ->
                            Text(
                                reason,
                                color = colors.accent,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hero «Коллаж»: мозаика 440dp из топ-5 смешанного потока
 * (доминантная плитка + 4 малых), реролл = ротация кэша без сети.
 */
@Composable
internal fun DiscoveryHeroCollage(
    items: List<HomeHubDiscoveryItem>,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    var offset by remember { mutableIntStateOf(0) }
    // Окно до 5 плиток через seeded-shuffle: реролл (offset+1) всегда меняет порядок/состав
    // при любом размере ленты (фикс «мёртвого реролла» и дублей при <5 айтемов).
    val tiles = remember(items, offset) {
        items.shuffled(kotlin.random.Random(offset)).take(5)
    }
    val rest = tiles.drop(1)
    val col1 = listOfNotNull(rest.getOrNull(0), rest.getOrNull(2))
    val col2 = listOfNotNull(rest.getOrNull(1), rest.getOrNull(3))
    val outerShape = RoundedCornerShape(20.dp)
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp

    Box(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(outerShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, outerShape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(Modifier.fillMaxSize().padding(5.dp)) {
            CollageTile(
                item = tiles[0],
                big = true,
                modifier = Modifier.weight(1.55f).fillMaxHeight(),
                onClick = { onItemClick(tiles[0]) },
            )
            if (col1.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    col1.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        CollageTile(
                            item = item,
                            big = false,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onClick = { onItemClick(item) },
                        )
                    }
                }
            }
            if (col2.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    col2.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        CollageTile(
                            item = item,
                            big = false,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onClick = { onItemClick(item) },
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(AYMR.strings.for_you_collage_header),
                color = if (colors.isEInk) colors.textPrimary else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (colors.isEInk) {
                            colors.cardBackground
                        } else if (colors.isDark) {
                            Color.Black.copy(alpha = 0.40f)
                        } else {
                            Color.White.copy(alpha = 0.75f)
                        },
                    )
                    .clickable {
                        appHaptics.tap()
                        offset = offset + 1
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(AYMR.strings.for_you_collage_reroll),
                    tint = if (colors.isDark && !colors.isEInk) Color.White else colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.glass.copy(alpha = if (colors.isDark) 0.22f else 0.85f))
                    .clickable {
                        appHaptics.tap()
                        onMoreClick()
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(AYMR.strings.for_you_all_picks),
                    color = if (colors.isEInk) colors.textPrimary else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CollageTile(
    item: HomeHubDiscoveryItem,
    big: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Wide)
    val tileShape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .clip(tileShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, tileShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
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
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        if (colors.isEInk) Color.White.copy(alpha = 0.95f) else Color(0xCC04060A),
                    ),
                ),
            ),
        )
        if (big) {
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    item.title,
                    color = if (colors.isEInk) Color.Black else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
                discoveryReasonOrNull(item)?.let { reason ->
                    Text(
                        reason,
                        color = colors.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Пустая лента: карточка-вход на полный экран «Для тебя» (там есть рефреш).
 * Без неё при пустом кэше рефреш недостижим (тизер скрывался вместе с точкой входа).
 */
@Composable
private fun EmptyForYouCard(onMoreClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val emptyAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val emptyMaxWidthDp = emptyAdaptiveSpec.updatesMaxWidthDp ?: emptyAdaptiveSpec.entryMaxWidthDp
    Column(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(emptyMaxWidthDp)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (colors.isDark) colors.glass.copy(alpha = 0.10f) else colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(AYMR.strings.for_you_empty_title),
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(AYMR.strings.for_you_empty_subtitle),
            color = colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent)
                .clickable {
                    appHaptics.tap()
                    onMoreClick()
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(AYMR.strings.for_you_all_picks),
                color = colors.textOnAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
