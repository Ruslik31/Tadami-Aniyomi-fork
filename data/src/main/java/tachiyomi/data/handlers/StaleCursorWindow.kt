package tachiyomi.data.handlers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retry

/**
 * Android can refresh the CursorWindow under a cursor that is being iterated when concurrent
 * writes (typically DELETEs) shrink the result set: reads at the cursor's logical position
 * then return null even for NOT NULL columns, so SQLDelight's generated `!!` mappers throw
 * NullPointerException (sqldelight/sqldelight#6049; logcat marker: "CursorWindow: Failed to
 * read row X, column Y from a window with Z rows"). The condition is transient — re-running
 * the query obtains a fresh cursor — so read entry points retry once on NPE. Write paths
 * (`await` with a caller block) are deliberately NOT retried: they may be non-idempotent.
 */
internal fun <T> Flow<T>.retryOnceOnStaleCursorWindow(): Flow<T> =
    retry(1) { it is NullPointerException }

internal suspend fun <T> retryOnceOnStaleCursorWindow(block: suspend () -> T): T {
    return try {
        block()
    } catch (e: NullPointerException) {
        block()
    }
}
