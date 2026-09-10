package eu.kanade.domain.easteregg.aurora

import android.content.Context
import android.util.Base64
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Гейты [AuroraQuest.offer] (план aurora-heart-full-fix, Task 2):
 * B2 — до [AuroraQuest.revealHint] пасхалка молчит; L7 — чистая функция
 * in-session дедупа нормализованных запросов. Ваулт реальный: демо-ответы
 * до Task 13 — ступень 0 открывает «час волка», алиас «3 am» идёт через
 * checks-схему Option C (per-phrase срезы, Task 13).
 *
 * JVM-особенности: android.util.Base64 — заглушка android.jar, поэтому
 * статика переопределена mockkStatic на java.util.Base64 (приём из
 * NovelJsSourceTest / BaseHomeHubScreenModelTest); Context — mockk,
 * отдающий [AuroraPrefsFake] (реальный конструктор AuroraQuest не меняется).
 */
class AuroraQuestGateTest {

    private val finalPayloadJson = "{\"kind\":\"final\"}"

    @BeforeEach
    fun stubAndroidBase64() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        // Task 13: tryOpen сравнивает b64(sha256(key)) с checks — encodeToString тоже мокается
        every { Base64.encodeToString(any(), any<Int>()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
    }

    @AfterEach
    fun unstubAndroidBase64() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun offerWithoutRevealedHintReturnsNullAndKeepsStage() {
        val prefs = AuroraPrefsFake()
        val quest = questWithPrefs(prefs)

        val echo = quest.offer("час волка")

        echo shouldBe null
        quest.currentStageIndex shouldBe 0
        prefs.contains(AuroraPrefKeys.STAGE) shouldBe false
    }

    @Test
    fun offerAfterRevealHintOpensStageZero() {
        val prefs = AuroraPrefsFake()
        val quest = questWithPrefs(prefs)
        quest.revealHint()

        val echo = quest.offer("час волка")

        val progress = echo.shouldBeInstanceOf<AuroraEcho.Progress>()
        progress.stageIndex shouldBe 1
        progress.totalStages shouldBe AuroraVaultData.STAGES.size
        quest.currentStageIndex shouldBe 1
        prefs.getInt(AuroraPrefKeys.STAGE, 0) shouldBe 1
    }

    @Test
    fun offerWhenUnlockedAlwaysReturnsNull() {
        // Состояние пишет финальная ветка offer() (AuroraQuest :76-83): DONE=true + PAYLOAD=JSON.
        val prefs = AuroraPrefsFake(
            mapOf(
                AuroraPrefKeys.HINT to true,
                AuroraPrefKeys.DONE to true,
                AuroraPrefKeys.PAYLOAD to finalPayloadJson,
            ),
        )
        val quest = questWithPrefs(prefs)
        quest.isUnlocked shouldBe true

        quest.offer("час волка") shouldBe null
        quest.offer("sigil:2-5-8") shouldBe null
        quest.currentStageIndex shouldBe 0
    }

    @Test
    fun aliasThreeAmOpensStageZeroAfterRevealHint() {
        // Task 13: alias-таблицы в normalize больше нет — «3 am» открывает ступень 0
        // через checks-схему (срез data[idx] под своим PBKDF2-ключом). Поведение то же.
        val prefs = AuroraPrefsFake()
        val quest = questWithPrefs(prefs)
        quest.revealHint()

        val echo = quest.offer("3 am")

        val progress = echo.shouldBeInstanceOf<AuroraEcho.Progress>()
        progress.stageIndex shouldBe 1
        prefs.getInt(AuroraPrefKeys.STAGE, 0) shouldBe 1
    }

    @Test
    fun dedupSkipsOnlyRepeatedNormalizedPhrase() {
        // Контракт L7: first → не skip; тот же normalized → skip; другой → не skip; null last → не skip.
        AuroraOfferDedup.shouldSkip(lastNormalized = null, candidateNormalized = "час волка") shouldBe false
        AuroraOfferDedup.shouldSkip(lastNormalized = "час волка", candidateNormalized = "час волка") shouldBe true
        AuroraOfferDedup.shouldSkip(lastNormalized = "час волка", candidateNormalized = "sigil:2-5-8") shouldBe false
        AuroraOfferDedup.shouldSkip(lastNormalized = "час волка", candidateNormalized = null) shouldBe false
    }

    @Test
    fun dedupTreatsWhitespaceAndCaseVariantsAsSamePhrase() {
        // Task 13: alias-таблица удалена — normalize делает только unicode-схлопывание;
        // кейс/пробелы — та же фраза.
        val last = AuroraVault.normalize("ЧАС   ВОЛКА")
        AuroraOfferDedup.shouldSkip(last, AuroraVault.normalize("час волка")) shouldBe true
        // Разные фразы (canonical и alias) теперь нормализуются РАЗНО — дедуп их не склеивает
        AuroraOfferDedup.shouldSkip(AuroraVault.normalize("3 am"), AuroraVault.normalize("час волка")) shouldBe false
    }

    private fun questWithPrefs(prefs: AuroraPrefsFake): AuroraQuest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return AuroraQuest(context)
    }
}
