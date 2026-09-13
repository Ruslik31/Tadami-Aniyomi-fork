package tachiyomi.data.achievement.handler

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.AchievementTestBase

/**
 * Task 10 (C3/Q6): guard-семантика нового subtract-API PointsManager для
 * отката наград «Сердца Авроры» в debugReset — end-to-end на in-memory
 * AchievementsDatabase (JdbcSqliteDriver, паттерн PointsManagerTest).
 */
class PointsManagerResetTest : AchievementTestBase() {

    private lateinit var pointsManager: PointsManager

    @BeforeEach
    override fun setup() {
        super.setup()
        pointsManager = PointsManager(database)
    }

    private fun profile() = database.userProfileQueries.getDefaultProfile().executeAsOne()

    @Test
    fun `subtractPoints rolls back exactly what addPoints granted`() = runTest {
        pointsManager.addPoints(777)
        profile().total_xp shouldBe 777L

        pointsManager.subtractPoints(777)
        profile().total_xp shouldBe 0L
    }

    @Test
    fun `subtractPoints never drives total_xp below zero`() = runTest {
        pointsManager.subtractPoints(10)
        profile().total_xp shouldBe 0L

        pointsManager.addPoints(50)
        pointsManager.subtractPoints(80)
        profile().total_xp shouldBe 0L
    }

    @Test
    fun `subtractPoints ignores non-positive deltas like addPoints`() = runTest {
        pointsManager.addPoints(50)

        pointsManager.subtractPoints(0)
        pointsManager.subtractPoints(-5)

        profile().total_xp shouldBe 50L
    }

    @Test
    fun `subtractPoints recalculates level downwards`() = runTest {
        pointsManager.addPoints(777)
        (profile().level > 1L) shouldBe true

        pointsManager.subtractPoints(777)
        profile().total_xp shouldBe 0L
        profile().level shouldBe 1L
    }

    @Test
    fun `decrementAchievementUnlocked mirrors increment with zero floor`() = runTest {
        pointsManager.decrementAchievementUnlocked()
        profile().achievements_unlocked shouldBe 0L

        pointsManager.incrementUnlocked()
        pointsManager.incrementUnlocked()
        pointsManager.decrementAchievementUnlocked()
        profile().achievements_unlocked shouldBe 1L

        pointsManager.decrementAchievementUnlocked()
        pointsManager.decrementAchievementUnlocked()
        profile().achievements_unlocked shouldBe 0L
    }
}
