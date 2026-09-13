package eu.kanade.domain.easteregg.aurora

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Отложенное эхо для персистентной очереди (Task 11, B1/L6): снимок слота шины,
 * переживающий process death и невидимое окно. [AuroraEcho.Unlocked] в очередь
 * НЕ попадает — у финала свой путь (хук-награждение Task 8 + кодек/ачивки).
 */
@Serializable
data class PendingEcho(
    val type: String, // TYPE_PROGRESS | TYPE_WHISPER
    val ts: Long, // System.currentTimeMillis() на enqueue
    val stageIndex: Int? = null,
    val totalStages: Int? = null,
    val echoTitle: String? = null,
    val whisper: String? = null,
) {
    companion object {
        const val TYPE_PROGRESS = "progress"
        const val TYPE_WHISPER = "whisper"
    }

    /**
     * Равенство по содержимому БЕЗ [ts]: AuroraEchoBus.consume() удаляет из очереди
     * проигранное эхо, а момент consume всегда позже момента enqueue.
     */
    fun sameContentAs(other: PendingEcho): Boolean =
        type == other.type &&
            stageIndex == other.stageIndex &&
            totalStages == other.totalStages &&
            echoTitle == other.echoTitle &&
            whisper == other.whisper
}

/**
 * Чистые функции персистентной очереди эхо (Task 11): kotlinx-JSON без Android-
 * зависимостей, хранится под ключом [AuroraPrefKeys.PENDING] («rp_pend»).
 * Read-modify-write циклы шины (эмиттеры на IO, consume на Main) сериализует
 * AuroraEchoBus; все функции здесь детерминированы и потокобезопасны сами по себе.
 * Битый/чужой JSON молча превращается в пустую очередь (правило тишины).
 */
object AuroraEchoQueue {

    /** Эхо старше суток не проигрывается: момент упущен, антикварная вспышка — шум. */
    const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

    private val format = Json { ignoreUnknownKeys = true }

    /** Кодирует очередь; пустой список -> "" (пустой ключ prefs вместо «[]»-шума). */
    fun encode(list: List<PendingEcho>): String =
        if (list.isEmpty()) "" else format.encodeToString(list)

    /** Декодирует очередь; null / битый / пустой -> emptyList, без исключений. */
    fun decode(json: String?): List<PendingEcho> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { format.decodeFromString<List<PendingEcho>>(json) }.getOrDefault(emptyList())
    }

    /** FIFO-append + гигиена: элементы, просроченные к [now], не переживают добавление. */
    fun enqueue(json: String?, echo: PendingEcho, now: Long): String =
        encode(decode(json).filter { isFresh(it, now) } + echo)

    /**
     * Забирает ВСЕ свежие эхо по порядку добавления; элементы с ts старше
     * [MAX_AGE_MILLIS] отбрасывает. Частичный дренаж невозможен (контракт d
     * AuroraEchoQueueTest): второй элемент всегда null — остаток пуст.
     */
    fun drain(json: String?, now: Long): Pair<List<PendingEcho>, String?> =
        decode(json).filter { isFresh(it, now) } to null

    private fun isFresh(echo: PendingEcho, now: Long): Boolean = now - echo.ts <= MAX_AGE_MILLIS
}
