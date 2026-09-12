package eu.kanade.tachiyomi.ui.reader.novel

import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import kotlin.math.ceil

/** Designs of the 4:5 quote share card (spec 2026-09-11, Feature 3). */
enum class NovelQuoteCardStyle {
    CODEX_SACRA,
    AURORA_GLASS,
    MINIMAL,
}

/**
 * Render-ready data of one quote share card, mapped from a stored highlight.
 * The card itself is drawn by `NovelQuoteCardViews`; sharing goes through `NovelQuoteCardSharer`.
 */
data class NovelQuoteCardModel(
    val text: String,
    val novelTitle: String,
    val chapterName: String?,
    val note: String?,
    val pageIndex: Int,
    val pageCount: Int,
    val colorArgb: Long,
    val style: NovelQuoteCardStyle,
    val quoteFontSp: Float,
) {
    /** "45/312"; null when the page position is unknown (0/0). */
    val pageLabel: String?
        get() = if (pageCount > 0) "$pageIndex/$pageCount" else null

    val needsAutofit: Boolean
        get() = quoteFontSp < baseFontSp(style)

    companion object {
        const val CARD_WIDTH_PX = 1080
        const val CARD_HEIGHT_PX = 1350
        const val MAX_QUOTE_LINES = 12
        const val MIN_QUOTE_FONT_SP = 19f
        const val QUOTE_FONT_STEP_SP = 0.5f

        // Estimation canvas: the capture layout is pinned to 360x450dp (1080x1350px at density 3),
        // the quote column is ~300dp wide and an average glyph advance is ~0.5em.
        private const val QUOTE_COLUMN_DP = 300f
        private const val AVG_CHAR_WIDTH_EM = 0.5f

        fun baseFontSp(style: NovelQuoteCardStyle): Float = when (style) {
            NovelQuoteCardStyle.CODEX_SACRA -> 28f
            NovelQuoteCardStyle.AURORA_GLASS -> 26f
            NovelQuoteCardStyle.MINIMAL -> 34f
        }

        fun fromHighlight(item: NovelHighlightWithChapter, style: NovelQuoteCardStyle): NovelQuoteCardModel {
            val highlight = item.highlight
            return NovelQuoteCardModel(
                text = highlight.normalizedText,
                novelTitle = item.novelTitle,
                chapterName = item.chapterName,
                note = highlight.note.trim().takeIf { it.isNotEmpty() },
                pageIndex = highlight.pageIndex,
                pageCount = highlight.pageCount,
                colorArgb = highlight.colorArgb,
                style = style,
                quoteFontSp = estimateQuoteFontSp(highlight.normalizedText, style),
            )
        }

        /** JVM-side autofit estimate; the card composables refine it with real text measurement. */
        fun estimateQuoteFontSp(text: String, style: NovelQuoteCardStyle): Float {
            var size = baseFontSp(style)
            while (size > MIN_QUOTE_FONT_SP && estimateLineCount(text, size) > MAX_QUOTE_LINES) {
                size -= QUOTE_FONT_STEP_SP
            }
            return size
        }

        private fun estimateLineCount(text: String, fontSp: Float): Int {
            val charsPerLine = (QUOTE_COLUMN_DP / (fontSp * AVG_CHAR_WIDTH_EM)).toInt().coerceAtLeast(1)
            return ceil(text.length.toFloat() / charsPerLine).toInt()
        }
    }
}
