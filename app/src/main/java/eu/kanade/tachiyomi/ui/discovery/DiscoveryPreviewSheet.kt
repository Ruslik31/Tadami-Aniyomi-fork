package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.discovery.DiscoveryMeta
import eu.kanade.tachiyomi.ui.home.discoveryReasonText
import eu.kanade.tachiyomi.ui.home.toHomeHubDiscoveryItem
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

internal enum class BadgeColorKind { TASTE, FRESH, SOURCE }

internal data class DiscoveryBadge(val textRes: StringResource, val colorKind: BadgeColorKind)

/** Микро-бейдж сигнала для обложки: solid-пилюля по типу сигнала (прототип P3, V1). */
internal fun discoveryBadge(item: DiscoverySuggestion): DiscoveryBadge? = when (item.rowType) {
    DiscoveryRowType.TASTE -> DiscoveryBadge(AYMR.strings.for_you_badge_taste, BadgeColorKind.TASTE)
    DiscoveryRowType.TREND ->
        DiscoveryBadge(
            if (item.reason == "next") AYMR.strings.for_you_badge_season else AYMR.strings.for_you_badge_trend,
            BadgeColorKind.FRESH,
        )
    DiscoveryRowType.SOURCE -> DiscoveryBadge(AYMR.strings.for_you_badge_source, BadgeColorKind.SOURCE)
    DiscoveryRowType.LIKE -> null
}

@Composable
internal fun badgeColor(kind: BadgeColorKind): androidx.compose.ui.graphics.Color {
    val colors = AuroraTheme.colors
    return when (kind) {
        BadgeColorKind.TASTE -> colors.gradientPurple
        BadgeColorKind.FRESH -> colors.progressCyan
        BadgeColorKind.SOURCE -> colors.accent
    }
}

/**
 * Aurora Preview Bottom Sheet (прототип P3, V1): матовый лист с обложкой в ореоли,
 * бейджем совпадения, жанрами, синопсисом (лениво из AniList) и действиями.
 */
@Composable
internal fun DiscoveryPreviewSheet(
    item: DiscoverySuggestion,
    meta: DiscoveryMeta?,
    isMetaLoading: Boolean,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onFind: () -> Unit,
    onHide: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val appHaptics = LocalAppHaptics.current
    val coverReloadTick = rememberCoverReloadTick()
    val coverRequest = remember(context, item.coverUrl, coverReloadTick) {
        buildAuroraCoverImageRequest(context, item.coverUrl)
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant = AuroraCoverPlaceholderVariant.Portrait)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val homeItem = remember(item) { item.toHomeHubDiscoveryItem() }
    val similarTemplate = stringResource(AYMR.strings.for_you_reason_similar)
    val trendTemplate = stringResource(AYMR.strings.for_you_reason_trending)
    val nextTemplate = stringResource(AYMR.strings.for_you_reason_season_next)
    val reason = discoveryReasonText(homeItem, similarTemplate, trendTemplate, nextTemplate)
    val badge = discoveryBadge(item)
    val genres = meta?.genres ?: if (item.rowType == DiscoveryRowType.TASTE) {
        item.reason?.split(", ")?.filter { it.isNotBlank() }.orEmpty()
    } else {
        emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Обложка с радиальным ореолом акцента
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(190.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(colors.accent.copy(alpha = 0.35f), colors.accent.copy(alpha = 0f)),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .width(150.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.cardBackground)
                        .border(1.dp, colors.divider, RoundedCornerShape(18.dp)),
                ) {
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = rememberAuroraPosterColorFilter(),
                        modifier = Modifier.matchParentSize(),
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
            }
            Spacer(Modifier.height(14.dp))
            Text(
                item.title,
                color = colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            meta?.altTitle?.let { alt ->
                Text(
                    alt,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (reason != null) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent.copy(alpha = 0.16f))
                        .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(reason, color = colors.accent, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (genres.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    genres.take(4).forEach { genre ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.cardBackground)
                                .border(1.dp, colors.divider, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(genre, color = colors.textSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            when {
                isMetaLoading -> Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_meta_loading),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                meta?.description != null -> Text(
                    meta.description,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accent)
                        .clickable {
                            appHaptics.tap()
                            onAdd()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = colors.textOnAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(AYMR.strings.for_you_add_library),
                            color = colors.textOnAccent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.cardBackground)
                        .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                        .clickable {
                            appHaptics.tap()
                            onFind()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(AYMR.strings.for_you_sheet_find),
                            color = colors.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        appHaptics.tap()
                        onHide()
                    },
                ) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        stringResource(AYMR.strings.for_you_hide),
                        tint = colors.textSecondary,
                    )
                }
            }
        }
    }
}
