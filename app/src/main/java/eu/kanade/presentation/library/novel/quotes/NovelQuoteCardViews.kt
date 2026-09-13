package eu.kanade.presentation.library.novel.quotes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteCardModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteCardStyle
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The three 4:5 quote share card designs from the approved prototype
 * (`docs/prototypes/prototype_quote_share_card.html`): A «Codex Sacra» parchment,
 * B «Aurora Glass», C typography-minimal. Cards fill the size given by the caller;
 * the offscreen capture pins it to exactly 1080×1350px.
 */
@Composable
fun NovelQuoteCard(
    model: NovelQuoteCardModel,
    modifier: Modifier = Modifier,
) {
    when (model.style) {
        NovelQuoteCardStyle.CODEX_SACRA -> CodexSacraQuoteCard(model, isSystemInDarkTheme(), modifier)
        NovelQuoteCardStyle.AURORA_GLASS -> AuroraGlassQuoteCard(model, AuroraTheme.colors.isDark, modifier)
        NovelQuoteCardStyle.MINIMAL -> MinimalQuoteCard(model, AuroraTheme.colors.isDark, modifier)
    }
}

/** Real-measurement autofit: largest base→19sp half-step size that keeps ≤12 lines. */
@Composable
private fun autofitQuoteFontSp(
    text: String,
    style: NovelQuoteCardStyle,
    fontStyle: FontStyle,
    lineHeightRatio: Float,
    maxWidthPx: Int,
): Float {
    val textMeasurer = rememberTextMeasurer()
    return remember(text, style, fontStyle, lineHeightRatio, maxWidthPx, textMeasurer) {
        var size = NovelQuoteCardModel.baseFontSp(style)
        while (size > NovelQuoteCardModel.MIN_QUOTE_FONT_SP) {
            val measured = textMeasurer.measure(
                text = text,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontStyle = fontStyle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = size.sp,
                    lineHeight = (size * lineHeightRatio).sp,
                ),
                constraints = Constraints(maxWidth = maxWidthPx),
            )
            if (measured.lineCount <= NovelQuoteCardModel.MAX_QUOTE_LINES) break
            size -= NovelQuoteCardModel.QUOTE_FONT_STEP_SP
        }
        size.coerceAtLeast(NovelQuoteCardModel.MIN_QUOTE_FONT_SP)
    }
}

@Composable
private fun NoteText(
    note: String,
    color: Color,
    arrowColor: Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, color = arrowColor)) {
                append("↳ ")
            }
            append(note)
        },
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.4f).sp,
        color = color,
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun TadamiWatermark(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "TADAMI",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 4.sp,
            color = tint,
        )
    }
}

/** Вариант A «Codex Sacra»: пергамент, двойная рамка, виньетка, ❦. */
@Composable
private fun CodexSacraQuoteCard(
    model: NovelQuoteCardModel,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val parchment = if (isDark) Color(0xFF2B2419) else Color(0xFFF1E4C6)
    val ink = if (isDark) Color(0xFFE8DCC0) else Color(0xFF3B2F23)
    val faded = if (isDark) Color(0xFFB8A98A) else Color(0xFF7A6A50)
    val line = ink.copy(alpha = 0.25f)
    val vignette = if (isDark) Color.Black.copy(alpha = 0.42f) else Color(0xFF604826).copy(alpha = 0.20f)
    val spot = if (isDark) Color.Black.copy(alpha = 0.20f) else Color(0xFF7A6A50).copy(alpha = 0.11f)
    val tight = model.needsAutofit
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(parchment)
            .border(1.dp, line, RoundedCornerShape(4.dp)),
    ) {
        // Износ пергамента: лёгкая виньетка + четыре пятна.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val vignetteBrush = Brush.radialGradient(
                        0.52f to Color.Transparent,
                        1f to vignette,
                        center = Offset(w * 0.5f, h * 0.42f),
                        radius = w * 1.3f,
                    )
                    val spotBrushes = listOf(
                        Brush.radialGradient(
                            0f to spot,
                            0.42f to spot,
                            1f to Color.Transparent,
                            center = Offset(w * 0.16f, h * 0.10f),
                            radius = w * 0.38f,
                        ),
                        Brush.radialGradient(
                            0f to spot,
                            0.38f to spot,
                            1f to Color.Transparent,
                            center = Offset(w * 0.86f, h * 0.74f),
                            radius = w * 0.34f,
                        ),
                        Brush.radialGradient(
                            0f to spot,
                            0.33f to spot,
                            1f to Color.Transparent,
                            center = Offset(w * 0.70f, h * 0.18f),
                            radius = w * 0.24f,
                        ),
                        Brush.radialGradient(
                            0f to spot,
                            0.36f to spot,
                            1f to Color.Transparent,
                            center = Offset(w * 0.24f, h * 0.88f),
                            radius = w * 0.28f,
                        ),
                    )
                    onDrawBehind {
                        drawRect(vignetteBrush)
                        spotBrushes.forEach { drawRect(it) }
                    }
                },
        )

        val quoteFontSp = autofitQuoteFontSp(
            text = model.text,
            style = NovelQuoteCardStyle.CODEX_SACRA,
            fontStyle = FontStyle.Italic,
            lineHeightRatio = if (tight) 1.3f else 1.42f,
            maxWidthPx = constraints.maxWidth -
                with(density) { (if (tight) 40.dp else 56.dp).roundToPx() },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (tight) 20.dp else 28.dp,
                    vertical = if (tight) 16.dp else 26.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                if (tight) 5.dp else 9.dp,
                Alignment.CenterVertically,
            ),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(model.colorArgb), CircleShape),
            )
            Text(
                text = "«${model.text}»",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                fontSize = quoteFontSp.sp,
                lineHeight = (quoteFontSp * if (tight) 1.3f else 1.42f).sp,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = NovelQuoteCardModel.MAX_QUOTE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            model.note?.let { note ->
                NoteText(note = note, color = faded, arrowColor = faded, fontSizeSp = 13f)
            }
            model.chapterName?.let { chapter ->
                Text(
                    text = "— $chapter",
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                    letterSpacing = 0.4.sp,
                    color = faded,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = model.novelTitle.uppercase(),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                lineHeight = 18.sp,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "❦", fontFamily = FontFamily.Serif, fontSize = 16.sp, color = faded)
            TadamiWatermark(tint = faded)
        }

        // Внутренняя нить двойной рамки.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(7.dp)
                .border(1.dp, line),
        )
    }
}

/** Вариант B «Aurora Glass»: чернильный градиент, свечение, стекло, ❖-divider. */
@Composable
private fun AuroraGlassQuoteCard(
    model: NovelQuoteCardModel,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val accent = colors.accent
    val bg1 = if (isDark) Color(0xFF12151D) else Color(0xFFEDF2FA)
    val bg2 = if (isDark) Color(0xFF0F1116) else Color(0xFFDDE7F4)
    val glow = accent.copy(alpha = if (isDark) 0.18f else 0.14f)
    val glass = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.58f)
    val quoteColor = if (isDark) Color(0xFFEFE9E0) else Color(0xFF1B2432)
    val sec = if (isDark) Color(0xFF8B96A8) else Color(0xFF5C6B80)
    val bookColor = if (isDark) Color(0xFFF4F1EA) else Color(0xFF10151F)
    val inkBlot1 = (if (isDark) Color(0xFF94BEFF) else Color(0xFF0064C8))
        .copy(alpha = if (isDark) 0.055f else 0.05f)
    val inkBlot2 = accent.copy(alpha = if (isDark) 0.05f else 0.06f)
    val borderStart = Color.White.copy(alpha = if (isDark) 0.40f else 0.90f)
    val borderMid = accent.copy(alpha = if (isDark) 0.30f else 0.35f)
    val tight = model.needsAutofit
    val highlight = Color(model.colorArgb)

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp)),
    ) {
        val cardWidthPx = constraints.maxWidth
        // Фон: линейный градиент + два radial-свечения + статичные чернильные разводы.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val bgBrush = Brush.linearGradient(
                        listOf(bg1, bg2),
                        start = Offset(w * 0.6f, 0f),
                        end = Offset(w * 0.4f, h),
                    )
                    val glowTop = Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(w * 0.5f, -h * 0.08f),
                        radius = h * 0.73f,
                    )
                    val glowBottom = Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(w * 0.88f, h),
                        radius = w * 0.75f,
                    )
                    val blots = listOf(
                        Triple(Offset(w * 0.22f, h * 0.26f), w * 0.34f, inkBlot1),
                        Triple(Offset(w * 0.74f, h * 0.44f), w * 0.40f, inkBlot2),
                        Triple(Offset(w * 0.34f, h * 0.72f), w * 0.26f, inkBlot1),
                        Triple(Offset(w * 0.66f, h * 0.88f), w * 0.46f, inkBlot2),
                    )
                    onDrawBehind {
                        drawRect(bgBrush)
                        drawRect(glowTop)
                        drawRect(glowBottom)
                        scale(1.15f, 1.15f) {
                            rotate(-4f) {
                                blots.forEach { (center, radius, color) ->
                                    drawRect(
                                        Brush.radialGradient(
                                            listOf(color, Color.Transparent),
                                            center = center,
                                            radius = radius,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (tight) 12.dp else 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(glass)
                // Градиентная рамка White α.40 → accent α.30 → transparent (паттерн Exlibris).
                .drawWithCache {
                    val stroke = Stroke(width = 1.dp.toPx())
                    val corner = CornerRadius(20.dp.toPx())
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f,
                                top = 0f,
                                right = size.width,
                                bottom = size.height,
                                radiusX = corner.x,
                                radiusY = corner.y,
                            ),
                        )
                    }
                    val borderBrush = Brush.linearGradient(
                        0f to borderStart,
                        0.48f to borderMid,
                        0.85f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind {
                        drawPath(path = path, brush = borderBrush, style = stroke)
                    }
                },
        ) {
            val quoteFontSp = autofitQuoteFontSp(
                text = model.text,
                style = NovelQuoteCardStyle.AURORA_GLASS,
                fontStyle = FontStyle.Italic,
                lineHeightRatio = if (tight) 1.3f else 1.44f,
                // glass inset + column padding + stripe block (7dp) and its gap (12dp)
                maxWidthPx = cardWidthPx - with(density) {
                    ((if (tight) 48.dp else 76.dp) + 19.dp).roundToPx()
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (tight) 12.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (tight) 4.dp else 12.dp,
                    Alignment.CenterVertically,
                ),
            ) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Вертикальная полоса цвета хайлайта со свечением.
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(highlight.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(3.dp)
                                .background(highlight, RoundedCornerShape(3.dp)),
                        )
                    }
                    Text(
                        text = "«${model.text}»",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = quoteFontSp.sp,
                        lineHeight = (quoteFontSp * if (tight) 1.3f else 1.44f).sp,
                        color = quoteColor,
                        maxLines = NovelQuoteCardModel.MAX_QUOTE_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                model.note?.let { note ->
                    NoteText(
                        note = note,
                        color = accent.copy(alpha = 0.85f),
                        arrowColor = accent,
                        fontSizeSp = 12.5f,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 15.dp),
                    )
                }
                val provenance = buildList {
                    model.chapterName?.let { add(it) }
                    if (model.pageLabel != null) {
                        add(
                            stringResource(
                                AYMR.strings.novel_highlight_page_label,
                                model.pageIndex,
                                model.pageCount,
                            ),
                        )
                    }
                }
                if (provenance.isNotEmpty()) {
                    Text(
                        text = provenance.joinToString(" · "),
                        fontSize = 11.sp,
                        letterSpacing = 0.2.sp,
                        color = sec,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 15.dp),
                    )
                }
                // ❖-divider: линия — ромб — линия.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, accent.copy(alpha = 0.4f), Color.Transparent),
                                ),
                            ),
                    )
                    Text(text = "❖", color = accent.copy(alpha = 0.85f), fontSize = 10.sp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, accent.copy(alpha = 0.4f), Color.Transparent),
                                ),
                            ),
                    )
                }
                Text(
                    text = model.novelTitle.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.4.sp,
                    lineHeight = 16.sp,
                    color = bookColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TadamiWatermark(
                    tint = accent,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** Вариант C «Типографика-минимал»: огромный serif, полупрозрачные «лапки», линия цвета хайлайта. */
@Composable
private fun MinimalQuoteCard(
    model: NovelQuoteCardModel,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (isDark) Color(0xFF0A0C12) else Color(0xFFFAF7F2)
    val textColor = if (isDark) Color(0xFFF2F5FA) else Color(0xFF171A20)
    val sec = if (isDark) Color(0xFF94A3B8) else Color(0xFF6E7480)
    val accent = AuroraTheme.colors.accent
    val mark = accent.copy(alpha = if (isDark) 0.13f else 0.11f)
    val tight = model.needsAutofit
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(bg),
    ) {
        Text(
            text = "«",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 190.sp,
            lineHeight = 190.sp,
            color = mark,
            modifier = Modifier.offset(x = 6.dp, y = (-34).dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 30.dp)
                .width(48.dp)
                .height(3.dp)
                .background(Color(model.colorArgb), RoundedCornerShape(2.dp)),
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val quoteFontSp = autofitQuoteFontSp(
                text = model.text,
                style = NovelQuoteCardStyle.MINIMAL,
                fontStyle = FontStyle.Normal,
                lineHeightRatio = if (tight) 1.3f else 1.34f,
                maxWidthPx = constraints.maxWidth -
                    with(density) { (if (tight) 44.dp else 52.dp).roundToPx() },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (tight) 22.dp else 26.dp,
                        end = if (tight) 22.dp else 26.dp,
                        top = if (tight) 44.dp else 56.dp,
                        bottom = if (tight) 58.dp else 64.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    if (tight) 9.dp else 14.dp,
                    Alignment.CenterVertically,
                ),
            ) {
                Text(
                    text = model.text,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = quoteFontSp.sp,
                    lineHeight = (quoteFontSp * if (tight) 1.3f else 1.34f).sp,
                    letterSpacing = 0.2.sp,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = NovelQuoteCardModel.MAX_QUOTE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                model.note?.let { note ->
                    NoteText(note = note, color = sec, arrowColor = accent, fontSizeSp = 13f)
                }
            }
        }
        Text(
            text = buildAnnotatedString {
                append(model.novelTitle)
                if (model.chapterName != null) {
                    append(" — ")
                    append(model.chapterName)
                }
                append(" · ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
                    append("Tadami")
                }
            },
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = sec,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
        )
    }
}
