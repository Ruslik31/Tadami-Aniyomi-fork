package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Контракт шины с персистентной очередью (план aurora-heart-full-fix, Task 11):
 * L6 — занятый слот НЕ перезаписывается (второе эхо ждёт в очереди);
 * B1 — каждая эмиссия зеркалится в очередь, а consume удаляет сыгранное эхо
 * (дренаж не дублирует live-показ); peekDeferredHead чистит просрочку и
 * выбрасывает невалидные записи; replay/replayWhisper — CAS в слот БЕЗ
 * повторной постановки в очередь; Unlocked в очереди не участвует (Task 8).
 *
 * Синхронный JVM-тест: слоты — StateFlow.value, persister/loader — in-memory
 * строка (автобус глобальный — BeforeEach/AfterEach чистят слоты и проводку).
 */
class AuroraEchoBusQueueTest {

    private var storage: String? = null

    @BeforeEach
    fun wireInMemoryQueue() {
        // Шина — глобальный object: очистить слоты (в т.ч. от других suites) и подключить in-memory персист.
        AuroraEchoBus.consume()
        AuroraEchoBus.consumeWhisper()
        AuroraEchoBus.consumeUnlocked()
        storage = null
        AuroraEchoBus.loader = { storage }
        AuroraEchoBus.persister = { storage = it }
    }

    @AfterEach
    fun unwire() {
        AuroraEchoBus.consume()
        AuroraEchoBus.consumeWhisper()
        AuroraEchoBus.consumeUnlocked()
        AuroraEchoBus.loader = null
        AuroraEchoBus.persister = null
        storage = null
    }

    private fun progress(stage: Int) = AuroraEcho.Progress(
        stageIndex = stage,
        totalStages = 5,
        echoTitle = "Эхо $stage",
    )

    private fun pendingProgress(stage: Int, ts: Long = 0L) = PendingEcho(
        type = PendingEcho.TYPE_PROGRESS,
        ts = ts,
        stageIndex = stage,
        totalStages = 5,
        echoTitle = "Эхо $stage",
    )

    private fun queue(): List<PendingEcho> = AuroraEchoQueue.decode(storage)

    @Test
    fun liveEmitMirrorsToQueueAndConsumeRemovesIt() {
        // B1: live-эхо (свободный слот) показывается как раньше И зеркалится в PENDING.
        val echo = progress(1)
        AuroraEchoBus.emit(echo)

        AuroraEchoBus.flash.value shouldBe echo
        queue().single().sameContentAs(pendingProgress(1)) shouldBe true

        // consume убирает зеркало — следующий дренаж НЕ повторит сыгранное эхо.
        AuroraEchoBus.consume()

        AuroraEchoBus.flash.value shouldBe null
        queue() shouldBe emptyList()
    }

    @Test
    fun busySlotIsNotOverwrittenSecondEchoWaitsInQueue() {
        // L6: второе эхо при занятом слоте не теряет первое и не теряется само.
        val first = progress(1)
        val second = progress(2)
        AuroraEchoBus.emit(first)
        AuroraEchoBus.emit(second)

        AuroraEchoBus.flash.value shouldBe first // слот НЕ перезаписан
        queue().map { it.stageIndex } shouldBe listOf(1, 2) // FIFO-очередь хранит оба

        AuroraEchoBus.consume() // первое сыграно

        AuroraEchoBus.flash.value shouldBe null
        queue().single().stageIndex shouldBe 2 // второе ждёт дренажа

        // Дренаж: replay ставит второе в слот БЕЗ повторной постановки в очередь.
        AuroraEchoBus.replay(second) shouldBe true
        AuroraEchoBus.flash.value shouldBe second
        queue().single().stageIndex shouldBe 2

        AuroraEchoBus.consume()
        queue() shouldBe emptyList()
    }

    @Test
    fun whisperFollowsSameQueueSemantics() {
        AuroraEchoBus.emitWhisper("Первый")
        AuroraEchoBus.emitWhisper("Второй")

        AuroraEchoBus.whisper.value shouldBe "Первый"
        queue().map { it.whisper } shouldBe listOf("Первый", "Второй")

        AuroraEchoBus.consumeWhisper()

        AuroraEchoBus.whisper.value shouldBe null
        queue().single().whisper shouldBe "Второй"

        AuroraEchoBus.replayWhisper("Второй") shouldBe true
        AuroraEchoBus.consumeWhisper()
        queue() shouldBe emptyList()
    }

    @Test
    fun replayYieldsToLiveEcho() {
        // Приоритет live: replay не отнимает занятый слот (false — вызывающий ждёт consume).
        val live = progress(3)
        AuroraEchoBus.emit(live)

        AuroraEchoBus.replay(progress(1)) shouldBe false
        AuroraEchoBus.flash.value shouldBe live

        AuroraEchoBus.replayWhisper("шёпот") shouldBe true // whisper-слот свободен
        AuroraEchoBus.replayWhisper("второй") shouldBe false

        AuroraEchoBus.consumeWhisper()
        AuroraEchoBus.consume()
    }

    @Test
    fun peekDeferredHeadDropsInvalidKeepsValid() {
        // Невалидные записи (чужой type, шёпот без текста) выбрасываются физически —
        // иначе они заклинили бы дренаж (бесконечный цикл на одной голове).
        val now = System.currentTimeMillis()
        val expired = pendingProgress(1, ts = now - AuroraEchoQueue.MAX_AGE_MILLIS - 1)
        val bogus = PendingEcho(type = "bogus", ts = now)
        val whisperNoText = PendingEcho(type = PendingEcho.TYPE_WHISPER, ts = now)
        val valid = pendingProgress(2, ts = now)
        storage = AuroraEchoQueue.encode(listOf(expired, bogus, whisperNoText, valid))

        AuroraEchoBus.peekDeferredHead(now)?.sameContentAs(valid) shouldBe true

        val left = queue()
        left.any { it.type == "bogus" } shouldBe false
        left.any { it.type == PendingEcho.TYPE_WHISPER && it.whisper == null } shouldBe false
        left.count { it.sameContentAs(valid) } shouldBe 1
    }

    @Test
    fun peekDeferredHeadClearsExpiredRemains() {
        // Очередь только из просрочки: дренаж отдаёт null и чистит персист (записывает остаток).
        val now = System.currentTimeMillis()
        storage = AuroraEchoQueue.encode(listOf(pendingProgress(1, ts = now - AuroraEchoQueue.MAX_AGE_MILLIS - 1)))

        AuroraEchoBus.peekDeferredHead(now) shouldBe null

        storage shouldBe ""
    }

    @Test
    fun unlockedBypassesQueue() {
        // Unlocked в очередь НЕ попадает (у финала свой путь: хук Task 8 + кодек/ачивки).
        AuroraEchoBus.emitUnlocked(AuroraPayload(kind = "final"))

        queue() shouldBe emptyList()
        AuroraEchoBus.unlocked.value?.kind shouldBe "final"

        AuroraEchoBus.consumeUnlocked()
        AuroraEchoBus.unlocked.value shouldBe null
        queue() shouldBe emptyList()
    }
}
