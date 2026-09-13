package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelReaderTtsUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsSleepTimerWiringTest {

    @Test
    fun `sleep timer state defaults to disarmed`() {
        val uiState = NovelReaderTtsUiState()

        uiState.sleepTimerRemainingSeconds shouldBe 0
        uiState.sleepTimerEndOfChapter shouldBe false
        uiState.isSleepTimerActive.shouldBeFalse()
    }

    @Test
    fun `countdown remaining seconds arm the sleep timer`() {
        val uiState = NovelReaderTtsUiState(sleepTimerRemainingSeconds = 42)

        uiState.isSleepTimerActive.shouldBeTrue()
    }

    @Test
    fun `end-of-chapter flag arms the sleep timer without countdown`() {
        val uiState = NovelReaderTtsUiState(sleepTimerEndOfChapter = true)

        uiState.sleepTimerRemainingSeconds shouldBe 0
        uiState.isSleepTimerActive.shouldBeTrue()
    }

    @Test
    fun `expiry while playing pauses and announces`() {
        shouldPauseAndAnnounceSleepTimerExpiry(NovelTtsPlaybackState.PLAYING).shouldBeTrue()
    }

    @Test
    fun `expiry while paused stays silent`() {
        shouldPauseAndAnnounceSleepTimerExpiry(NovelTtsPlaybackState.PAUSED).shouldBeFalse()
    }

    @Test
    fun `expiry without an active session stays silent`() {
        shouldPauseAndAnnounceSleepTimerExpiry(NovelTtsPlaybackState.IDLE).shouldBeFalse()
        shouldPauseAndAnnounceSleepTimerExpiry(NovelTtsPlaybackState.COMPLETED).shouldBeFalse()
    }
}
