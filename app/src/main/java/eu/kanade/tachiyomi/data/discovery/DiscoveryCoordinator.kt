package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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

    suspend fun streamFeed(
        context: DiscoveryBuildContext,
        onRowAvailable: suspend (DiscoveryRowType, List<DiscoveryRowItem>) -> Unit,
    ): DiscoveryFeed = supervisorScope {
        val channel = Channel<Pair<DiscoveryRowType, List<DiscoveryRowItem>?>>(Channel.UNLIMITED)
        val failed = mutableSetOf<DiscoveryRowType>()
        val excluded = context.libraryCleanTitles + context.historyCleanTitles + context.hiddenCleanTitles
        val rawCandidates = mutableMapOf<DiscoveryRowType, List<DiscoveryRowItem>>()
        val finalRows = mutableMapOf<DiscoveryRowType, List<DiscoveryRowItem>>()

        val jobs = rowBuilders.map { builder ->
            launch {
                val items = try {
                    builder.build(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat { "[DiscoveryCoordinator] row ${builder.rowType} FAILED: ${e.message}" }
                    null
                }
                channel.send(builder.rowType to items)
            }
        }

        repeat(rowBuilders.size) {
            val (type, items) = channel.receive()
            if (items == null) {
                failed += type
            } else {
                rawCandidates[type] = items

                // Пересчитываем все полученные ряды в строгом порядке приоритета enum: LIKE > TASTE > TREND > SOURCE.
                val seenSoFar = mutableSetOf<String>()
                val updatedRows = mutableListOf<Pair<DiscoveryRowType, List<DiscoveryRowItem>>>()

                for (pType in DiscoveryRowType.entries) {
                    val candidates = rawCandidates[pType] ?: continue
                    val selected = selectRowItems(candidates, excluded, seenSoFar, context.recentCleanTitles)
                    selected.forEach { seenSoFar += it.cleanTitle }

                    if (finalRows[pType] != selected) {
                        finalRows[pType] = selected
                        updatedRows.add(pType to selected)
                    }
                }

                for ((updatedType, updatedItems) in updatedRows) {
                    onRowAvailable(updatedType, updatedItems)
                }
            }
        }

        jobs.forEach { it.join() }

        DiscoveryFeed(
            mediaType = context.mediaType,
            rows = finalRows,
            failedRows = failed,
            generatedAt = System.currentTimeMillis(),
        )
    }

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

        // Обход в порядке приоритета enum: LIKE > TASTE > TREND > SOURCE.
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
            val selected = selectRowItems(items, excluded, seen, context.recentCleanTitles)
            selected.forEach { seen += it.cleanTitle }
            rows[type] = selected
        }

        DiscoveryFeed(
            mediaType = context.mediaType,
            rows = rows,
            failedRows = failed,
            generatedAt = System.currentTimeMillis(),
        )
    }

    private fun selectRowItems(
        items: List<DiscoveryRowItem>,
        excluded: Set<String>,
        seen: Set<String>,
        recentCleanTitles: Set<String>,
    ): List<DiscoveryRowItem> {
        val valid = items
            .filterNot { it.cleanTitle.isBlank() || it.cleanTitle in excluded || it.cleanTitle in seen }
            .distinctBy { it.cleanTitle }

        return if (recentCleanTitles.isNotEmpty()) {
            val fresh = valid.filterNot { it.cleanTitle in recentCleanTitles }
            if (fresh.size >= rowLimit) {
                fresh.take(rowLimit)
            } else {
                val stale = valid.filter { it.cleanTitle in recentCleanTitles }
                (fresh + stale).take(rowLimit)
            }
        } else {
            valid.take(rowLimit)
        }
    }
}
