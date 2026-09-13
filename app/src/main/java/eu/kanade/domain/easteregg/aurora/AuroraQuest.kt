package eu.kanade.domain.easteregg.aurora

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Результат попытки: прогресс по цепочке или финальная разблокировка. */
sealed interface AuroraEcho {
    data class Progress(
        val stageIndex: Int,
        val totalStages: Int,
        val echoTitle: String?,
    ) : AuroraEcho

    data class Unlocked(val payload: AuroraPayload) : AuroraEcho
}

/**
 * RU+EN пара текущей загадки (Task 13): ваулт и prefs хранят оба варианта.
 * Q5 локаль-выбор — [display]: EN-юзеру En-текст, при отсутствии En
 * (приватный сценарий) — фолбэк на RU.
 */
data class AuroraRiddleText(val ru: String, val en: String? = null) {
    fun display(): String = if (AuroraLocalization.isEnglish()) (en ?: ru) else ru
}

/** Формат хранения RIDDLE (Task 13): JSON `{"r": RU, "re": EN?}` в одном ключе. */
@Serializable
private data class StoredRiddle(val r: String, val re: String? = null)

/**
 * Состояние квеста «Сердце Авроры».
 *
 * Точки входа:
 *  - [revealHint] — вызывается триггером (например, серия, досмотренная
 *    в 02:45–04:15) — открывает первую загадку;
 *  - [offer] — скармливается каждый ОТПРАВЛЕННЫЙ запрос глобального поиска
 *    (именно submit, не каждый символ — PBKDF2 намеренно медленный).
 *
 * Имена SharedPreferences нарочно неприметные — не выдают пасхалку
 * при беглом осмотре данных приложения.
 */
class AuroraQuest internal constructor(
    context: Context,
    private val stages: List<AuroraStage>,
    private val firstRiddle: String,
    private val firstRiddleEn: String,
) {

    /**
     * Публичный конструктор (продакшн-путь) — всегда реальный ваулт [AuroraVaultData].
     * Internal-seam (Task 15): тесты строят квест на фикстурных ступенях, не зная
     * настоящих ответов; публичный API и поведение не меняются.
     */
    constructor(context: Context) : this(
        context,
        AuroraVaultData.STAGES,
        AuroraVaultData.FIRST_RIDDLE,
        AuroraVaultData.FIRST_RIDDLE_EN,
    )

    private val prefs = context.applicationContext
        .getSharedPreferences(AuroraPrefKeys.STORE, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    val isHintRevealed: Boolean get() = prefs.getBoolean(AuroraPrefKeys.HINT, false)
    val isUnlocked: Boolean get() = prefs.getBoolean(AuroraPrefKeys.DONE, false)
    private val stageIndex: Int get() = prefs.getInt(AuroraPrefKeys.STAGE, 0)

    /** Публичные аксессоры для AuroraHeartManager (UI ходит через менеджер). */
    val currentStageIndex: Int get() = stageIndex
    val totalStagesCount: Int get() = stages.size

    /** Вызывается триггером-событием. Повторные вызовы безвредны. */
    fun revealHint() {
        if (!isHintRevealed) prefs.edit().putBoolean(AuroraPrefKeys.HINT, true).apply()
    }

    /**
     * Текущая загадка для UI (RU+EN, Task 13). null — пока подсказка
     * не открыта или квест пройден. Обратная совместимость старого
     * формата RIDDLE не нужна: регенерация ваулта меняет VERSION →
     * migrateIfNeeded стирает quest-prefs (мягкая миграция by design).
     */
    fun currentRiddle(): AuroraRiddleText? = when {
        !isHintRevealed || isUnlocked -> null
        stageIndex == 0 -> AuroraRiddleText(firstRiddle, firstRiddleEn)
        else -> prefs.getString(AuroraPrefKeys.RIDDLE, null)
            ?.let { stored -> runCatching { json.decodeFromString<StoredRiddle>(stored) }.getOrNull() }
            ?.let { AuroraRiddleText(it.r, it.re) }
    }

    /** Финальная награда, если квест пройден на этом устройстве. */
    fun unlockedPayload(): AuroraPayload? =
        prefs.getString(AuroraPrefKeys.PAYLOAD, null)?.let {
            runCatching { json.decodeFromString<AuroraPayload>(it) }.getOrNull()
        }

    /**
     * Проверяет поисковый запрос против текущей ступени.
     * До [revealHint] всегда null — квест ещё не начался (гейт B2).
     * Возвращает null почти всегда — вызов безопасен и незаметен.
     */
    fun offer(query: String): AuroraEcho? {
        if (isUnlocked) return null
        if (!isHintRevealed) return null
        if (query.length !in 3..64) return null
        val idx = stageIndex
        val stage = stages.getOrNull(idx) ?: return null
        val plain = AuroraVault.tryOpen(query, stage)?.decodeToString() ?: return null
        val payload = runCatching { json.decodeFromString<AuroraPayload>(plain) }.getOrNull() ?: return null

        return if (payload.kind == "final") {
            prefs.edit()
                .putBoolean(AuroraPrefKeys.DONE, true)
                .putString(AuroraPrefKeys.PAYLOAD, plain)
                .remove(AuroraPrefKeys.RIDDLE)
                .apply()
            AuroraEchoBus.emitUnlocked(payload)
            AuroraEcho.Unlocked(payload)
        } else {
            prefs.edit()
                .putInt(AuroraPrefKeys.STAGE, idx + 1)
                .putBoolean(AuroraPrefKeys.HINT, true)
                .putString(AuroraPrefKeys.RIDDLE, storedRiddle(payload))
                .apply()
            appendEcho(payload.echoTitle, payload.riddle)
            val echo = AuroraEcho.Progress(
                stageIndex = idx + 1,
                totalStages = stages.size,
                echoTitle = payload.echoTitle,
            )
            AuroraEchoBus.emit(echo)
            echo
        }
    }

    /** Запись загадки по новой схеме (Task 13): оба варианта одним JSON; null → удаление (как раньше). */
    private fun storedRiddle(payload: AuroraPayload): String? =
        payload.riddle?.let { json.encodeToString(StoredRiddle(it, payload.riddleEn)) }

    /** Кодекс: журнал пройденных эх (читает AuroraHeartManager.codex()). */
    private fun appendEcho(title: String?, riddle: String?) {
        val list = runCatching {
            json.decodeFromString<List<AuroraCodexEntry>>(prefs.getString(AuroraPrefKeys.ECHOES, null) ?: "[]")
        }.getOrDefault(emptyList())
        prefs.edit()
            .putString(
                AuroraPrefKeys.ECHOES,
                json.encodeToString(list + AuroraCodexEntry(title = title, riddle = riddle)),
            )
            .apply()
    }
}
