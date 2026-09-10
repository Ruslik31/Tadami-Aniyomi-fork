package eu.kanade.domain.easteregg.aurora

import kotlinx.coroutines.flow.first
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.data.achievement.UserProfileManager
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository

/**
 * Идемпотентная выдача наград пасхальных яиц (Aurora K1 + Lattice Q8).
 *
 * Проблема: хуки App.kt реплеили побочные эффекты разблокировки безусловно на
 * каждый emitUnlocked (реплей финала из AuroraCodexScreen давал +777 XP за клик).
 * По образцу AchievementCalculator: wasUnlocked-гейт по авторитетному DB-статусу —
 * XP/счётчики только при первой разблокировке; идемпотентные set-операции
 * (тема/unlockables) повторяются и на реплее — защита от restore/migration-потерь.
 */
class AuroraUnlockRewarder(
    private val achievementRepository: AchievementRepository,
    private val pointsManager: PointsManager,
    private val unlockableManager: UnlockableManager,
    private val userProfileManager: UserProfileManager,
    private val activityDataRepository: ActivityDataRepository,
) {

    data class Reward(
        val achievementId: String,
        val points: Int,
        val themeId: String?,
        val unlockableIds: List<String>,
    )

    /**
     * @return true — первая разблокировка (полный путь, как прежний хук App.kt);
     * false — достижение уже открыто: только идемпотентная косметика, без XP.
     */
    suspend fun grant(reward: Reward): Boolean {
        val existing = achievementRepository.getAllProgress().first()
            .find { it.achievementId == reward.achievementId }
        val wasUnlocked = existing?.isUnlocked == true
        if (wasUnlocked) {
            applyCosmetics(reward)
            return false
        }
        achievementRepository.insertOrUpdateProgress(
            AchievementProgress.createStandard(
                achievementId = reward.achievementId,
                progress = 1,
                maxProgress = 1,
                isUnlocked = true,
                unlockedAt = System.currentTimeMillis(),
            ),
        )
        if (reward.points > 0) {
            pointsManager.addPoints(reward.points)
        }
        pointsManager.incrementUnlocked()
        activityDataRepository.recordAchievementUnlock()
        applyCosmetics(reward)
        return true
    }

    /** Идемпотентные set-операции: безопасно повторить после restore/migration. */
    private suspend fun applyCosmetics(reward: Reward) {
        reward.themeId?.let { userProfileManager.unlockTheme(it) }
        reward.unlockableIds.forEach { unlockableManager.setUnlockableUnlocked(it) }
    }

    companion object {
        const val AURORA_ACHIEVEMENT_ID = "aurora_heart"
        const val AURORA_THEME_ID = "AURORA_PRIME"
        const val LATTICE_DEFAULT_ACHIEVEMENT_ID = "lattice_resonance"
        val AURORA_UNLOCKABLE_IDS = listOf("theme_AURORA_PRIME", "special_navbar_aurora_celestial")

        /** Награда финала Aurora; points — из payload (bonusPoints ?: 0). */
        fun aurora(points: Int): Reward = Reward(
            achievementId = AURORA_ACHIEVEMENT_ID,
            points = points,
            themeId = AURORA_THEME_ID,
            unlockableIds = AURORA_UNLOCKABLE_IDS,
        )

        /** Награда финала Lattice; значения 1-в-1 соответствуют прежнему хуку App.kt. */
        fun lattice(
            achievementId: String?,
            points: Int?,
            themeId: String?,
            unlockableIds: List<String>,
        ): Reward = Reward(
            achievementId = achievementId ?: LATTICE_DEFAULT_ACHIEVEMENT_ID,
            points = points ?: 0,
            themeId = themeId,
            unlockableIds = unlockableIds,
        )
    }
}
