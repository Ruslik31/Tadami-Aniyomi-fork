package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository

/**
 * Task 8 (план aurora-heart-full-fix): идемпотентная выдача награды K1 + Q8-lattice.
 *
 * wasUnlocked-гейт по образцу AchievementCalculator: повторный grant (реплей финала
 * из AuroraCodexScreen / повторный сигнал lattice) НЕ должен реплеить XP/счётчики —
 * только идемпотентная косметика (защита от restore-потерь).
 *
 * Зафиксировано (c): points=0 → addPoints не вызывается вовсе (гард PointsManager «>0»).
 */
class AuroraUnlockRewarderTest {

    private val achievementRepository: AchievementRepository = mockk()
    private val pointsManager: PointsManager = mockk()
    private val unlockableManager: UnlockableManager = mockk()
    private val userProfileManager: UserProfileManager = mockk()
    private val activityDataRepository: ActivityDataRepository = mockk()

    private val rewarder = AuroraUnlockRewarder(
        achievementRepository = achievementRepository,
        pointsManager = pointsManager,
        unlockableManager = unlockableManager,
        userProfileManager = userProfileManager,
        activityDataRepository = activityDataRepository,
    )

    @BeforeEach
    fun setup() {
        coJustRun { achievementRepository.insertOrUpdateProgress(any()) }
        coJustRun { pointsManager.addPoints(any()) }
        coJustRun { pointsManager.incrementUnlocked() }
        coJustRun { activityDataRepository.recordAchievementUnlock() }
        coJustRun { userProfileManager.unlockTheme(any()) }
        justRun { unlockableManager.setUnlockableUnlocked(any()) }
    }

    private fun stubProgress(vararg rows: AchievementProgress) {
        coEvery { achievementRepository.getAllProgress() } returns flowOf(rows.toList())
    }

    // (a) fresh: progress-ряда нет → полный путь, каждый эффект ровно 1×
    @Test
    fun freshGrantWithoutRowReplaysFullRewardOnce() = runTest {
        stubProgress()

        val granted = rewarder.grant(AuroraUnlockRewarder.aurora(points = 777))

        granted shouldBe true
        val progress = slot<AchievementProgress>()
        coVerify(exactly = 1) { achievementRepository.insertOrUpdateProgress(capture(progress)) }
        progress.captured.achievementId shouldBe "aurora_heart"
        progress.captured.isUnlocked shouldBe true
        progress.captured.progress shouldBe 1
        progress.captured.maxProgress shouldBe 1
        progress.captured.unlockedAt shouldNotBe null
        coVerify(exactly = 1) { pointsManager.addPoints(777) }
        coVerify(exactly = 1) { pointsManager.incrementUnlocked() }
        coVerify(exactly = 1) { activityDataRepository.recordAchievementUnlock() }
        coVerify(exactly = 1) { userProfileManager.unlockTheme("AURORA_PRIME") }
        verify(exactly = 1) { unlockableManager.setUnlockableUnlocked("theme_AURORA_PRIME") }
        verify(exactly = 1) {
            unlockableManager.setUnlockableUnlocked("special_navbar_aurora_celestial")
        }
    }

    // (a2) fresh: ряд есть, но isUnlocked=false → тоже полный путь
    @Test
    fun freshGrantWithLockedRowReplaysFullRewardOnce() = runTest {
        stubProgress(AchievementProgress(achievementId = "aurora_heart", isUnlocked = false))

        rewarder.grant(AuroraUnlockRewarder.aurora(points = 777)) shouldBe true

        coVerify(exactly = 1) { achievementRepository.insertOrUpdateProgress(any()) }
        coVerify(exactly = 1) { pointsManager.addPoints(777) }
        coVerify(exactly = 1) { pointsManager.incrementUnlocked() }
        coVerify(exactly = 1) { activityDataRepository.recordAchievementUnlock() }
    }

    // (b) already unlocked: ядро K1 — ноль XP-реплеев, косметика идемпотентно 1×
    @Test
    fun repeatGrantAfterUnlockSkipsXpAndReappliesCosmeticsOnly() = runTest {
        stubProgress(
            AchievementProgress(achievementId = "aurora_heart", isUnlocked = true, unlockedAt = 1L),
        )

        val granted = rewarder.grant(AuroraUnlockRewarder.aurora(points = 777))

        granted shouldBe false
        coVerify(exactly = 0) { achievementRepository.insertOrUpdateProgress(any()) }
        coVerify(exactly = 0) { pointsManager.addPoints(any()) }
        coVerify(exactly = 0) { pointsManager.incrementUnlocked() }
        coVerify(exactly = 0) { activityDataRepository.recordAchievementUnlock() }
        coVerify(exactly = 1) { userProfileManager.unlockTheme("AURORA_PRIME") }
        verify(exactly = 1) { unlockableManager.setUnlockableUnlocked("theme_AURORA_PRIME") }
        verify(exactly = 1) {
            unlockableManager.setUnlockableUnlocked("special_navbar_aurora_celestial")
        }
    }

    // (c) points=0: addPoints не вызывается вовсе (зафиксированное поведение)
    @Test
    fun zeroPointsFreshGrantDoesNotCallAddPoints() = runTest {
        stubProgress()

        rewarder.grant(AuroraUnlockRewarder.aurora(points = 0)) shouldBe true

        coVerify(exactly = 0) { pointsManager.addPoints(any()) }
        coVerify(exactly = 1) { achievementRepository.insertOrUpdateProgress(any()) }
        coVerify(exactly = 1) { pointsManager.incrementUnlocked() }
        coVerify(exactly = 1) { activityDataRepository.recordAchievementUnlock() }
    }

    // (d) lattice-Reward: значения 1-в-1 из прежнего хука App.kt (Q8), дефолт id
    @Test
    fun latticeRewardFallsBackToLegacyHookDefaults() = runTest {
        stubProgress()

        val reward = AuroraUnlockRewarder.lattice(
            achievementId = null,
            points = 500,
            themeId = "LATTICE_THEME",
            unlockableIds = listOf("unlockable_lattice"),
        )
        rewarder.grant(reward) shouldBe true

        val progress = slot<AchievementProgress>()
        coVerify(exactly = 1) { achievementRepository.insertOrUpdateProgress(capture(progress)) }
        progress.captured.achievementId shouldBe "lattice_resonance"
        coVerify(exactly = 1) { pointsManager.addPoints(500) }
        coVerify(exactly = 1) { pointsManager.incrementUnlocked() }
        coVerify(exactly = 1) { activityDataRepository.recordAchievementUnlock() }
        coVerify(exactly = 1) { userProfileManager.unlockTheme("LATTICE_THEME") }
        verify(exactly = 1) { unlockableManager.setUnlockableUnlocked("unlockable_lattice") }
    }

    // (d2) lattice: явные значения payload важнее дефолтов; null-косметика не вызывается
    @Test
    fun latticeRewardPrefersExplicitPayloadValuesAndSkipsNullCosmetics() = runTest {
        stubProgress(AchievementProgress(achievementId = "custom_lattice", isUnlocked = false))

        val reward = AuroraUnlockRewarder.lattice(
            achievementId = "custom_lattice",
            points = null,
            themeId = null,
            unlockableIds = emptyList(),
        )
        rewarder.grant(reward) shouldBe true

        val progress = slot<AchievementProgress>()
        coVerify(exactly = 1) { achievementRepository.insertOrUpdateProgress(capture(progress)) }
        progress.captured.achievementId shouldBe "custom_lattice"
        coVerify(exactly = 0) { pointsManager.addPoints(any()) }
        coVerify(exactly = 0) { userProfileManager.unlockTheme(any()) }
        verify(exactly = 0) { unlockableManager.setUnlockableUnlocked(any()) }
    }

    // (d3) lattice already unlocked: wasUnlocked-гейт симметричен aurora (Q8)
    @Test
    fun latticeRepeatGrantAfterUnlockSkipsXp() = runTest {
        stubProgress(AchievementProgress(achievementId = "lattice_resonance", isUnlocked = true))

        val granted = rewarder.grant(
            AuroraUnlockRewarder.lattice(
                achievementId = null,
                points = 500,
                themeId = "LATTICE_THEME",
                unlockableIds = listOf("unlockable_lattice"),
            ),
        )

        granted shouldBe false
        coVerify(exactly = 0) { pointsManager.addPoints(any()) }
        coVerify(exactly = 0) { achievementRepository.insertOrUpdateProgress(any()) }
        coVerify(exactly = 1) { userProfileManager.unlockTheme("LATTICE_THEME") }
    }

    // Фабрики companion — единый источник id (для хуков App.kt и Task 10 reset)
    @Test
    fun auroraFactoryCarriesLegacyHookValuesVerbatim() {
        val reward = AuroraUnlockRewarder.aurora(points = 777)

        reward.achievementId shouldBe "aurora_heart"
        reward.points shouldBe 777
        reward.themeId shouldBe "AURORA_PRIME"
        reward.unlockableIds shouldBe listOf("theme_AURORA_PRIME", "special_navbar_aurora_celestial")
    }
}
