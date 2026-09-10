package eu.kanade.domain.easteregg.aurora

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Глобальная шина «эха»: AuroraQuest.offer() сам кладёт сюда
 * каждый пройденный этап (Progress) и финальную разблокировку
 * (payload). UI-оверлей AuroraEchoOverlay подписывается ОДИН раз
 * в корне приложения — вспышка этапа и финальный экран
 * появляются поверх ЛЮБОГО экрана без ручной проводки.
 *
 * На неверные ответы сюда НИЧЕГО не попадает (правило тишины).
 *
 * Task 11 (B1/L6): каждая эмиссия Progress/шёпота зеркалится в
 * персистентную очередь (AuroraEchoQueue на AuroraPrefKeys.PENDING;
 * persister/loader подключает AuroraHeartManager при создании, до
 * любых emit). Занятый слот БОЛЬШЕ НЕ перезаписывается: второе
 * эхо ждёт в очереди и проигрывается дренажом оверлея. consume
 * удаляет сыгранное эхо из очереди, поэтому live-эхо никогда не
 * дублируется, а непросмотренное — переживает смерть процесса.
 * Unlocked в очереди не участвует (свой путь награждения, Task 8).
 */
object AuroraEchoBus {
    private val _flash = MutableStateFlow<AuroraEcho.Progress?>(null)
    val flash: StateFlow<AuroraEcho.Progress?> = _flash

    private val _unlocked = MutableStateFlow<AuroraPayload?>(null)
    val unlocked: StateFlow<AuroraPayload?> = _unlocked

    /** Одноразовые «шёпоты» — тихие атмосферные намёки (без ответов!). */
    private val _whisper = MutableStateFlow<String?>(null)
    val whisper: StateFlow<String?> = _whisper

    /**
     * Хук побочных эффектов финала: разблокировка достижения,
     * начисление очков, уведомление. Установить ОДИН раз при
     * старте приложения (см. ШАГ 13 в AI_INTEGRATION_GUIDE.md).
     * Вызывается до показа финального экрана.
     * Не логировать содержимое payload.
     */
    @Volatile
    var onUnlocked: ((AuroraPayload) -> Unit)? = null

    /**
     * Персистентность очереди отложенных эхо (Task 11). Подключаются
     * ОДИН раз AuroraHeartManager при создании (AuroraPrefKeys.PENDING).
     * null — очередь выключена (JVM-тесты без менеджера): шина работает
     * только в памяти, публичное поведение emit/consume не меняется.
     */
    @Volatile
    var persister: ((String) -> Unit)? = null

    @Volatile
    var loader: (() -> String?)? = null

    /**
     * Сериализует read-modify-write очереди (loader→JSON→persister): эмиттеры
     * работают на IO (offer/registerNightAction), consume и дренаж — на Main.
     * Без замка два перекрывшихся цикла потеряли бы одну из записей (L6-класс).
     */
    private val queueLock = Any()

    fun emit(echo: AuroraEcho.Progress) {
        synchronized(queueLock) {
            // L6: занятый слот не перезаписывается — эхо уходит в очередь и сыграет следующим дренажом.
            _flash.compareAndSet(null, echo)
            val now = System.currentTimeMillis()
            enqueuePending(
                PendingEcho(
                    type = PendingEcho.TYPE_PROGRESS,
                    ts = now,
                    stageIndex = echo.stageIndex,
                    totalStages = echo.totalStages,
                    echoTitle = echo.echoTitle,
                ),
                now,
            )
        }
    }

    fun consume() {
        synchronized(queueLock) {
            val consumed = _flash.value
            _flash.value = null
            // B1: сыгранное эхо убирается из очереди — следующий дренаж его НЕ повторит.
            if (consumed != null) {
                dequeuePending(
                    PendingEcho(
                        type = PendingEcho.TYPE_PROGRESS,
                        ts = 0L,
                        stageIndex = consumed.stageIndex,
                        totalStages = consumed.totalStages,
                        echoTitle = consumed.echoTitle,
                    ),
                )
            }
        }
    }

    fun emitUnlocked(payload: AuroraPayload) {
        runCatching { onUnlocked?.invoke(payload) }
        _unlocked.value = payload
    }

    fun consumeUnlocked() {
        _unlocked.value = null
    }

    fun emitWhisper(text: String) {
        synchronized(queueLock) {
            // L6 для шёпотов: занятый слот не перезаписывается — текст ждёт в очереди.
            _whisper.compareAndSet(null, text)
            val now = System.currentTimeMillis()
            enqueuePending(PendingEcho(type = PendingEcho.TYPE_WHISPER, ts = now, whisper = text), now)
        }
    }

    fun consumeWhisper() {
        synchronized(queueLock) {
            val consumed = _whisper.value
            _whisper.value = null
            if (consumed != null) {
                dequeuePending(PendingEcho(type = PendingEcho.TYPE_WHISPER, ts = 0L, whisper = consumed))
            }
        }
    }

    /**
     * Голова персистентной очереди БЕЗ извлечения (Task 11): сыгранное эхо
     * удалит consume/consumeWhisper, поэтому пауза или смерть процесса
     * посреди дренажа ничего не теряет — непросмотренный хвост остаётся в
     * PENDING. Побочно чистит персист от просроченного остатка (дренаж
     * отбросил всё — записываем остаток, т.е. пусто). Невалидные записи
     * (чужой type, шёпот без текста) молча удаляются, чтобы не клинить
     * очередь. null — проигрывать нечего.
     */
    fun peekDeferredHead(now: Long = System.currentTimeMillis()): PendingEcho? {
        synchronized(queueLock) {
            while (true) {
                val json = loader?.invoke()
                if (json.isNullOrEmpty()) return null
                val (items, rest) = AuroraEchoQueue.drain(json, now)
                val head = items.firstOrNull()
                if (head == null) {
                    persister?.invoke(rest ?: "")
                    return null
                }
                val valid = head.type == PendingEcho.TYPE_PROGRESS ||
                    (head.type == PendingEcho.TYPE_WHISPER && head.whisper != null)
                if (!valid) {
                    dequeuePending(head)
                    continue
                }
                return head
            }
        }
    }

    /**
     * Проигрывание ОТСРОЧЕННОГО эха (дренаж оверлея, Task 11): CAS в слот
     * БЕЗ повторной постановки в очередь — очередную копию удалит consume.
     * false — слот занят live-эхом (приоритет у live); вызывающий ждёт
     * consume и повторяет попытку.
     */
    fun replay(echo: AuroraEcho.Progress): Boolean = _flash.compareAndSet(null, echo)

    fun replayWhisper(text: String): Boolean = _whisper.compareAndSet(null, text)

    private fun enqueuePending(echo: PendingEcho, now: Long) {
        val save = persister ?: return
        save(AuroraEchoQueue.enqueue(loader?.invoke(), echo, now))
    }

    private fun dequeuePending(consumed: PendingEcho) {
        val save = persister ?: return
        val queue = AuroraEchoQueue.decode(loader?.invoke())
        val index = queue.indexOfFirst { it.sameContentAs(consumed) }
        if (index < 0) return
        save(AuroraEchoQueue.encode(queue.filterIndexed { i, _ -> i != index }))
    }
}
