package eu.kanade.presentation.reader.manga

import androidx.compose.runtime.Composable
import eu.kanade.presentation.reader.components.FinalePlate
import eu.kanade.tachiyomi.ui.reader.model.ReaderFinaleState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Manga binding of the shared [FinalePlate]: revealed on the end-of-series transition,
 * primary action returns to the entry screen.
 */
@Composable
fun ReaderFinaleOverlay(
    state: ReaderFinaleState,
    reducedMotion: Boolean,
    onBackToManga: () -> Unit,
    onStay: () -> Unit,
) {
    FinalePlate(
        state = state,
        reducedMotion = reducedMotion,
        backLabel = stringResource(MR.strings.reader_finale_back_to_manga),
        onBack = onBackToManga,
        onStay = onStay,
    )
}
