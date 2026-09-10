package eu.kanade.domain.easteregg.aurora

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TimeZone

/** Наблюдаемое состояние квеста для UI (без секретов). */
data class AuroraHeartState(
    val hintRevealed: Boolean,
    val stageIndex: Int,
    val totalStages: Int,
    val unlocked: Boolean,
)

/**
 * ЕДИНСТВЕННАЯ продакшн-точка доступа к квесту (Pillar 6 плана):
 *  - синглтон (зарегистрируй в Injekt/DI или бери через get());
 *  - StateFlow состояния — UI больше НЕ читает SharedPreferences напрямую;
 *  - offer() всегда на IO + mutex + rate-limit (PBKDF2 намеренно дорог);
 *  - версионирование ваулта: при замене AuroraVaultData прогресс
 *    мягко сбрасывается (без крашей и призрачных состояний);
 *  - ночной счётчик + одноразовый «шёпот» (Pillar 2, деликатная
 *    обнаружимость без спойлеров);
 *  - Кодекс — журнал пройденных эх для AuroraCodexScreen.
 */
class AuroraHeartManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val quest = AuroraQuest(appContext)
    private val prefs = appContext.getSharedPreferences(AuroraPrefKeys.STORE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val cryptoMutex = Mutex()

    @Volatile
    private var lastAttemptAt = 0L

    /** In-session дедуп (L7): последняя обработанная нормализованная фраза. В памяти, НЕ в prefs. */
    @Volatile
    private var lastOfferNormalized: String? = null

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AuroraHeartState> = _state

    init {
        attachEchoPersistence()
        migrateIfNeeded()
    }

    /**
     * Task 11 (B1): персистентная очередь эхо шины на AuroraPrefKeys.PENDING.
     * Подключение в init синглтона — строго ДО любых emit-путей: все эмиттеры
     * (offer/revealHintCore/registerNightAction) — методы экземпляра, а экземпляр
     * публикуется (Injekt/companion) только после конструирования. Гонки инициализации нет.
     */
    private fun attachEchoPersistence() {
        AuroraEchoBus.loader = { prefs.getString(AuroraPrefKeys.PENDING, null) }
        AuroraEchoBus.persister = { json -> prefs.edit().putString(AuroraPrefKeys.PENDING, json).apply() }
    }

    /** Ядро открытия хинта БЕЗ шёпота: quest.revealHint() + Progress-эхо «Зов Авроры» + refresh. */
    private fun revealHintCore() {
        val wasRevealed = quest.isHintRevealed
        quest.revealHint()
        if (!wasRevealed && quest.isHintRevealed) {
            refresh()
            // Дать видимую обратную связь: вспышка при первом открытии хинта (от чтения ранобэ и т.п.)
            val current = quest.currentRiddle()
            if (current != null) {
                AuroraEchoBus.emit(
                    AuroraEcho.Progress(
                        stageIndex = 0,
                        totalStages = quest.totalStagesCount,
                        echoTitle = "Зов Авроры",
                    ),
                )
            }
        } else {
            refresh()
        }
    }

    fun currentRiddle(): AuroraRiddleText? = quest.currentRiddle()

    fun unlockedPayload(): AuroraPayload? = quest.unlockedPayload()

    /** Первая загадка ваулта (RU+EN, Task 13); локаль-выбор — [AuroraRiddleText.display]. */
    fun firstRiddle(): AuroraRiddleText =
        AuroraRiddleText(AuroraVaultData.FIRST_RIDDLE, AuroraVaultData.FIRST_RIDDLE_EN)

    /** Журнал пройденных эх (для Кодекса). Пуст, пока нет прогресса. */
    fun codex(): List<AuroraCodexEntry> = runCatching {
        json.decodeFromString<List<AuroraCodexEntry>>(prefs.getString(AuroraPrefKeys.ECHOES, null) ?: "[]")
    }.getOrDefault(emptyList())

    /**
     * Единая точка проверки фраз из ВСЕХ каналов.
     * Всегда IO + mutex; попытки чаще чем раз в 250 мс отбрасываются
     * (анти-флуд дорогого PBKDF2), а повтор последней обработанной
     * нормализованной фразы не доходит до quest.offer/ваулта вовсе
     * (in-session дедуп L7 — смены фильтра/пагинация с тем же запросом).
     * Вызывать из любой корутины.
     */
    suspend fun offer(phrase: String): AuroraEcho? {
        if (phrase.length !in 3..64) return null
        val normalized = AuroraVault.normalize(phrase)
        if (AuroraOfferDedup.shouldSkip(lastOfferNormalized, normalized)) return null
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttemptAt < MIN_ATTEMPT_INTERVAL_MS) return null
        lastAttemptAt = now
        val echo = cryptoMutex.withLock {
            withContext(Dispatchers.IO) { quest.offer(phrase) }
        }
        lastOfferNormalized = normalized
        if (echo != null) refresh()
        return echo
    }

    /**
     * Отметка «ночного действия» (прочитана глава / досмотрена серия
     * в окне AuroraNight). Ночи считаются УНИКАЛЬНЫЕ: не больше одной
     * за локальные сутки (AuroraNights). После WHISPER_THRESHOLD ночей —
     * один раз за всю жизнь устройства — в шину уходит тихий шёпот без
     * ответов и открывается хинт (единственным шёпотом ночи, без коллизии).
     */
    fun registerNightAction() {
        if (quest.isUnlocked || quest.isHintRevealed) return
        val adv = AuroraNights.advance(
            lastNightKey = prefs.getString(AuroraPrefKeys.LAST_NIGHT, null),
            todayKey = todayKey(),
            currentCount = prefs.getInt(AuroraPrefKeys.NIGHT, 0),
        )
        if (!adv.isNewNight) {
            refresh()
            return
        }
        prefs.edit()
            .putInt(AuroraPrefKeys.NIGHT, adv.count)
            .putString(AuroraPrefKeys.LAST_NIGHT, adv.lastNightKey)
            .apply()
        if (adv.count >= WHISPER_THRESHOLD && !prefs.getBoolean(AuroraPrefKeys.WHISPER, false)) {
            // Единственный шёпот ночи: пороговый текст, затем хинт БЕЗ второго шёпота (анти-коллизия)
            AuroraEchoBus.emitWhisper("Некоторые границы тоньше других.")
            revealHintCore()
            // T3#1: флаг ПОСЛЕ emit/hint — смерть процесса = пропущенный шёпот (хинт открыт), а не вечный блок ветки
            prefs.edit().putBoolean(AuroraPrefKeys.WHISPER, true).apply()
        }
        refresh()
    }

    /** Ключ локальных суток без java.time (desugaring не подтверждён): UTC-мс + смещение TimeZone. */
    private fun todayKey(): String {
        val now = System.currentTimeMillis()
        val localMillis = now + TimeZone.getDefault().getOffset(now)
        return (localMillis / 86_400_000L).toString()
    }

    fun refresh() {
        _state.value = readState()
    }

    // Centralized presentation state per strategist recommendation for AC1/AC2
    sealed class RiddlePresentation {
        object None : RiddlePresentation()

        /** [riddle] — текст, уже резолвленный по локали ([AuroraRiddleText.display], Task 13). */
        data class Show(val riddle: String, val stageIndex: Int, val totalStages: Int) : RiddlePresentation()
    }

    private val _presentation = MutableStateFlow<RiddlePresentation>(RiddlePresentation.None)
    val presentation: StateFlow<RiddlePresentation> = _presentation

    fun onFlashFinished(echo: AuroraEcho.Progress) {
        // After flash for a solved stage (echo reports the advanced index), show the *current pending* riddle
        // using live quest state so dots and label match the next layer. Only auto-show if more stages.
        val cur = quest.currentStageIndex
        if (cur < echo.totalStages) {
            val r = currentRiddle() ?: return
            _presentation.value = RiddlePresentation.Show(r.display(), cur, echo.totalStages)
            markCurrentStageRiddleAutoShown()
        }
    }

    fun dismissRiddle() {
        _presentation.value = RiddlePresentation.None
    }

    fun requestAutoShowForAchievements() {
        val r = currentRiddle() ?: return
        _presentation.value = RiddlePresentation.Show(r.display(), quest.currentStageIndex, quest.totalStagesCount)
        markCurrentStageRiddleAutoShown() // one-time for manual browse path (continuation marks in onFlash)
    }

    /**
     * Полный сброс прогресса пасхалки «Сердце Авроры».
     * Доступно только в debug-сборках (кнопка на экране «Еще»; гейтинг
     * BuildConfig.DEBUG — на вызывающей стороне MoreTab, он не меняется).
     * Сбрасывает:
     * - награды финала (Q6 ПОЛНЫЙ откат): XP, счётчик разблокированных,
     *   тема профиля, unlockables, activity-счётчик дня;
     * - состояние квеста (стадии, загадки);
     * - кодекс эхо;
     * - счётчик ночных действий (NIGHT + LAST_NIGHT — T3-note);
     * - шёпоты;
     * - in-session дедуп (lastOfferNormalized — T2-minor#3) и riddle-presentation.
     * Состав награды — из единого источника AuroraUnlockRewarder.aurora(...)
     * (Task 8); points — из payload.bonusPoints, прочитанного ДО удаления prefs.
     * Откат наград — ЗА гейтом AuroraResetGate «а была ли выдача» (fix round 1):
     * холостой/повторный ресет счётчики не трогает; прочие шаги идемпотентны.
     * Activity-откат аппроксимирован: декрементит счётчик СЕГОДНЯШНЕГО дня
     * (guard ≥0), а не дня выдачи; отметка level=4 не откатывается.
     * Удаление achievement-ряда — ВНЕШНЕЕ (MoreTab: deleteAchievement),
     * здесь не дублируется. Вызов — из корутины (MoreTab launchIO).
     */
    suspend fun debugReset() {
        // 1) Откат наград — ДО удаления payload (нужен состав/bonusPoints).
        val payload = quest.unlockedPayload()
        // Fix round 1 (Q6 «откат выданного»): гейт «а была ли выдача» — холостой и
        // повторные ресеты не дрейфят achievements_unlocked/activity-счётчики.
        if (AuroraResetGate.shouldRollbackRewards(quest.isUnlocked, payload != null)) {
            val reward = AuroraUnlockRewarder.aurora(points = payload?.bonusPoints ?: 0)
            val pointsManager = Injekt.get<PointsManager>()
            pointsManager.subtractPoints(reward.points)
            pointsManager.decrementAchievementUnlocked()
            reward.themeId?.let { Injekt.get<UserProfileManager>().removeTheme(it) }
            val unlockableManager = Injekt.get<UnlockableManager>()
            reward.unlockableIds.forEach { unlockableManager.removeUnlockable(it) }
            Injekt.get<ActivityDataRepository>().decrementAchievementUnlock()
        }

        // 2) Скрыть открытую загадку.
        _presentation.value = RiddlePresentation.None
        // 3) Сбросить in-session дедуп (Task 2).
        lastOfferNormalized = null

        // 4) Все prefs-ключи пасхалки (LAST_NIGHT — T3-note; PENDING — безусловно).
        prefs.edit()
            .remove(AuroraPrefKeys.HINT)
            .remove(AuroraPrefKeys.STAGE)
            .remove(AuroraPrefKeys.RIDDLE)
            .remove(AuroraPrefKeys.DONE)
            .remove(AuroraPrefKeys.PAYLOAD)
            .remove(AuroraPrefKeys.ECHOES)
            .remove(AuroraPrefKeys.NIGHT)
            .remove(AuroraPrefKeys.LAST_NIGHT)
            .remove(AuroraPrefKeys.WHISPER)
            .remove(AuroraPrefKeys.VER)
            .remove(AuroraPrefKeys.SHOWN)
            .remove(AuroraPrefKeys.PENDING)
            .apply()

        // Сбросим внутреннее состояние квеста, чтобы currentRiddle() сразу вернул null
        lastAttemptAt = 0L
        // 5) Синхронизировать UI-состояние.
        refresh()
    }

    /** Возвращает true, если для текущей стадии ещё не было авто-открытия загадки (один раз на этап). */
    fun shouldAutoShowRiddleForCurrentStage(): Boolean {
        val stage = quest.currentStageIndex
        val shown = prefs.getString(AuroraPrefKeys.SHOWN, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()
        return stage !in shown
    }

    /** Пометить текущую стадию как "авто-открыто один раз". */
    fun markCurrentStageRiddleAutoShown() {
        val stage = quest.currentStageIndex
        val shown = prefs.getString(AuroraPrefKeys.SHOWN, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toMutableSet() ?: mutableSetOf()
        shown += stage
        prefs.edit().putString(AuroraPrefKeys.SHOWN, shown.joinToString(",")).apply()
    }

    private fun readState() = AuroraHeartState(
        hintRevealed = quest.isHintRevealed,
        stageIndex = quest.currentStageIndex,
        totalStages = quest.totalStagesCount,
        unlocked = quest.isUnlocked,
    )

    /**
     * Версионирование ваулта: VERSION генерируется форжем из контрольных
     * хешей ступеней. Если хранилище заменили — старый прогресс невалиден
     * (другие загадки/ответы) и мягко сбрасывается. Больше не нужно
     * чистить данные приложения вручную при перегенерации сценария.
     *
     * Task 9 (B3): решение — через чистую AuroraMigration.decide. При wipe
     * уже открытого квеста DONE/PAYLOAD НЕ удаляются (restore не должен
     * превращать заработанное достижение в «???»); LAST_NIGHT стирается
     * вместе с прочими ночными ключами. Achievement-DB/XP здесь не трогаются.
     * Fix round 1 (перенос из Task 11): PENDING тоже стирается при wipe —
     * после смены VERSION ваулта в очереди не должно остаться эхо со старыми
     * stageIndex/echoTitle (preserve DONE/PAYLOAD не затрагивает).
     */
    private fun migrateIfNeeded() {
        val payload = quest.unlockedPayload()
        val decision = AuroraMigration.decide(
            storedRev = prefs.getInt(AuroraPrefKeys.VER, 0),
            currentRev = AuroraVaultData.VERSION,
            unlocked = quest.isUnlocked,
            payloadHasThemeMaterial = payload?.themeMaterial != null,
        )
        if (!decision.wipe) return
        val editor = prefs.edit()
            .remove(AuroraPrefKeys.HINT)
            .remove(AuroraPrefKeys.STAGE)
            .remove(AuroraPrefKeys.RIDDLE)
            .remove(AuroraPrefKeys.ECHOES)
            .remove(AuroraPrefKeys.NIGHT)
            .remove(AuroraPrefKeys.LAST_NIGHT)
            .remove(AuroraPrefKeys.WHISPER)
            .remove(AuroraPrefKeys.SHOWN)
            .remove(AuroraPrefKeys.PENDING)
        if (!decision.preserveDoneAndPayload) {
            editor.remove(AuroraPrefKeys.DONE)
                .remove(AuroraPrefKeys.PAYLOAD)
        }
        if (decision.updateRev) {
            editor.putInt(AuroraPrefKeys.VER, AuroraVaultData.VERSION)
        }
        editor.apply()
        refresh()
    }

    companion object {
        @Volatile
        private var instance: AuroraHeartManager? = null

        /** Синглтон. В проекте лучше зарегистрировать в DI (см. ШАГ 15). */
        fun get(context: Context): AuroraHeartManager =
            instance ?: synchronized(this) {
                instance ?: AuroraHeartManager(context).also { instance = it }
            }

        private const val MIN_ATTEMPT_INTERVAL_MS = 250L
        private const val WHISPER_THRESHOLD = 3
    }
}

/**
 * Чистая функция in-session дедупа (L7): повторный запрос, чья нормализация
 * равна последней обработанной, отбрасывается до quest.offer/ваулта —
 * экономия PBKDF2-120k на сменах фильтра и пагинации с тем же текстом.
 * Пропуск повтора верного ответа безопасен: ответы уникальны по стадиям
 * (валидирует forge). Normalize дёшев (без PBKDF2) — звать на проверке можно.
 */
internal object AuroraOfferDedup {
    fun shouldSkip(lastNormalized: String?, candidateNormalized: String?): Boolean =
        candidateNormalized != null && candidateNormalized == lastNormalized
}

/**
 * Чистый гейт отката наград debugReset (fix round 1, Q6 «откат выданного»):
 * откатывать ТОЛЬКО если пасхалка реально открывалась на этом устройстве —
 * квест открыт (DONE) ИЛИ payload жив. Кнопка ресета в MoreTab не гейтится
 * состоянием пасхалки, поэтому без гейта холостой ресет и каждый повторный
 * пресс дрейфили achievements_unlocked/activity на −1. После ресета оба входа
 * false (prefs стёрты) → повтор безопасен. Restore-кейс (DONE уцелел через
 * preserve Task 9, PAYLOAD утрачен) → откат разрешён, points=0 безопасен
 * (subtractPoints игнорирует ≤0, как и гард выдачи Task 8).
 */
internal object AuroraResetGate {
    fun shouldRollbackRewards(unlocked: Boolean, payloadPresent: Boolean): Boolean =
        unlocked || payloadPresent
}
