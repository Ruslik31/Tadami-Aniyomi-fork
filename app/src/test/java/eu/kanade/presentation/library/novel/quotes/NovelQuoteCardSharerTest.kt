package eu.kanade.presentation.library.novel.quotes

import androidx.compose.ui.graphics.ImageBitmap
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteCardModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelQuoteCardStyle
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter

class NovelQuoteCardSharerTest {

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

    private class FakeSharer : NovelQuoteCardSharer {
        var model: NovelQuoteCardModel? = null
        var mimeType: String? = null
        var captureInvoked = false

        override suspend fun shareCard(
            model: NovelQuoteCardModel,
            mimeType: String,
            capture: suspend () -> ImageBitmap?,
        ): Boolean {
            this.model = model
            this.mimeType = mimeType
            captureInvoked = true
            return capture() != null
        }
    }

    @Test
    fun `shareQuoteCardAsImage maps the highlight with the chosen style and image mime`() = runTest {
        val fake = FakeSharer()

        val shared = shareQuoteCardAsImage(fake, item(note = "заметка"), NovelQuoteCardStyle.AURORA_GLASS) { null }

        shared shouldBe false
        fake.captureInvoked shouldBe true
        fake.mimeType shouldBe "image/*"
        val model = fake.model ?: error("sharer was not called")
        model.style shouldBe NovelQuoteCardStyle.AURORA_GLASS
        model.text shouldBe "цитата 7"
        model.novelTitle shouldBe "Гора Безмолвия"
        model.chapterName shouldBe "Глава 3"
        model.note shouldBe "заметка"
        model.pageLabel shouldBe "45/312"
        model.colorArgb shouldBe 0xFFFBC02D
    }

    @Test
    fun `share is not reported when the offscreen capture yields nothing`() = runTest {
        val fake = FakeSharer()

        shareQuoteCardAsImage(fake, item(), NovelQuoteCardStyle.MINIMAL) { null } shouldBe false
    }
}
