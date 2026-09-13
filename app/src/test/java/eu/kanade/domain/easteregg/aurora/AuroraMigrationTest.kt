package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Task 9 (план aurora-heart-full-fix): чистая логика решения миграции B3.
 * Правило (дословно из Плана v2):
 * `storedRev == currentRev && (!unlocked || payloadHasThemeMaterial)` → noop;
 * иначе → wipe=true, preserveDoneAndPayload=unlocked, updateRev=true.
 */
class AuroraMigrationTest {

    private fun decide(rev: Int, cur: Int, unlocked: Boolean, material: Boolean) =
        AuroraMigration.decide(
            storedRev = rev,
            currentRev = cur,
            unlocked = unlocked,
            payloadHasThemeMaterial = material,
        )

    private val noop = AuroraMigration.Decision(wipe = false, preserveDoneAndPayload = false, updateRev = false)
    private val wipePreserve = AuroraMigration.Decision(wipe = true, preserveDoneAndPayload = true, updateRev = true)
    private val wipeAll = AuroraMigration.Decision(wipe = true, preserveDoneAndPayload = false, updateRev = true)

    // (a) rev совпал, квест не открыт → ничего не делать
    @Test
    fun sameRevAndNotUnlockedIsNoop() {
        decide(rev = 5, cur = 5, unlocked = false, material = false) shouldBe noop
    }

    // (b) rev совпал, открыт, payload с themeMaterial → актуально, ничего не делать
    @Test
    fun sameRevUnlockedWithMaterialIsNoop() {
        decide(rev = 5, cur = 5, unlocked = true, material = true) shouldBe noop
    }

    // (c) rev не совпал, открыт → wipe, НО preserve DONE+PAYLOAD (B3-restore)
    @Test
    fun revMismatchUnlockedWipesButPreservesDoneAndPayload() {
        decide(rev = 4, cur = 5, unlocked = true, material = true) shouldBe wipePreserve
    }

    // (d) rev не совпал, не открыт → полный wipe без preserve
    @Test
    fun revMismatchNotUnlockedWipesEverything() {
        decide(rev = 4, cur = 5, unlocked = false, material = false) shouldBe wipeAll
    }

    // (e) rev совпал, открыт, payload БЕЗ themeMaterial → wipe c preserve (устаревший payload допустим)
    @Test
    fun sameRevUnlockedWithoutMaterialWipesButPreserves() {
        decide(rev = 5, cur = 5, unlocked = true, material = false) shouldBe wipePreserve
    }

    // fresh install: stored=0 (дефолт prefs) → wipe без preserve
    @Test
    fun freshInstallStoredZeroWipesWithoutPreserve() {
        decide(rev = 0, cur = AuroraVaultData.VERSION, unlocked = false, material = false) shouldBe wipeAll
    }
}
