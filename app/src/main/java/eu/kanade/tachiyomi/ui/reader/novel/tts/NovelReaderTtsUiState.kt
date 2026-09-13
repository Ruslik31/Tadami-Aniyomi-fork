package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode

data class NovelReaderTtsUiState(
    val enabled: Boolean = false,
    val playbackState: NovelTtsPlaybackState = NovelTtsPlaybackState.IDLE,
    val activeSession: NovelTtsSession? = null,
    val pendingChapterHandoffId: Long? = null,
    val activeHighlightMode: NovelTtsHighlightMode = NovelTtsHighlightMode.OFF,
    val activeWordRange: NovelTtsWordRange? = null,
    val activeUtteranceText: String? = null,
    val activeSourceBlockIndex: Int? = null,
    val availableEngines: List<NovelTtsEngineDescriptor> = emptyList(),
    val availableVoices: List<NovelTtsVoiceDescriptor> = emptyList(),
    val availableLocales: List<String> = emptyList(),
    val recentLanguageTags: List<String> = emptyList(),
    val isLoadingVoices: Boolean = false,
    val selectedEnginePackage: String = "",
    val selectedVoiceId: String = "",
    val selectedLocaleTag: String = "",
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val capabilities: NovelTtsEngineCapabilities = NovelTtsEngineCapabilities.NONE,
    val errorMessage: String? = null,
    /** Voice id currently being previewed in settings; empty string means system default. Null = idle. */
    val previewingVoiceId: String? = null,
    /** Remaining seconds of an armed sleep-timer countdown; 0 when no countdown is running. */
    val sleepTimerRemainingSeconds: Int = 0,
    /** True while the "until end of chapter" sleep timer is armed. */
    val sleepTimerEndOfChapter: Boolean = false,
) {
    val isPlaying: Boolean
        get() = playbackState == NovelTtsPlaybackState.PLAYING

    val canResume: Boolean
        get() = playbackState == NovelTtsPlaybackState.PAUSED && activeSession != null

    val isPreviewingVoice: Boolean
        get() = previewingVoiceId != null

    val isSleepTimerActive: Boolean
        get() = sleepTimerEndOfChapter || sleepTimerRemainingSeconds > 0
}
