package eu.kanade.domain.easteregg.aurora

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Fix round 1 (план aurora-heart-full-fix, Q6 «откат выданного»): гейт отката
 * наград в debugReset. Чистый гейт [AuroraResetGate] (прецедент — AuroraOfferDedup)
 * + вывод его входов из РЕАЛЬНОГО AuroraQuest на [AuroraPrefsFake] (приём из
 * AuroraQuestGateTest): без выдачи отката нет; после ресета (prefs стёрты)
 * повторное нажатие тоже без дрейфа счётчиков; restore-кейс (DONE уцелел,
 * PAYLOAD утрачен) → откат выполняется с points=0.
 *
 * Injekt-часть debugReset (PointsManager/UserProfileManager/UnlockableManager/
 * ActivityDataRepository) без Android Context/DI юнит-не тестируема — покрыта
 * compile + grep (см. task-10-report.md, Fix round 1).
 */
class AuroraResetGateTest {

    // Таблица истинности гейта (контракт контроллера: payload != null || quest.isUnlocked)
    @Test
    fun gateTruthTableMatchesControllerContract() {
        AuroraResetGate.shouldRollbackRewards(unlocked = false, payloadPresent = false) shouldBe false
        AuroraResetGate.shouldRollbackRewards(unlocked = true, payloadPresent = false) shouldBe true
        AuroraResetGate.shouldRollbackRewards(unlocked = false, payloadPresent = true) shouldBe true
        AuroraResetGate.shouldRollbackRewards(unlocked = true, payloadPresent = true) shouldBe true
    }

    // Сценарий (a): выдачи никогда не было → счётчики НЕ декрементируются
    @Test
    fun freshStateBlocksRollback() {
        val quest = questWithPrefs(AuroraPrefsFake())

        val rollback = AuroraResetGate.shouldRollbackRewards(
            unlocked = quest.isUnlocked,
            payloadPresent = quest.unlockedPayload() != null,
        )

        rollback shouldBe false
    }

    // Сценарий (b): двойной reset — после стирания prefs (шаг 4 debugReset) второй пресс без дрейфа
    @Test
    fun stateAfterResetBlocksSecondRollback() {
        val prefs = AuroraPrefsFake(
            mapOf(
                AuroraPrefKeys.DONE to true,
                AuroraPrefKeys.PAYLOAD to "{\"kind\":\"final\",\"bonusPoints\":777}",
            ),
        )
        val quest = questWithPrefs(prefs)
        AuroraResetGate.shouldRollbackRewards(quest.isUnlocked, quest.unlockedPayload() != null) shouldBe true

        // Имитация шага 4 debugReset: DONE/PAYLOAD (и остальные ключи) удалены
        prefs.edit()
            .remove(AuroraPrefKeys.DONE)
            .remove(AuroraPrefKeys.PAYLOAD)
            .apply()

        AuroraResetGate.shouldRollbackRewards(quest.isUnlocked, quest.unlockedPayload() != null) shouldBe false
    }

    // Restore-кейс: DONE сохранён (Task 9 preserve), PAYLOAD утрачен → откат с points=0
    @Test
    fun restoreStateAllowsRollbackWithZeroPoints() {
        val quest = questWithPrefs(AuroraPrefsFake(mapOf(AuroraPrefKeys.DONE to true)))

        val payload = quest.unlockedPayload()
        AuroraResetGate.shouldRollbackRewards(quest.isUnlocked, payload != null) shouldBe true

        val reward = AuroraUnlockRewarder.aurora(points = payload?.bonusPoints ?: 0)
        reward.points shouldBe 0
    }

    // Обычная выдача жива: DONE + PAYLOAD с bonusPoints → откат с фактическими очками
    @Test
    fun liveUnlockStateAllowsRollbackWithPayloadPoints() {
        val quest = questWithPrefs(
            AuroraPrefsFake(
                mapOf(
                    AuroraPrefKeys.DONE to true,
                    AuroraPrefKeys.PAYLOAD to "{\"kind\":\"final\",\"bonusPoints\":777}",
                ),
            ),
        )

        val payload = quest.unlockedPayload()
        AuroraResetGate.shouldRollbackRewards(quest.isUnlocked, payload != null) shouldBe true
        AuroraUnlockRewarder.aurora(points = payload?.bonusPoints ?: 0).points shouldBe 777
    }

    private fun questWithPrefs(prefs: AuroraPrefsFake): AuroraQuest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return AuroraQuest(context)
    }
}
