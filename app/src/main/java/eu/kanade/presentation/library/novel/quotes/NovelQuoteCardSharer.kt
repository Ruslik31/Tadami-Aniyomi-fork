package eu.kanade.presentation.library.novel.quotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteCardModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteCardStyle
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import java.io.File
import android.graphics.Canvas as AndroidCanvas

/**
 * Renders the selected 4:5 quote card offscreen and shares it as an image
 * (spec 2026-09-11, Feature 3). The bitmap/uri/intent pipeline follows the
 * `NovelImageActionHelper`/`ReaderViewModel.shareImage` canon:
 * cacheImageDir → FileProvider → ACTION_SEND with the image mime type.
 */
interface NovelQuoteCardSharer {

    /**
     * Shares [model] as a 1080×1350 PNG. [capture] provides the offscreen-rendered card bitmap;
     * when it yields nothing, a Canvas/Paint fallback card is rendered instead.
     * Returns true when the share intent was launched.
     */
    suspend fun shareCard(
        model: NovelQuoteCardModel,
        mimeType: String,
        capture: suspend () -> ImageBitmap?,
    ): Boolean

    companion object {
        const val MIME_IMAGE = "image/*"
    }
}

/** Orchestrator seam: maps the highlight to a card model and delegates to [sharer]. */
suspend fun shareQuoteCardAsImage(
    sharer: NovelQuoteCardSharer,
    item: NovelHighlightWithChapter,
    style: NovelQuoteCardStyle,
    capture: suspend () -> ImageBitmap?,
): Boolean = sharer.shareCard(
    model = NovelQuoteCardModel.fromHighlight(item, style),
    mimeType = NovelQuoteCardSharer.MIME_IMAGE,
    capture = capture,
)

class NovelQuoteCardSharerImpl(private val context: Context) : NovelQuoteCardSharer {

    override suspend fun shareCard(
        model: NovelQuoteCardModel,
        mimeType: String,
        capture: suspend () -> ImageBitmap?,
    ): Boolean {
        val captured = runCatching { capture() }.getOrNull()
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = context.cacheImageDir
                dir.deleteRecursively()
                dir.mkdirs()
                val target = File(dir, "novel_quote_card_${System.currentTimeMillis()}.png")
                val bitmap = captured?.asAndroidBitmap() ?: renderFallbackCard(model)
                target.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
                target
            }.getOrNull()
        } ?: return false
        val uri = runCatching { file.getUriCompat(context) }.getOrNull() ?: return false
        return runCatching {
            context.startActivity(uri.toShareIntent(context, type = mimeType))
        }.isSuccess
    }
}

/**
 * Simplified Canvas/Paint rendition of the card (fallback when the Compose offscreen
 * capture is unavailable). Uses the dark palettes of the prototype; the dynamic Aurora
 * accent is not reachable outside composition, so a static accent stands in.
 */
private fun renderFallbackCard(model: NovelQuoteCardModel): Bitmap {
    val w = NovelQuoteCardModel.CARD_WIDTH_PX
    val h = NovelQuoteCardModel.CARD_HEIGHT_PX
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val s = w / 360f

    val bg: Int
    val ink: Int
    val faded: Int
    val accent: Int
    val serifQuote: Boolean
    when (model.style) {
        NovelQuoteCardStyle.CODEX_SACRA -> {
            bg = 0xFFF1E4C6.toInt()
            ink = 0xFF3B2F23.toInt()
            faded = 0xFF7A6A50.toInt()
            accent = faded
            serifQuote = true
        }
        NovelQuoteCardStyle.AURORA_GLASS -> {
            bg = 0xFF0F1116.toInt()
            ink = 0xFFEFE9E0.toInt()
            faded = 0xFF8B96A8.toInt()
            accent = 0xFF0095FF.toInt()
            serifQuote = true
        }
        NovelQuoteCardStyle.MINIMAL -> {
            bg = 0xFF0A0C12.toInt()
            ink = 0xFFF2F5FA.toInt()
            faded = 0xFF94A3B8.toInt()
            accent = 0xFF0095FF.toInt()
            serifQuote = false
        }
    }

    canvas.drawColor(bg)

    val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * s
        color = Color.argb(64, Color.red(ink), Color.green(ink), Color.blue(ink))
    }
    when (model.style) {
        NovelQuoteCardStyle.CODEX_SACRA -> {
            canvas.drawRect(RectF(s, s, w - s, h - s), framePaint)
            val inset = 8f * s
            canvas.drawRect(RectF(inset, inset, w - inset, h - inset), framePaint)
        }
        NovelQuoteCardStyle.AURORA_GLASS -> {
            val inset = 16f * s
            val radius = 20f * s
            canvas.drawRoundRect(
                RectF(inset, inset, w - inset, h - inset),
                radius,
                radius,
                framePaint,
            )
        }
        NovelQuoteCardStyle.MINIMAL -> {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = model.colorArgb.toInt() }
            val lineW = 48f * s
            val top = 30f * s
            canvas.drawRoundRect(
                RectF((w - lineW) / 2f, top, (w + lineW) / 2f, top + 3f * s),
                2f * s,
                2f * s,
                linePaint,
            )
        }
    }

    val margin = if (model.style == NovelQuoteCardStyle.MINIMAL) 26f * s else 28f * s
    val quote = if (serifQuote) "«${model.text}»" else model.text
    val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = model.quoteFontSp * s
        typeface = Typeface.create(
            Typeface.SERIF,
            if (serifQuote) Typeface.BOLD_ITALIC else Typeface.BOLD,
        )
    }
    val availableWidth = w - 2 * margin
    val capped = TextUtils.ellipsize(
        quote,
        quotePaint,
        availableWidth * NovelQuoteCardModel.MAX_QUOTE_LINES,
        TextUtils.TruncateAt.END,
    )
    val layout = StaticLayout.Builder
        .obtain(capped, 0, capped.length, quotePaint, availableWidth.toInt())
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, if (serifQuote) 1.42f else 1.34f)
        .build()

    canvas.save()
    canvas.translate(margin, (h - layout.height) * 0.40f)
    layout.draw(canvas)
    canvas.restore()

    val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = faded
        textSize = 13f * s
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }
    var y = (h - layout.height) * 0.40f + layout.height + 26f * s
    model.chapterName?.let { chapter ->
        canvas.drawText("— $chapter", w / 2f, y, metaPaint)
        y += 20f * s
    }
    val titlePaint = TextPaint(metaPaint).apply {
        color = if (model.style == NovelQuoteCardStyle.MINIMAL) faded else ink
        textSize = 14f * s
        letterSpacing = 0.2f
        isFakeBoldText = true
    }
    canvas.drawText(model.novelTitle.uppercase(), w / 2f, y, titlePaint)
    y += 24f * s
    val watermarkPaint = TextPaint(metaPaint).apply {
        color = accent
        textSize = 10f * s
        letterSpacing = 0.4f
        isFakeBoldText = true
    }
    canvas.drawText("TADAMI", w / 2f, y, watermarkPaint)
    return bitmap
}
