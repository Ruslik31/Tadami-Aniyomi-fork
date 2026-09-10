package eu.kanade.tachiyomi.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.AuroraGlassCtaSurface
import eu.kanade.presentation.entries.components.aurora.AuroraHeroCtaMode
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.tachiyomi.data.discovery.DiscoveryRowItem
import eu.kanade.tachiyomi.data.discovery.interleaveMix
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.ui.discovery.BadgeColorKind
import eu.kanade.tachiyomi.ui.discovery.DiscoveryBadge
import eu.kanade.tachiyomi.ui.discovery.badgeColor
import eu.kanade.tachiyomi.ui.discovery.discoveryCoverData
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    offset: Int = 0,
): List<HomeHubDiscoveryItem> {
    val capped = limit.coerceIn(3, 20)
    val rows = items.groupBy { it.rowType }
        .mapValues { (_, row) ->
            row.map { s ->
                DiscoveryRowItem(s.title, s.cleanTitle, s.coverUrl, s.reason, s.seedTitle, s.provider, s.score)
            }
        }
    val fullMix = interleaveMix(rows, total = items.size)
    val rotated = if (fullMix.size <= capped || offset <= 0) {
        fullMix.take(capped)
    } else {
        val safeOffset = offset % fullMix.size
        (fullMix.drop(safeOffset) + fullMix.take(safeOffset)).take(capped)
    }
    return rotated.mapNotNull { row ->
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

internal fun HomeHubDiscoveryItem.toDiscoverySuggestion(): DiscoverySuggestion = DiscoverySuggestion(
    id = 0L,
    mediaType = mediaType,
    rowType = rowType,
    title = title,
    cleanTitle = cleanTitle,
    coverUrl = coverUrl,
    reason = reasonPayload,
    seedTitle = seedTitle,
    provider = provider,
    score = 1.0,
    position = 0L,
    createdAt = 0L,
)

// ============================ UI ============================

/** Маппинг секции Home Hub в медиатип discovery (для резолва источника обложки). */
internal fun HomeHubSection.toDiscoveryMediaType(): DiscoveryMediaType = when (this) {
    HomeHubSection.Anime -> DiscoveryMediaType.ANIME
    HomeHubSection.Manga -> DiscoveryMediaType.MANGA
    HomeHubSection.Novel -> DiscoveryMediaType.NOVEL
}

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

internal data class HybridDiscoveryStripLayoutSpec(
    val cardWidth: Int,
    val sectionHorizontalPadding: Int,
    val rowSpacing: Int,
)

internal fun resolveHybridDiscoveryStripLayoutSpec(deviceClass: AuroraDeviceClass): HybridDiscoveryStripLayoutSpec {
    return when (deviceClass) {
        AuroraDeviceClass.Phone -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 128,
            sectionHorizontalPadding = 24,
            rowSpacing = 14,
        )
        AuroraDeviceClass.TabletCompact -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 152,
            sectionHorizontalPadding = 28,
            rowSpacing = 16,
        )
        AuroraDeviceClass.TabletExpanded -> HybridDiscoveryStripLayoutSpec(
            cardWidth = 176,
            sectionHorizontalPadding = 32,
            rowSpacing = 18,
        )
    }
}

/** Карточка discovery: гармонизирована с HomeHubRecentPosterCard (постер 0.9, скругление 16dp/18dp, текст под постером). */
@Composable
internal fun DiscoveryPosterCard(
    title: String,
    coverUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deviceClass: AuroraDeviceClass = AuroraDeviceClass.Phone,
    coverMediaType: DiscoveryMediaType? = null,
    coverProvider: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val posterSpec = remember(deviceClass) {
        resolveHomeHubRecentPosterCardSpec(deviceClass)
    }
    val surfaceSpec = remember(colors.isDark) {
        resolveHomeHubRecentPosterSurfaceSpec(colors.isDark)
    }
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(
        variant = AuroraCoverPlaceholderVariant.Portrait,
    )
    val isLightTheme = !colors.isDark && !colors.isEInk
    val outerSurface = if (colors.isDark) {
        colors.glass.copy(alpha = surfaceSpec.containerAlpha)
    } else if (colors.isEInk) {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    } else {
        Color.Transparent
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(posterSpec.posterAspectRatio)
                    .clip(posterShape)
                    .background(posterSurface)
                    .then(
                        if (colors.isDark || colors.isEInk) {
                            Modifier.border(
                                width = 1.dp,
                                color = if (colors.isDark) {
                                    Color.White.copy(alpha = 0.06f)
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
                val posterContext = LocalContext.current
                val posterCoverReloadTick = rememberCoverReloadTick()
                val posterCoverRequest = remember(
                    posterContext,
                    coverUrl,
                    coverMediaType,
                    coverProvider,
                    posterCoverReloadTick,
                ) {
                    buildAuroraCoverImageRequest(
                        posterContext,
                        discoveryCoverData(coverMediaType, coverProvider, coverUrl),
                    )
                }
                AsyncImage(
                    model = posterCoverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = rememberAuroraPosterColorFilter(),
                    modifier = Modifier.fillMaxSize(),
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                )
            }
            Spacer(Modifier.height(posterSpec.textTopSpacingDp.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = posterSpec.textBlockMinHeightDp.dp)
                    .padding(horizontal = posterSpec.textHorizontalPaddingDp.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = posterSpec.titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = {
                appHaptics.tap()
                onClick()
            },
            onLongClick = {
                appHaptics.tap()
                onLongClick()
            },
        )
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = {
                appHaptics.tap()
                onClick()
            },
        )
    }

    if (isLightTheme) {
        Box(
            modifier = modifier
                .drawBehind {
                    val radius = 18.dp.toPx()
                    val cornerRadius = CornerRadius(radius, radius)
                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()
                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.035f),
                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                    drawRoundRect(
                        color = Color(0xFF6B4E28).copy(alpha = 0.04f),
                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                        size = Size(width = size.width - warmInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.68f),
                            Color.White.copy(alpha = 0.60f),
                        ),
                    ),
                    shape = cardShape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                    shape = cardShape,
                )
                .clip(cardShape)
                .then(clickModifier),
        ) {
            cardContent()
        }
    } else {
        Box(
            modifier = modifier
                .clip(cardShape)
                .background(outerSurface)
                .then(
                    if (colors.isDark || colors.isEInk) {
                        Modifier.border(
                            width = 1.dp,
                            color = if (colors.isDark) {
                                Color.White.copy(alpha = 0.06f)
                            } else {
                                Color.Black.copy(alpha = 0.05f)
                            },
                            shape = cardShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(clickModifier),
        ) {
            cardContent()
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
    return discoveryReasonText(item, similarTemplate, trendTemplate, nextTemplate).orEmpty()
}

/** Тизер-секция «Для тебя» на Home Hub: заголовок + «Ещё» + горизонтальный рельс карточек. */
@Composable
internal fun ForYouSection(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
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
                    deviceClass = auroraAdaptiveSpec.deviceClass,
                    coverMediaType = coverMediaType,
                    coverProvider = item.provider,
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

/** Полоса из 3 плиток под compact-hero в гибридном режиме (гармонизирована с HomeHubRecentPosterCard). */
@Composable
internal fun HybridDiscoveryStrip(
    items: List<HomeHubDiscoveryItem>,
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val stripAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val stripMaxWidthDp = stripAdaptiveSpec.updatesMaxWidthDp ?: stripAdaptiveSpec.entryMaxWidthDp
    val layoutSpec = remember(stripAdaptiveSpec.deviceClass) {
        resolveHybridDiscoveryStripLayoutSpec(stripAdaptiveSpec.deviceClass)
    }
    val cardWidth = layoutSpec.cardWidth.dp
    val sectionHorizontalPadding = layoutSpec.sectionHorizontalPadding.dp
    val rowSpacing = layoutSpec.rowSpacing.dp
    val posterSpec = remember(stripAdaptiveSpec.deviceClass) {
        resolveHomeHubRecentPosterCardSpec(stripAdaptiveSpec.deviceClass)
    }
    val surfaceSpec = remember(colors.isDark) {
        resolveHomeHubRecentPosterSurfaceSpec(colors.isDark)
    }
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)
    val isLightTheme = !colors.isDark && !colors.isEInk
    val outerSurface = if (colors.isDark) {
        colors.glass.copy(alpha = surfaceSpec.containerAlpha)
    } else if (colors.isEInk) {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    } else {
        Color.Transparent
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(stripMaxWidthDp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(AYMR.strings.aurora_for_you),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (onRefreshClick != null) {
                    Spacer(Modifier.width(10.dp))
                    val rotationAnim = rememberInfiniteTransition(label = "hybrid_refresh_rot")
                    val rotationAngle by if (isRefreshing) {
                        rotationAnim.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "refresh_angle",
                        )
                    } else {
                        remember { mutableFloatStateOf(0f) }
                    }

                    val refreshRimBrush = remember(colors) {
                        if (colors.isEInk) {
                            SolidColor(colors.divider)
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    if (colors.isDark) {
                                        Color.White.copy(
                                            alpha = 0.18f,
                                        )
                                    } else {
                                        Color.White.copy(alpha = 0.50f)
                                    },
                                    Color.Transparent,
                                ),
                            )
                        }
                    }
                    val refreshTintBrush = remember(colors) {
                        if (colors.isEInk) {
                            SolidColor(Color.Transparent)
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    if (colors.isDark) {
                                        Color.White.copy(
                                            alpha = 0.08f,
                                        )
                                    } else {
                                        Color.White.copy(alpha = 0.20f)
                                    },
                                    Color.Transparent,
                                ),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(
                                1.dp,
                                refreshRimBrush,
                                CircleShape,
                            )
                            .background(
                                brush = refreshTintBrush,
                                shape = CircleShape,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 16.dp),
                                onClick = {
                                    appHaptics.tap()
                                    onRefreshClick()
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(AYMR.strings.for_you_refresh),
                            tint = if (colors.isDark && !colors.isEInk) colors.accent else colors.textPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = rotationAngle },
                        )
                    }
                }
            }
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
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(
                items = items,
                key = { it.rowType.key + ":" + it.cleanTitle },
                contentType = { "hybrid_discovery_tile" },
            ) { item ->
                DiscoveryPosterCard(
                    modifier = Modifier.width(cardWidth),
                    title = item.title,
                    coverUrl = item.coverUrl,
                    subtitle = discoveryReasonOrNull(item),
                    deviceClass = stripAdaptiveSpec.deviceClass,
                    coverMediaType = coverMediaType,
                    coverProvider = item.provider,
                    onClick = {
                        appHaptics.tap()
                        onItemClick(item)
                    },
                    onLongClick = onLongClick?.let { { it(item) } },
                )
            }
            if (items.isNotEmpty()) {
                item(key = "hybrid_discovery_more", contentType = "hybrid_discovery_more") {
                    val moreContent: @Composable () -> Unit = {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(posterSpec.posterAspectRatio)
                                    .clip(posterShape)
                                    .background(posterSurface)
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                colors.accent.copy(alpha = 0.40f),
                                                Color.Transparent,
                                            ),
                                        ),
                                        posterShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.ArrowForward,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(AYMR.strings.for_you_all_picks),
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(posterSpec.textTopSpacingDp.dp))
                            Spacer(Modifier.height(posterSpec.textBlockMinHeightDp.dp))
                        }
                    }

                    if (isLightTheme) {
                        Box(
                            modifier = Modifier
                                .width(cardWidth)
                                .drawBehind {
                                    val radius = 18.dp.toPx()
                                    val cornerRadius = CornerRadius(radius, radius)
                                    val neutralOffsetY = 3.dp.toPx()
                                    val warmOffsetY = 5.dp.toPx()
                                    val neutralInset = 1.dp.toPx()
                                    val warmInset = 3.dp.toPx()

                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = 0.035f),
                                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                                        cornerRadius = cornerRadius,
                                    )
                                    drawRoundRect(
                                        color = Color(0xFF6B4E28).copy(alpha = 0.04f),
                                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                                        size = Size(width = size.width - warmInset * 2, height = size.height),
                                        cornerRadius = cornerRadius,
                                    )
                                }
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.78f),
                                            Color.White.copy(alpha = 0.68f),
                                            Color.White.copy(alpha = 0.60f),
                                        ),
                                    ),
                                    shape = cardShape,
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.75f),
                                            Color.White.copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.12f),
                                        ),
                                    ),
                                    shape = cardShape,
                                )
                                .clip(cardShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        appHaptics.tap()
                                        onMoreClick()
                                    },
                                ),
                        ) {
                            moreContent()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .width(cardWidth)
                                .clip(cardShape)
                                .background(outerSurface)
                                .then(
                                    if (colors.isDark || colors.isEInk) {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = if (colors.isDark) {
                                                Color.White.copy(alpha = 0.06f)
                                            } else {
                                                Color.Black.copy(alpha = 0.05f)
                                            },
                                            shape = cardShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        appHaptics.tap()
                                        onMoreClick()
                                    },
                                ),
                        ) {
                            moreContent()
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
    coverMediaType: DiscoveryMediaType,
    onMoreClick: () -> Unit,
    onItemClick: (HomeHubDiscoveryItem) -> Unit,
    onLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val discoveryPreferences = remember { Injekt.get<DiscoveryPreferences>() }
    val intervalHours by discoveryPreferences.collageRotationIntervalHours().collectAsStateWithLifecycle()
    val animSpeed by discoveryPreferences.collageAnimationSpeed().collectAsStateWithLifecycle()
    var offset by remember { mutableIntStateOf(0) }
    var userInteractionToken by remember { mutableIntStateOf(0) }

    // Авто-ротация с настраиваемым интервалом (от 1 до 24 ч, 0 = отключено)
    if (!colors.isEInk && items.size > 5 && intervalHours > 0) {
        LaunchedEffect(items.size, intervalHours, userInteractionToken) {
            val intervalMillis = intervalHours * 3600_000L
            while (isActive) {
                val now = System.currentTimeMillis()
                val lastTime = discoveryPreferences.collageLastRotationTime().get()
                val elapsed = now - lastTime
                if (lastTime == 0L) {
                    discoveryPreferences.collageLastRotationTime().set(now)
                    delay(intervalMillis)
                } else if (elapsed >= intervalMillis) {
                    offset += 1
                    discoveryPreferences.collageLastRotationTime().set(now)
                    delay(intervalMillis)
                } else {
                    val remaining = maxOf(1000L, intervalMillis - elapsed)
                    delay(remaining)
                    offset += 1
                    discoveryPreferences.collageLastRotationTime().set(System.currentTimeMillis())
                }
            }
        }
    }

    val staggerStep = when (animSpeed) {
        "fast" -> 40
        "smooth" -> 110
        else -> 70
    }

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
            AnimatedContent(
                targetState = tiles[0],
                transitionSpec = {
                    resolveCollageSlotTransition(
                        delayMillis = 0,
                        isEInk = colors.isEInk,
                        speed = animSpeed,
                    )
                },
                modifier = Modifier.weight(1.55f).fillMaxHeight(),
                label = "collage_hero_slot",
            ) { targetHero ->
                CollageTile(
                    item = targetHero,
                    big = true,
                    modifier = Modifier.fillMaxSize(),
                    coverMediaType = coverMediaType,
                    coverProvider = targetHero.provider,
                    onClick = { onItemClick(targetHero) },
                    onLongClick = onLongClick?.let { { it(targetHero) } },
                )
            }
            if (col1.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    col1.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        AnimatedContent(
                            targetState = item,
                            transitionSpec = {
                                resolveCollageSlotTransition(
                                    delayMillis = staggerStep + index * staggerStep,
                                    isEInk = colors.isEInk,
                                    speed = animSpeed,
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = "collage_col1_$index",
                        ) { targetItem ->
                            CollageTile(
                                item = targetItem,
                                big = false,
                                modifier = Modifier.fillMaxSize(),
                                coverMediaType = coverMediaType,
                                coverProvider = targetItem.provider,
                                onClick = { onItemClick(targetItem) },
                                onLongClick = onLongClick?.let { { it(targetItem) } },
                            )
                        }
                    }
                }
            }
            if (col2.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    col2.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        AnimatedContent(
                            targetState = item,
                            transitionSpec = {
                                resolveCollageSlotTransition(
                                    delayMillis = (staggerStep * 3) + index * staggerStep,
                                    isEInk = colors.isEInk,
                                    speed = animSpeed,
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = "collage_col2_$index",
                        ) { targetItem ->
                            CollageTile(
                                item = targetItem,
                                big = false,
                                modifier = Modifier.fillMaxSize(),
                                coverMediaType = coverMediaType,
                                coverProvider = targetItem.provider,
                                onClick = { onItemClick(targetItem) },
                                onLongClick = onLongClick?.let { { it(targetItem) } },
                            )
                        }
                    }
                }
            }
        }

        // Кнопка обновления в правом верхнем углу: высокий контраст на любых фонах
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (colors.isEInk) {
                        colors.cardBackground
                    } else if (colors.isDark) {
                        Color.Black.copy(alpha = 0.75f)
                    } else {
                        Color.White.copy(alpha = 0.90f)
                    },
                )
                .border(
                    BorderStroke(
                        width = 1.dp,
                        color = if (colors.isEInk) colors.divider else Color.White.copy(alpha = 0.22f),
                    ),
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp),
                    onClick = {
                        appHaptics.tap()
                        discoveryPreferences.collageLastRotationTime().set(System.currentTimeMillis())
                        userInteractionToken++
                        offset = offset + 1
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(AYMR.strings.for_you_collage_reroll),
                tint = if (colors.isDark && !colors.isEInk) colors.accent else colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        // Кнопка перехода в стиле Aurora Hero CTA («Продолжить / Читать»)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            val buttonInteractionSource = remember { MutableInteractionSource() }
            AuroraGlassCtaSurface(
                mode = AuroraHeroCtaMode.Aurora,
                onClick = {
                    appHaptics.tap()
                    onMoreClick()
                },
                modifier = Modifier.height(44.dp),
                isHome = true,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                interactionSource = buttonInteractionSource,
            ) { contentColor ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_all_picks),
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

internal fun resolveCollageSlotTransition(
    delayMillis: Int,
    isEInk: Boolean,
    speed: String = "normal",
): ContentTransform {
    return if (isEInk) {
        fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
    } else {
        val (enterDuration, exitDuration) = when (speed) {
            "fast" -> 250 to 200
            "smooth" -> 700 to 500
            else -> 420 to 300
        }
        val enter = fadeIn(animationSpec = tween(durationMillis = enterDuration, delayMillis = delayMillis)) +
            scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(durationMillis = enterDuration, delayMillis = delayMillis),
            )
        val exit = fadeOut(animationSpec = tween(durationMillis = exitDuration)) +
            scaleOut(targetScale = 1.02f, animationSpec = tween(durationMillis = exitDuration))
        enter togetherWith exit
    }
}

@Composable
private fun CollageTile(
    item: HomeHubDiscoveryItem,
    big: Boolean,
    modifier: Modifier = Modifier,
    coverMediaType: DiscoveryMediaType? = null,
    coverProvider: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val appHaptics = LocalAppHaptics.current
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, item.coverUrl, coverMediaType, coverProvider, coverReloadTick) {
        buildAuroraCoverImageRequest(context, discoveryCoverData(coverMediaType, coverProvider, item.coverUrl))
    }
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
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = {
                            appHaptics.tap()
                            onClick()
                        },
                        onLongClick = {
                            appHaptics.tap()
                            onLongClick()
                        },
                    )
                } else {
                    Modifier.clickable {
                        appHaptics.tap()
                        onClick()
                    }
                },
            ),
    ) {
        AsyncImage(
            model = coverRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = rememberAuroraPosterColorFilter(),
            modifier = Modifier.fillMaxSize(),
            error = fallbackPainter,
            fallback = fallbackPainter,
        )
        // Скрим только на большой плитке и только в нижней трети под заголовком:
        // постеры остаются яркими, как в обычных карточках, а малые плитки без
        // текста не затемняются вовсе.
        if (big) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1.0f to if (colors.isEInk) Color.White.copy(alpha = 0.95f) else Color(0xCC04060A),
                    ),
                ),
            )
        }
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
