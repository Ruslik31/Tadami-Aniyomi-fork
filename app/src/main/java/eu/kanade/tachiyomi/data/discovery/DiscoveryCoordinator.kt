package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

data class DiscoveryFeed(
    val mediaType: DiscoveryMediaType,
    val rows: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    val failedRows: Set<DiscoveryRowType>,
    val generatedAt: Long,
)

/**
 * Собирает discovery-ленту для одного медиатипа: параллельно запускает строители рядов,
 * затем нормализует и фильтрует результат (библиотека / история / скрытые) и распределяет
 * айтемы по рядам с межрядовым приоритетом [DiscoveryRowType] (LIKE > TASTE > TREND).
 *
 * Контракт деградации: упавший строитель даёт пустой ряд и отметку в [DiscoveryFeed.failedRows];
 * записывать провалившийся ряд в кэш или нет — решает вызывающий (DiscoveryRunner).
 */
class DiscoveryCoordinator(
    private val rowBuilders: List<DiscoveryRowBuilder>,
    private val rowLimit: Int = 20,
) {

    suspend fun buildFeed(context: DiscoveryBuildContext): DiscoveryFeed = supervisorScope {
        val jobs = rowBuilders.map { builder ->
            builder.rowType to async {
                try {
                    builder.build(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat { "[DiscoveryCoordinator] row ${builder.rowType} FAILED: ${e.message}" }
                    null
                }
            }
        }

        val failed = mutableSetOf<DiscoveryRowType>()
        val survivors = mutableMapOf<DiscoveryRowType, List<DiscoveryRowItem>>()

        // Обход в порядке приоритета enum: LIKE > TASTE > TREND.
        for (type in DiscoveryRowType.entries) {
            val job = jobs.firstOrNull { it.first == type }?.second ?: continue
            val items = job.await()
            if (items == null) {
                failed += type
            } else {
                survivors[type] = items
            }
        }

        val excluded = context.libraryCleanTitles + context.historyCleanTitles + context.hiddenCleanTitles
        val seen = mutableSetOf<String>()
        val rows = mutableMapOf<DiscoveryRowType, List<DiscoveryRowItem>>()
        for (type in DiscoveryRowType.entries) {
            val items = survivors[type] ?: continue
            rows[type] = items
                .filterNot { it.cleanTitle.isBlank() || it.cleanTitle in excluded || it.cleanTitle in seen }
                .distinctBy { it.cleanTitle }
                .onEach { seen += it.cleanTitle }
                .take(rowLimit)
        }

        DiscoveryFeed(
            mediaType = context.mediaType,
            rows = rows,
            failedRows = failed,
            generatedAt = System.currentTimeMillis(),
        )
    }
}
