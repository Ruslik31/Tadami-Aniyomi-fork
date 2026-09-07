package eu.kanade.tachiyomi.network.interceptor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * P6/P3: process-wide record of hosts whose latest Cloudflare solve ended in an INTERACTIVE
 * challenge (a human click is required). The interceptor uses it as a negative cache (no
 * repeated heavyweight WebView solves within [NEGATIVE_TTL_MS]); the UI uses it to offer a
 * guided manual solve in the WebView screen, whose cookies are shared app-wide through
 * AndroidCookieJar - solving there once clears every client.
 */
object CloudflareInteractiveChallengeTracker {

    const val NEGATIVE_TTL_MS: Long = 10L * 60L * 1000L

    data class Entry(
        val host: String,
        val url: String,
        val atMs: Long,
    )

    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val state: StateFlow<Map<String, Entry>> = entries.asStateFlow()

    fun record(url: String, atMs: Long) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        entries.value = entries.value + (host to Entry(host, url, atMs))
    }

    /**
     * The clock is invoked ONLY when a record exists for the host - callers on platforms
     * where the clock is expensive/unavailable (JVM tests: SystemClock is not mocked) must
     * not pay for it on the cache-free fast path.
     */
    fun freshEntry(host: String, clock: () -> Long): Entry? =
        entries.value[host]?.takeIf { clock() - it.atMs < NEGATIVE_TTL_MS }

    fun freshEntries(clockMs: Long): List<Entry> =
        entries.value.values.filter { clockMs - it.atMs < NEGATIVE_TTL_MS }

    fun clear(host: String) {
        entries.value = entries.value - host
    }

    fun clearAll() {
        entries.value = emptyMap()
    }
}
