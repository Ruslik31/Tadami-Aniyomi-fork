package tachiyomi.domain.discovery.model

enum class DiscoveryMediaType(val key: String) {
    ANIME("anime"),
    MANGA("manga"),
    NOVEL("novel"),
    ;

    companion object {
        fun fromKey(key: String): DiscoveryMediaType? = entries.firstOrNull { it.key == key }
    }
}

/** Порядок enum = приоритет межрядового дедупа (LIKE > TASTE > TREND > SOURCE). */
enum class DiscoveryRowType(val key: String) {
    LIKE("like"),
    TASTE("taste"),
    TREND("trend"),
    SOURCE("source"),
    ;

    companion object {
        fun fromKey(key: String): DiscoveryRowType? = entries.firstOrNull { it.key == key }
    }
}

data class DiscoverySuggestion(
    val id: Long,
    val mediaType: DiscoveryMediaType,
    val rowType: DiscoveryRowType,
    val title: String,
    val cleanTitle: String,
    val coverUrl: String?,
    val reason: String?,
    val seedTitle: String?,
    val provider: String,
    val score: Double,
    val position: Long,
    val createdAt: Long,
)

fun normalizeDiscoveryTitle(raw: String): String =
    raw.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
