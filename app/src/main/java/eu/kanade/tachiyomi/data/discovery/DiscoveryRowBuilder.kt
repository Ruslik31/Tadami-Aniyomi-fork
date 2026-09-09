package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

data class DiscoveryRowItem(
    val title: String,
    val cleanTitle: String,
    val coverUrl: String?,
    val reason: String?,
    val seedTitle: String?,
    val provider: String,
    val score: Double,
)

data class DiscoveryBuildContext(
    val mediaType: DiscoveryMediaType,
    val seeds: List<DiscoverySeedInput>,
    val libraryCleanTitles: Set<String>,
    val historyCleanTitles: Set<String>,
    val hiddenCleanTitles: Set<String>,
    val tasteProfile: List<Pair<String, Double>> = emptyList(),
    val sourceId: Long = -1L,
)

interface DiscoveryRowBuilder {
    val rowType: DiscoveryRowType
    suspend fun build(context: DiscoveryBuildContext): List<DiscoveryRowItem>
}
