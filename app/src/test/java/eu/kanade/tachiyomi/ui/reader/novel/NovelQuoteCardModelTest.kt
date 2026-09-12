package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import kotlin.math.roundToInt

class NovelQuoteCardModelTest {

    private fun item(
        id: Long = 7,
        novel: String = "Гора Безмолвия",
        chapter: String? = "Глава 3",
        text: String = "цитата $id",
        note: String = "",
        pageIndex: Int = 45,
        pageCount: Int = 312,
        colorArgb: Long = 0xFFFBC02D,
    ) = NovelHighlightWithChapter(
        highlight = NovelHighlight(
            id = id,
            novelId = id * 10,
            chapterId = id * 100,
            blockIndex = 0,
            charStart = 0,
            charEndExclusive = 10,
            normalizedText = text,
            colorArgb = colorArgb,
            note = note,
            createdAt = id,
            updatedAt = id,
            pageIndex = pageIndex,
            pageCount = pageCount,
        ),
        novelTitle = novel,
        chapterName = chapter,
        chapterSourceOrder = 0L,
    )

    @Test
    fun `fromHighlight maps all card fields`() {
        val model = NovelQuoteCardModel.fromHighlight(
            item(note = "  Момент милосердия  ", colorArgb = 0xFF38BDF8),
            NovelQuoteCardStyle.CODEX_SACRA,
        )

        model.text shouldBe "цитата 7"
        model.novelTitle shouldBe "Гора Безмолвия"
        model.chapterName shouldBe "Глава 3"
        model.note shouldBe "Момент милосердия"
        model.pageLabel shouldBe "45/312"
        model.colorArgb shouldBe 0xFF38BDF8
        model.style shouldBe NovelQuoteCardStyle.CODEX_SACRA
    }

    @Test
    fun `missing chapter maps to null chapter name`() {
        val model = NovelQuoteCardModel.fromHighlight(item(chapter = null), NovelQuoteCardStyle.MINIMAL)

        model.chapterName shouldBe null
    }

    @Test
    fun `unknown page position maps to null page label`() {
        val model = NovelQuoteCardModel.fromHighlight(
            item(pageIndex = 0, pageCount = 0),
            NovelQuoteCardStyle.AURORA_GLASS,
        )

        model.pageLabel shouldBe null
    }

    @Test
    fun `blank note maps to null`() {
        NovelQuoteCardModel.fromHighlight(item(note = "   "), NovelQuoteCardStyle.CODEX_SACRA)
            .note shouldBe null
    }

    @Test
    fun `short quote keeps the per style base font without autofit`() {
        val short = item(text = "Тишина говорила громче слов.")

        NovelQuoteCardModel.fromHighlight(short, NovelQuoteCardStyle.CODEX_SACRA)
            .quoteFontSp shouldBe 28f
        NovelQuoteCardModel.fromHighlight(short, NovelQuoteCardStyle.AURORA_GLASS)
            .quoteFontSp shouldBe 26f
        NovelQuoteCardModel.fromHighlight(short, NovelQuoteCardStyle.MINIMAL)
            .quoteFontSp shouldBe 34f
        NovelQuoteCardModel.fromHighlight(short, NovelQuoteCardStyle.CODEX_SACRA)
            .needsAutofit shouldBe false
    }

    @Test
    fun `long quote steps the font down in half point steps`() {
        val long = item(text = "слово ".repeat(50)) // 300 chars

        val model = NovelQuoteCardModel.fromHighlight(long, NovelQuoteCardStyle.CODEX_SACRA)

        model.needsAutofit shouldBe true
        (model.quoteFontSp < 28f) shouldBe true
        (model.quoteFontSp >= NovelQuoteCardModel.MIN_QUOTE_FONT_SP) shouldBe true
        (model.quoteFontSp * 10).roundToInt() % 5 shouldBe 0
    }

    @Test
    fun `very long quote clamps at the minimum font`() {
        val huge = item(text = "ночь ".repeat(1000)) // 5000 chars

        NovelQuoteCardModel.fromHighlight(huge, NovelQuoteCardStyle.CODEX_SACRA)
            .quoteFontSp shouldBe NovelQuoteCardModel.MIN_QUOTE_FONT_SP
    }
}
