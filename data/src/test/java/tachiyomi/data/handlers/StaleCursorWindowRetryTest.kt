package tachiyomi.data.handlers

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins the stale-CursorWindow guard (sqldelight/sqldelight#6049): a transient NPE from a
 * refreshed window re-runs the read exactly once; a second NPE or any other error propagates.
 */
class StaleCursorWindowRetryTest {

    @Test
    fun `flow resubscribes once after a stale-window NPE`() = runTest {
        var attempts = 0
        val result = flow {
            attempts++
            if (attempts == 1) throw NullPointerException("stale window")
            emit(1)
        }
            .retryOnceOnStaleCursorWindow()
            .toList()

        result shouldBe listOf(1)
        attempts shouldBe 2
    }

    @Test
    fun `flow propagates a second stale-window NPE`() = runTest {
        val failing = flow<Int> { throw NullPointerException("stale window") }
            .retryOnceOnStaleCursorWindow()

        assertThrows<NullPointerException> { failing.toList() }
    }

    @Test
    fun `flow does not retry other errors`() = runTest {
        var attempts = 0
        val failing = flow<Int> {
            attempts++
            throw IllegalStateException("real bug")
        }
            .retryOnceOnStaleCursorWindow()

        assertThrows<IllegalStateException> { failing.toList() }
        attempts shouldBe 1
    }

    @Test
    fun `suspending read retries once after a stale-window NPE`() = runTest {
        var attempts = 0
        val result = retryOnceOnStaleCursorWindow {
            attempts++
            if (attempts == 1) throw NullPointerException("stale window")
            "row"
        }

        result shouldBe "row"
        attempts shouldBe 2
    }

    @Test
    fun `suspending read propagates a second stale-window NPE`() = runTest {
        assertThrows<NullPointerException> {
            retryOnceOnStaleCursorWindow { throw NullPointerException("stale window") }
        }
    }
}
