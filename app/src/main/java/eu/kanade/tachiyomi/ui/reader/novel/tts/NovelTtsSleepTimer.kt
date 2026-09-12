package eu.kanade.tachiyomi.ui.reader.novel.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NovelTtsSleepTimerMode {
    COUNTDOWN,
    END_OF_CHAPTER,
}

/**
 * Session sleep timer for novel TTS, following the player sleep-timer semantics
 * (`PlayerViewModel.startTimer`): the countdown ticks once per second, manual pause/play does not
 * stop it and cancelling is `start(0)`. In [NovelTtsSleepTimerMode.END_OF_CHAPTER] mode there is
 * no countdown; the owner consumes the timer at the chapter boundary through
 * [completeEndOfChapter].
 */
class NovelTtsSleepTimer(
    private val scope: CoroutineScope,
    private val onTick: (remainingSeconds: Int) -> Unit,
    private val onExpire: suspend (mode: NovelTtsSleepTimerMode) -> Unit,
) {
    private var timerJob: Job? = null

    var mode: NovelTtsSleepTimerMode? = null
        private set

    var remainingSeconds: Int = 0
        private set

    val isEndOfChapterArmed: Boolean
        get() = mode == NovelTtsSleepTimerMode.END_OF_CHAPTER

    /** Starts a countdown; [seconds] below 1 disarms the timer (cancel == start(0)). */
    fun start(seconds: Int) {
        cancel()
        if (seconds < 1) return
        mode = NovelTtsSleepTimerMode.COUNTDOWN
        remainingSeconds = seconds
        timerJob = scope.launch {
            for (remaining in seconds downTo 0) {
                remainingSeconds = remaining
                onTick(remaining)
                delay(TICK_INTERVAL_MS)
            }
            mode = null
            remainingSeconds = 0
            onExpire(NovelTtsSleepTimerMode.COUNTDOWN)
        }
    }

    fun startEndOfChapter() {
        cancel()
        mode = NovelTtsSleepTimerMode.END_OF_CHAPTER
        remainingSeconds = 0
    }

    /** Consumes an armed end-of-chapter timer at the chapter boundary; false when not armed. */
    suspend fun completeEndOfChapter(): Boolean {
        if (!isEndOfChapterArmed) return false
        mode = null
        onExpire(NovelTtsSleepTimerMode.END_OF_CHAPTER)
        return true
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        mode = null
        remainingSeconds = 0
    }

    private companion object {
        const val TICK_INTERVAL_MS = 1000L
    }
}
