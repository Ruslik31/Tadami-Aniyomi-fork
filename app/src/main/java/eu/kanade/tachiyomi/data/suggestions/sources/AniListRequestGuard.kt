package eu.kanade.tachiyomi.data.suggestions.sources

import kotlinx.coroutines.delay
import okhttp3.Headers
import java.io.IOException

/**
 * Троттлинг + circuit breaker для AniList GraphQL.
 *
 * Логи устройства показали массовые HTTP 403 от graphql.anilist.co после пакетных
 * прогонов (десятки запросов за run): похоже на rate-limit бан Cloudflare. Меры:
 * - минимум [MIN_INTERVAL_MS] между вызовами (сериализация параллельных кандидатов);
 * - при 403 цепь размыкается на [CIRCUIT_OPEN_MS]: последующие вызовы падают сразу,
 *   без сети → ряд помечается failed → кэш ленты НЕ затирается.
 */
object AniListRequestGuard {

    private const val MIN_INTERVAL_MS = 350L
    private const val CIRCUIT_OPEN_MS = 10 * 60_000L

    private var lastCallAt = 0L
    private var circuitOpenUntil = 0L

    /** Идентифицирующие заголовки вместо дефолтного okhttp-UA. */
    val headers: Headers = Headers.Builder()
        .add("User-Agent", "Tadami/0.61 (Android; discovery-suggestions)")
        .add("Accept", "application/json")
        .add("Accept-Language", "en-US,en;q=0.8")
        .build()

    fun circuitOpen(): Boolean = System.currentTimeMillis() < circuitOpenUntil

    fun reportForbidden() {
        circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_OPEN_MS
    }

    fun ensureClosed() {
        if (circuitOpen()) {
            throw IOException("AniList circuit breaker open (403 backoff)")
        }
    }

    /** Выдерживает интервал между вызовами; безопасен при параллельных заходах. */
    suspend fun acquire() {
        val waitMs = synchronized(this) {
            val now = System.currentTimeMillis()
            val wait = lastCallAt + MIN_INTERVAL_MS - now
            lastCallAt = maxOf(now, lastCallAt) + MIN_INTERVAL_MS
            wait
        }
        if (waitMs > 0) delay(waitMs)
    }
}
