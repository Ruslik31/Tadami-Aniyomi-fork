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
 * Гейты [AuroraQuest.offer] (план aurora-heart-full-fix):
 *  (a) B2 — до [AuroraQuest.revealHint] пасхалка молчит;
 *  (b) после revealHint правильная фраза продвигает ступень;
 *  (c) после разблокировки offer всегда null;
 *  (d) L7 — чистая функция in-session дедупа нормализованных запросов;
 *  (e) алиас фразы открывает ту же ступень (checks-схема Option C).
 *
 * Task 15: квест собирается через internal-seam конструктор на ФИКСТУРНЫХ
 * ступенях [AuroraTestStages] — тесты не содержат ни одного ответа реального
 * ваулта (ни демо, ни приватного) и переживают любую перековку сценария.
 *
 * JVM-особенности: android.util.Base64 — заглушка android.jar, поэтому
 * статика переопределена mockkStatic на java.util.Base64 (приём из
 * NovelJsSourceTest / BaseHomeHubScreenModelTest); Context — mockk,
 * отдающий [AuroraPrefsFake].
 */
class AuroraQuestGateTest {

    private val finalPayloadJson = "{\"kind\":\"final\"}"

    /** Две fixture-ступени: у нулевой — canonical + алиас; соли/iv случайные. */
    private val fixture: List<AuroraStage> by lazy {
        AuroraTestStages.buildStages(
            AuroraTestStages.StageSpec(
                phrase = CANONICAL_0,
                aliases = listOf(ALIAS_0),
                payloadJson = PAYLOAD_0,
            ),
            AuroraTestStages.StageSpec(phrase = CANONICAL_1, payloadJson = PAYLOAD_1),
        )
    }

    @BeforeEach
    fun stubAndroidBase64() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        // tryOpen сравнивает b64(sha256(key)) с checks — encodeToString тоже мокается
        every { Base64.encodeToString(any(), any<Int>()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
    }

    @AfterEach
    fun unstubAndroidBase64() {
        unmockkStatic(Base64::class)
    }

    // (a) молчание до раскрытия подсказки

    @Test
    fun offerWithoutRevealedHintReturnsNullAndKeepsStage() {
        val prefs = AuroraPrefsFake()
        val quest = questWithPrefs(prefs)

        val echo = quest.offer(CANONICAL_0)

        echo shouldBe null
        quest.currentStageIndex shouldBe 0
        prefs.contains(AuroraPrefKeys.STAGE) shouldBe false
    }

    // (b) верная fixture-фраза продвигает ступень после revealHint

    @Test
    fun offerAfterRevealHintOpensStageZero() {
        val prefs = AuroraPrefsFake()
        val quest = questWithPrefs(prefs)
        quest.revealHint()

        val echo = quest.offer(CANONICAL_0)

        val progress = echo.shouldBeInstanceOf<AuroraEcho.Progress>()
        progress.stageIndex shouldBe 1
        progress.totalStages shouldBe fixture.size
        quest.currentStageIndex shouldBe 1
        prefs.getInt(AuroraPrefKeys.STAGE, 0) shouldBe 1
    }

    // (c) пройденный квест больше не реагирует ни на какую фразу

    @Test
    fun offerWhenUnlockedAlwaysReturnsNull() {
        // Состояние пишет финальная ветка offer(): DONE=true + PAYLOAD=JSON.
        val prefs = AuroraPrefsFake(
            mapOf(
                AuroraPrefKeys.HINT to true,
                AuroraPrefKeys.DONE to true,
                AuroraPrefKeys.PAYLOAD to finalPayloadJson,
            ),
        )
        val quest = questWithPrefs(prefs)
        quest.isUnlocked shouldBe true

        quest.offer(CANONICAL_0) shouldBe null
        quest.offer(ALIAS_0) shouldBe null
        quest.currentStageIndex shouldBe 0
    }

    // (e) алиас fixture-фразы открывает ту же ступень (срез под своим ключом)

    @Test
    fun aliasOpensStageZeroAfterRevealHint() {
        val prefs = AuroraPrefsFake()
        val quest = questWithPrefs(prefs)
        quest.revealHint()

        val echo = quest.offer(ALIAS_0)

        val progress = echo.shouldBeInstanceOf<AuroraEcho.Progress>()
        progress.stageIndex shouldBe 1
        prefs.getInt(AuroraPrefKeys.STAGE, 0) shouldBe 1
    }

    // (d) дедуп запросов — чистая функция на нормализованных строках

    @Test
    fun dedupSkipsOnlyRepeatedNormalizedPhrase() {
        // Контракт L7: first → не skip; тот же normalized → skip; другой → не skip; null last → не skip.
        AuroraOfferDedup.shouldSkip(lastNormalized = null, candidateNormalized = PHRASE_A) shouldBe false
        AuroraOfferDedup.shouldSkip(lastNormalized = PHRASE_A, candidateNormalized = PHRASE_A) shouldBe true
        AuroraOfferDedup.shouldSkip(lastNormalized = PHRASE_A, candidateNormalized = PHRASE_B) shouldBe false
        AuroraOfferDedup.shouldSkip(lastNormalized = PHRASE_A, candidateNormalized = null) shouldBe false
    }

    @Test
    fun dedupTreatsWhitespaceAndCaseVariantsAsSamePhrase() {
        // Alias-таблица в normalize удалена — только unicode-схлопывание; кейс/пробелы — та же фраза.
        val last = AuroraVault.normalize("FIXTURE   QUEST PHRASE")
        AuroraOfferDedup.shouldSkip(last, AuroraVault.normalize("fixture quest phrase")) shouldBe true
        // Разные фразы (canonical и алиас) нормализуются РАЗНО — дедуп их не склеивает
        AuroraOfferDedup.shouldSkip(
            AuroraVault.normalize("fixture quest alias"),
            AuroraVault.normalize("fixture quest phrase"),
        ) shouldBe false
    }

    private fun questWithPrefs(prefs: AuroraPrefsFake): AuroraQuest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return AuroraQuest(context, fixture, FIXTURE_RIDDLE, FIXTURE_RIDDLE_EN)
    }

    private companion object {
        /** Fixture-фразы — нейтральные строки для seam-тестов, НЕ ответы ваулта. */
        const val CANONICAL_0 = "fixture keeper phrase"
        const val ALIAS_0 = "fixture keeper alias"
        const val CANONICAL_1 = "fixture second stage phrase"

        const val PHRASE_A = "fixture quest phrase"
        const val PHRASE_B = "fixture other phrase"

        const val PAYLOAD_0 = AuroraTestStages.TEST_PAYLOAD_JSON
        const val PAYLOAD_1 = "{\"kind\":\"riddle\",\"riddle\":\"fixture riddle two\"," +
            "\"riddleEn\":\"fixture riddle two EN\",\"echoTitle\":\"fixture echo two\"}"

        const val FIXTURE_RIDDLE = "fixture first riddle"
        const val FIXTURE_RIDDLE_EN = "fixture first riddle EN"
    }
}
