package eu.kanade.presentation.library.novel.quotes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteCardModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteCardStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import kotlinx.coroutines.launch
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * «Поделиться карточкой» bottom sheet: live-превью карточки 4:5, сегменты трёх стилей
 * и кнопка шаринга. Превью и финальный bitmap рендерит ОДИН graphics layer: контент
 * записывается ровно в 1080×1350px, для показа он лишь масштабируется.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelQuoteCardShareSheet(
    item: NovelHighlightWithChapter,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AuroraTheme.colors
    val preferences = remember { Injekt.get<NovelReaderPreferences>() }
    var style by remember(item.highlight.id) { mutableStateOf(preferences.quoteCardStyle().get()) }
    var sharing by remember { mutableStateOf(false) }
    val model = remember(item, style) { NovelQuoteCardModel.fromHighlight(item, style) }
    val sharer = remember(context) { NovelQuoteCardSharerImpl(context) }

    val graphicsLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val cardWidth = with(density) { NovelQuoteCardModel.CARD_WIDTH_PX.toDp() }
    val cardHeight = with(density) { NovelQuoteCardModel.CARD_HEIGHT_PX.toDp() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(AYMR.strings.novel_quotes_share_as_image),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                letterSpacing = 0.4.sp,
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val scale = (maxWidth * 0.66f) / cardWidth
                Box(
                    modifier = Modifier
                        .size(cardWidth * scale, cardHeight * scale)
                        .clip(RoundedCornerShape(6.dp))
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                            .drawWithContent {
                                graphicsLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                drawLayer(graphicsLayer)
                            }
                            // requiredSize: входящие constraints превью-бокса меньше карточки,
                            // а layer обязан писать ровно 1080×1350px.
                            .requiredSize(cardWidth, cardHeight),
                    ) {
                        // Фиксированная плотность: карточка детерминированно 1080×1350px
                        // на любом устройстве и не зависит от пользовательского fontScale.
                        CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 1f)) {
                            NovelQuoteCard(model = model, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (colors.isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.06f))
                    .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                NovelQuoteCardStyle.entries.forEach { entry ->
                    val selected = entry == style
                    val label = when (entry) {
                        NovelQuoteCardStyle.CODEX_SACRA ->
                            stringResource(AYMR.strings.novel_quotes_card_style_codex)
                        NovelQuoteCardStyle.AURORA_GLASS ->
                            stringResource(AYMR.strings.novel_quotes_card_style_glass)
                        NovelQuoteCardStyle.MINIMAL ->
                            stringResource(AYMR.strings.novel_quotes_card_style_minimal)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                brush = if (selected) {
                                    Brush.verticalGradient(listOf(colors.accent, colors.accentVariant))
                                } else {
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                style = entry
                                preferences.quoteCardStyle().set(entry)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) colors.textOnAccent else colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(listOf(colors.accent, colors.accentVariant)),
                        RoundedCornerShape(16.dp),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable(enabled = !sharing) {
                        scope.launch {
                            sharing = true
                            val shared = shareQuoteCardAsImage(sharer, item, style) {
                                runCatching {
                                    withFrameNanos { }
                                    graphicsLayer.toImageBitmap()
                                }.getOrNull()
                            }
                            sharing = false
                            if (shared) onDismiss()
                        }
                    }
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = colors.textOnAccent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(MR.strings.action_share),
                    color = colors.textOnAccent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
