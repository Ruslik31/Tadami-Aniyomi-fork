package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Каналы ввода ответа (план aurora-heart-full-fix, Task 6):
 * [AuroraChannels.sigil] кодирует путь по сетке 3x3 и ОТКАЗЫВАЕТ (null)
 * невалидным путям — размер/диапазон ячеек и повторяющиеся ячейки;
 * [AuroraChannels.named] сохраняет прежний контракт (регресс-страховка).
 */
class AuroraChannelsTest {

    @Test
    fun validSigilEncodesCellPath() {
        // (a) Валидный путь из 3 уникальных ячеек — каноническая фраза.
        AuroraChannels.sigil(listOf(1, 5, 9)) shouldBe "sigil:1-5-9"
    }

    @Test
    fun sigilRejectsAllSameCells() {
        // (b) Все ячейки одинаковы — отказ в той же форме (null), что у размера/диапазона.
        AuroraChannels.sigil(listOf(5, 5, 5)) shouldBe null
    }

    @Test
    fun sigilRejectsPartialDuplicate() {
        // (c) Частичный дубликат в пути валидного размера — отказ (null).
        AuroraChannels.sigil(listOf(1, 5, 5, 9)) shouldBe null
    }

    @Test
    fun sigilRejectsBadSizeAndOutOfRangeCells() {
        // (d) Существующее поведение: размер вне 3..9 и ячейки вне 1..9 — отказ (null).
        AuroraChannels.sigil(listOf(1, 2)) shouldBe null
        AuroraChannels.sigil(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 1)) shouldBe null
        AuroraChannels.sigil(listOf(0, 5, 9)) shouldBe null
        AuroraChannels.sigil(listOf(1, 5, 10)) shouldBe null
    }

    @Test
    fun namedChannelKeepsExistingContract() {
        // (e) Именование категории — контракт не меняется.
        AuroraChannels.named("категория", "guovssahas") shouldBe "категория:guovssahas"
    }

    @Test
    fun boundaryValidSigilsPass() {
        // (f) Пограничные валидные: минимальные 3 ячейки и полный путь из 9 уникальных.
        AuroraChannels.sigil(listOf(1, 2, 3)) shouldBe "sigil:1-2-3"
        AuroraChannels.sigil(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)) shouldBe "sigil:1-2-3-4-5-6-7-8-9"
    }
}
