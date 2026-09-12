package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NovelTtsSleepTimerTest {

    @Test
    fun `countdown ticks every second and expires at zero`() = runTest {
        val ticks = mutableListOf<Int>()
        val expirations = mutableListOf<NovelTtsSleepTimerMode>()
        val timer = NovelTtsSleepTimer(
            scope = this,
            onTick = { ticks += it },
            onExpire = { expirations += it },
        )

        timer.start(3)
        testScheduler.advanceUntilIdle()

        ticks shouldContainExactly listOf(3, 2, 1, 0)
        expirations shouldContainExactly listOf(NovelTtsSleepTimerMode.COUNTDOWN)
        timer.mode.shouldBeNull()
        timer.remainingSeconds shouldBe 0
    }

    @Test
    fun `cancel before expiry stops the countdown without expiring`() = runTest {
        val ticks = mutableListOf<Int>()
        val expirations = mutableListOf<NovelTtsSleepTimerMode>()
        val timer = NovelTtsSleepTimer(
            scope = this,
            onTick = { ticks += it },
            onExpire = { expirations += it },
        )

        timer.start(3)
        advanceTimeBy(1500)
        runCurrent()
        timer.cancel()
        testScheduler.advanceUntilIdle()

        ticks shouldContainExactly listOf(3, 2)
        expirations.shouldBeEmpty()
        timer.mode.shouldBeNull()
    }

    @Test
    fun `restart replaces the previous countdown`() = runTest {
        val ticks = mutableListOf<Int>()
        val expirations = mutableListOf<NovelTtsSleepTimerMode>()
        val timer = NovelTtsSleepTimer(
            scope = this,
            onTick = { ticks += it },
            onExpire = { expirations += it },
        )

        timer.start(3)
        advanceTimeBy(1000)
        runCurrent()
        timer.start(2)
        testScheduler.advanceUntilIdle()

        ticks shouldContainExactly listOf(3, 2, 2, 1, 0)
        expirations shouldContainExactly listOf(NovelTtsSleepTimerMode.COUNTDOWN)
    }

    @Test
    fun `start with zero seconds cancels the armed timer`() = runTest {
        val ticks = mutableListOf<Int>()
        val expirations = mutableListOf<NovelTtsSleepTimerMode>()
        val timer = NovelTtsSleepTimer(
            scope = this,
            onTick = { ticks += it },
            onExpire = { expirations += it },
        )

        timer.start(3)
        runCurrent()
        timer.start(0)
        testScheduler.advanceUntilIdle()

        ticks shouldContainExactly listOf(3)
        expirations.shouldBeEmpty()
        timer.mode.shouldBeNull()
        timer.remainingSeconds shouldBe 0
    }

    @Test
    fun `end-of-chapter mode arms silently and expires through completeEndOfChapter`() = runTest {
        val ticks = mutableListOf<Int>()
        val expirations = mutableListOf<NovelTtsSleepTimerMode>()
        val timer = NovelTtsSleepTimer(
            scope = this,
            onTick = { ticks += it },
            onExpire = { expirations += it },
        )

        timer.startEndOfChapter()
        testScheduler.advanceUntilIdle()

        ticks.shouldBeEmpty()
        timer.isEndOfChapterArmed.shouldBeTrue()

        timer.completeEndOfChapter() shouldBe true

        expirations shouldContainExactly listOf(NovelTtsSleepTimerMode.END_OF_CHAPTER)
        timer.isEndOfChapterArmed.shouldBeFalse()
        timer.completeEndOfChapter() shouldBe false
    }

    @Test
    fun `completeEndOfChapter does not consume a countdown timer`() = runTest {
        val expirations = mutableListOf<NovelTtsSleepTimerMode>()
        val timer = NovelTtsSleepTimer(
            scope = this,
            onTick = {},
            onExpire = { expirations += it },
        )

        timer.start(3)
        timer.completeEndOfChapter() shouldBe false
        testScheduler.advanceUntilIdle()

        expirations shouldContainExactly listOf(NovelTtsSleepTimerMode.COUNTDOWN)
    }

    @Test
    fun `cancel disarms the end-of-chapter mode`() = runTest {
        val expirations = mutableListOf<NovelTtsSleepTimerMode>()
        val timer = NovelTtsSleepTimer(
            scope = this,
            onTick = {},
            onExpire = { expirations += it },
        )

        timer.startEndOfChapter()
        timer.cancel()

        timer.isEndOfChapterArmed.shouldBeFalse()
        timer.completeEndOfChapter() shouldBe false
        expirations.shouldBeEmpty()
    }
}
