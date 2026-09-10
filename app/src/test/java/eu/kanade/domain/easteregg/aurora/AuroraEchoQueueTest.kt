package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Чистая персистентная очередь отложенных эхо (план aurora-heart-full-fix, Task 11):
 * [AuroraEchoQueue] — encode/decode/enqueue/drain без Android-зависимостей (JVM).
 * Контракты брифа (a-e) + гигиена enqueue и sameContentAs (по нему bus удаляет
 * consumed-эхо из очереди, игнорируя ts).
 */
class AuroraEchoQueueTest {

    private val now = 1_757_500_000_000L

    private fun progress(stageIndex: Int, ts: Long = now) = PendingEcho(
        type = PendingEcho.TYPE_PROGRESS,
        ts = ts,
        stageIndex = stageIndex,
        totalStages = 5,
        echoTitle = "Эхо $stageIndex",
    )

    private fun whisper(text: String, ts: Long = now) = PendingEcho(
        type = PendingEcho.TYPE_WHISPER,
        ts = ts,
        whisper = text,
    )

    @Test
    fun roundtripEncodeDecodeIsLosslessAndTolerant() {
        // (a) roundtrip: encode→decode семантически равен исходному списку.
        val list = listOf(progress(1), whisper("Граница тонка"), progress(2))
        AuroraEchoQueue.decode(AuroraEchoQueue.encode(list)) shouldBe list

        // (a) null / мусор / пустая строка -> emptyList, без исключений.
        AuroraEchoQueue.decode(null) shouldBe emptyList()
        AuroraEchoQueue.decode("мусор") shouldBe emptyList()
        AuroraEchoQueue.decode("{битый json") shouldBe emptyList()
        AuroraEchoQueue.decode("") shouldBe emptyList()

        // (a) пустой список -> "" (ключ в prefs остаётся пустым, не «[]»-шумом).
        AuroraEchoQueue.encode(emptyList()) shouldBe ""
        AuroraEchoQueue.decode("[]") shouldBe emptyList()
    }

    @Test
    fun enqueueAppendsFifoAndDrainReturnsAllInOrder() {
        // (b) FIFO: enqueue×3 -> drain возвращает 3 в порядке добавления, остаток пуст.
        val first = progress(1)
        val second = whisper("Некоторые границы тоньше других.")
        val third = progress(2)

        var json: String? = null
        json = AuroraEchoQueue.enqueue(json, first, now)
        json = AuroraEchoQueue.enqueue(json, second, now)
        json = AuroraEchoQueue.enqueue(json, third, now)

        val (items, rest) = AuroraEchoQueue.drain(json, now)

        items shouldBe listOf(first, second, third)
        rest shouldBe null
        AuroraEchoQueue.decode(rest) shouldBe emptyList()
    }

    @Test
    fun drainDiscardsExpiredAndKeepsFresh() {
        // (c) expiry: ts = now - MAX_AGE - 1 -> отброшен; свежий -> возвращён.
        val expired = progress(1, ts = now - AuroraEchoQueue.MAX_AGE_MILLIS - 1)
        val fresh = whisper("свежий шёпот", ts = now - 1_000)
        val json = AuroraEchoQueue.encode(listOf(expired, fresh))

        val (items, rest) = AuroraEchoQueue.drain(json, now)

        items shouldBe listOf(fresh)
        rest shouldBe null

        // Граница: возраст ровно MAX_AGE — ещё НЕ «старше», элемент остаётся.
        val boundary = progress(2, ts = now - AuroraEchoQueue.MAX_AGE_MILLIS)
        AuroraEchoQueue.drain(AuroraEchoQueue.encode(listOf(boundary)), now).first shouldBe listOf(boundary)

        // Очередь только из просроченного -> drain возвращает пустой список.
        AuroraEchoQueue.drain(AuroraEchoQueue.encode(listOf(expired)), now).first shouldBe emptyList()
    }

    @Test
    fun drainIsTotalNeverPartial() {
        // (d) частичный дренаж невозможен: drain забирает ВСЁ, остаток всегда пуст —
        // повторный drain остатка ничего не возвращает.
        val json = AuroraEchoQueue.encode(listOf(progress(1), progress(2), progress(3)))

        val (items, rest) = AuroraEchoQueue.drain(json, now)

        items.size shouldBe 3
        rest shouldBe null
        AuroraEchoQueue.drain(rest, now).first shouldBe emptyList()
    }

    @Test
    fun pendingEchoSerializesStablyFieldByField() {
        // (e) kotlinx-Json стабильность: encode→decode сохраняет каждое поле (включая null).
        val echo = PendingEcho(
            type = PendingEcho.TYPE_PROGRESS,
            ts = 42L,
            stageIndex = 3,
            totalStages = 5,
            echoTitle = "Зов Авроры",
        )

        val decoded = AuroraEchoQueue.decode(AuroraEchoQueue.encode(listOf(echo))).single()

        decoded.type shouldBe PendingEcho.TYPE_PROGRESS
        decoded.ts shouldBe 42L
        decoded.stageIndex shouldBe 3
        decoded.totalStages shouldBe 5
        decoded.echoTitle shouldBe "Зов Авроры"
        decoded.whisper shouldBe null
        decoded shouldBe echo
    }

    @Test
    fun enqueuePrunesExpiredBeforeAppending() {
        // Гигиена enqueue: просроченный элемент не переживает следующее добавление.
        val expired = progress(1, ts = now - AuroraEchoQueue.MAX_AGE_MILLIS - 1)
        val fresh = progress(2)

        val json = AuroraEchoQueue.enqueue(AuroraEchoQueue.encode(listOf(expired)), fresh, now)

        AuroraEchoQueue.decode(json) shouldBe listOf(fresh)
    }

    @Test
    fun sameContentAsIgnoresTimestamp() {
        // bus.consume() удаляет из очереди эхо по содержимому: ts enqueue и ts consume разные.
        val queued = progress(1, ts = now - 5_000)

        queued.sameContentAs(progress(1)) shouldBe true
        queued.sameContentAs(progress(2)) shouldBe false
        queued.sameContentAs(whisper("другой тип", ts = now - 5_000)) shouldBe false
    }
}
