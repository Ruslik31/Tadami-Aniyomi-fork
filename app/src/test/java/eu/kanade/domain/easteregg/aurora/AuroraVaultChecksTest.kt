package eu.kanade.domain.easteregg.aurora

import android.util.Base64
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Task 15: проверки checks-схемы (Option C) БЕЗ единого литерала ответа ваулта.
 *
 *  (а) крипто-roundtrip на FIXTURE-ступенях ([AuroraTestStages]): canonical и каждый
 *      алиас вскрывают СВОЮ ступень и отдают ровно входной payload JSON; мусорная фраза,
 *      пустой ввод и фраза соседней ступени не вскрывают ничего; checks.size == data.size.
 *  (б) структурные ассерты на РЕАЛЬНОМ [AuroraVaultData]: 3 ступени, равная длина
 *      checks/data, строгий base64 у солей/хешей/срезов, непустая соль, VERSION > 0,
 *      две разные непустые формулировки первой загадки.
 *
 * Фикстура пишет base64 через java.util.Base64, production-[AuroraVault.tryOpen] читает
 * через android.util.Base64 — на JVM android.jar заглушка, поэтому статика переопределена
 * мостом mockkStatic → java.util (тот же приём, что в AuroraQuestGateTest).
 */
class AuroraVaultChecksTest {

    /** Фикстура с случайными солями/iv строится один раз на тест (JUnit — новый инстанс). */
    private val fixture: List<AuroraStage> by lazy {
        AuroraTestStages.buildStages(
            AuroraTestStages.StageSpec(phrase = CANONICAL_0, aliases = ALIASES_0, payloadJson = PAYLOAD_0),
            AuroraTestStages.StageSpec(phrase = CANONICAL_1, payloadJson = PAYLOAD_1),
        )
    }

    @BeforeEach
    fun stubAndroidBase64() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { Base64.encodeToString(any(), any<Int>()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
    }

    @AfterEach
    fun unstubAndroidBase64() {
        unmockkStatic(Base64::class)
    }

    // (а) крипто-roundtrip на фикстурных ступенях

    @Test
    fun canonicalFixturePhraseOpensItsStageWithExactPayload() {
        val plain = AuroraVault.tryOpen(CANONICAL_0, fixture[0])
        plain.shouldNotBeNull()
        plain.decodeToString() shouldBe PAYLOAD_0
    }

    @Test
    fun everyFixtureAliasOpensItsOwnStageWithExactPayload() {
        for (alias in ALIASES_0) {
            val plain = AuroraVault.tryOpen(alias, fixture[0])
            plain.shouldNotBeNull()
            plain.decodeToString() shouldBe PAYLOAD_0
        }
    }

    @Test
    fun wrongFixturePhraseOpensNothing() {
        AuroraVault.tryOpen(WRONG_PHRASE, fixture[0]).shouldBeNull()
        AuroraVault.tryOpen("   ", fixture[0]).shouldBeNull()
    }

    @Test
    fun fixturePhraseOfAnotherStageOpensNothing() {
        AuroraVault.tryOpen(CANONICAL_1, fixture[0]).shouldBeNull()
        AuroraVault.tryOpen(CANONICAL_0, fixture[1]).shouldBeNull()
        AuroraVault.tryOpen(ALIASES_0[0], fixture[1]).shouldBeNull()
    }

    @Test
    fun fixtureStageCarriesOneCheckPerPhrase() {
        fixture[0].checks.size shouldBe 1 + ALIASES_0.size
        fixture[0].data.size shouldBe fixture[0].checks.size
        fixture[1].checks.size shouldBe 1
        fixture[1].data.size shouldBe 1
    }

    // (б) структура реального ваулта — без опоры на ответы

    @Test
    fun realVaultHasThreeStages() {
        AuroraVaultData.STAGES.size shouldBe 3
    }

    @Test
    fun realVaultStageSlicesMatchTheirChecks() {
        for (stage in AuroraVaultData.STAGES) {
            stage.checks.isEmpty() shouldBe false
            stage.data.size shouldBe stage.checks.size
        }
    }

    @Test
    fun realVaultEntriesAreStrictBase64AndNonEmpty() {
        for (stage in AuroraVaultData.STAGES) {
            stage.salt.shouldNotBeBlank()
            decodeStrict(stage.salt, "salt").size shouldBe SALT_BYTES
            for (check in stage.checks) decodeStrict(check, "check").isEmpty() shouldBe false
            for (slice in stage.data) decodeStrict(slice, "data").isEmpty() shouldBe false
        }
    }

    @Test
    fun realVaultVersionIsPositive() {
        (AuroraVaultData.VERSION > 0) shouldBe true
    }

    @Test
    fun realVaultFirstRiddlesAreDistinctAndNotBlank() {
        AuroraVaultData.FIRST_RIDDLE.shouldNotBeBlank()
        AuroraVaultData.FIRST_RIDDLE_EN.shouldNotBeBlank()
        (AuroraVaultData.FIRST_RIDDLE == AuroraVaultData.FIRST_RIDDLE_EN) shouldBe false
    }

    /** Строгий декодер: переносы строк и мусор в алфавите — падение теста. */
    private fun decodeStrict(value: String, label: String): ByteArray = try {
        java.util.Base64.getDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
        throw AssertionError("vault $label entry is not strict base64 (length ${value.length})", e)
    }

    private companion object {
        /** Фикстурные фразы — нейтральные строки для roundtrip, НЕ ответы ваулта. */
        const val CANONICAL_0 = "fixture keeper phrase"
        val ALIASES_0 = listOf("fixture keeper alias one", "fixture keeper alias two")
        const val CANONICAL_1 = "fixture second stage phrase"
        const val WRONG_PHRASE = "fixture obviously wrong phrase"

        const val PAYLOAD_0 = AuroraTestStages.TEST_PAYLOAD_JSON
        const val PAYLOAD_1 = "{\"kind\":\"riddle\",\"riddle\":\"fixture riddle two\"," +
            "\"riddleEn\":\"fixture riddle two EN\",\"echoTitle\":\"fixture echo two\"}"

        /** Длина соли у forge — 16 байт. */
        const val SALT_BYTES = 16
    }
}
