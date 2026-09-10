package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Чистый счётчик уникальных ночей (план aurora-heart-full-fix, Task 3):
 * [AuroraNights.advance] решает, новая ли ночь, СТРОКОВЫМ сравнением ключа
 * суток — без часов, префов и арифметики над ключами (время — за вызывающим).
 */
class AuroraNightsTest {

    @Test
    fun firstNightWithoutLastKeyAdvancesCount() {
        // (a) Ключ последней ночи ещё не сохранён — первое действие делает ночь новой.
        val adv = AuroraNights.advance(lastNightKey = null, todayKey = "20000", currentCount = 0)

        adv.isNewNight shouldBe true
        adv.count shouldBe 1
        adv.lastNightKey shouldBe "20000"
    }

    @Test
    fun sameNightKeepsCountUnchanged() {
        // (b) Повторное действие в те же сутки — ночь не новая, счётчик не растёт.
        val adv = AuroraNights.advance(lastNightKey = "20000", todayKey = "20000", currentCount = 1)

        adv.isNewNight shouldBe false
        adv.count shouldBe 1
        adv.lastNightKey shouldBe "20000"
    }

    @Test
    fun threeConsecutiveNightsReachWhisperThreshold() {
        // (c) Три уникальные ночи подряд — count доходит до WHISPER_THRESHOLD (=3).
        val first = AuroraNights.advance(lastNightKey = null, todayKey = "20000", currentCount = 0)
        val second = AuroraNights.advance(first.lastNightKey, "20001", first.count)
        val third = AuroraNights.advance(second.lastNightKey, "20002", second.count)

        first.isNewNight shouldBe true
        second.isNewNight shouldBe true
        third.isNewNight shouldBe true
        third.count shouldBe 3
    }

    @Test
    fun fiveActionsWithinOneNightCountAsOne() {
        // (d) Пять вызовов с одной датой — ночь одна, count=1.
        var adv = AuroraNights.advance(lastNightKey = null, todayKey = "20000", currentCount = 0)
        repeat(4) {
            adv = AuroraNights.advance(adv.lastNightKey, "20000", adv.count)
            adv.isNewNight shouldBe false
        }

        adv.count shouldBe 1
    }

    @Test
    fun keysAreComparedAsStringsNotNumbers() {
        // (e) «020000» != «20000» как строки — никакой арифметики внутри advance.
        val adv = AuroraNights.advance(lastNightKey = "020000", todayKey = "20000", currentCount = 5)

        adv.isNewNight shouldBe true
        adv.count shouldBe 6
        adv.lastNightKey shouldBe "20000"
    }
}
