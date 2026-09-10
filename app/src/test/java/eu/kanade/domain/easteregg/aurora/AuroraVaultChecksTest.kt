package eu.kanade.domain.easteregg.aurora

import android.util.Base64
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Task 13 (Option C — per-phrase срезы): checks-схема на реальном демо-ваулте.
 * Ступень открывает canonical-ответ И каждый алиас (свой срез data[idx]);
 * неверный ответ не матчит ни один check. Демо-ответы публично скомпрометированы
 * (см. --release гард) — их присутствие в тестах секретность не нарушает.
 * Base64-моки — приём AuroraQuestGateTest (android.jar — заглушка на JVM).
 */
class AuroraVaultChecksTest {

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

    @Test
    fun canonicalAnswerOpensStageZero() {
        val plain = AuroraVault.tryOpen("час волка", AuroraVaultData.STAGES[0])
        plain.shouldNotBeNull()
        plain.decodeToString().contains("\"kind\":\"riddle\"") shouldBe true
    }

    @Test
    fun aliasOpensStageZeroThroughChecks() {
        AuroraVault.tryOpen("3 am", AuroraVaultData.STAGES[0]).shouldNotBeNull()
        AuroraVault.tryOpen("Hour of the Wolf", AuroraVaultData.STAGES[0]).shouldNotBeNull()
    }

    @Test
    fun wrongAnswerMatchesNoCheck() {
        AuroraVault.tryOpen("wrong answer", AuroraVaultData.STAGES[0]) shouldBe null
        AuroraVault.tryOpen("час волка", AuroraVaultData.STAGES[1]) shouldBe null
    }

    @Test
    fun stageZeroCarriesCanonicalPlusScenarioAliases() {
        val stage = AuroraVaultData.STAGES[0]
        // 1 canonical + 13 aliases (scenario.json, отчёт Task 12 — «13/13 перенесены»)
        stage.checks.size shouldBe 14
        stage.data.size shouldBe stage.checks.size
        // Ступени без алиасов: единственный срез
        AuroraVaultData.STAGES[1].checks.size shouldBe 1
        AuroraVaultData.STAGES[2].checks.size shouldBe 1
    }
}
