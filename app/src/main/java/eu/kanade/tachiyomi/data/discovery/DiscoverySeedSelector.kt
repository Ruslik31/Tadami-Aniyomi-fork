package eu.kanade.tachiyomi.data.discovery

data class DiscoverySeedInput(
    val entryId: Long,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val description: String? = null,
    val author: String? = null,
    val genres: List<String> = emptyList(),
    val dateAdded: Long = 0L,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val lastInteraction: Long? = null,
)

data class SeedSettings(
    val maxSeeds: Int = 3,
    val useCompleted: Boolean = true,
    val useActive14: Boolean = true,
    val useAdded: Boolean = false,
    val completedWindowDays: Long = 30,
    val activeWindowDays: Long = 14,
)

class DiscoverySeedSelector(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun select(candidates: List<DiscoverySeedInput>, settings: SeedSettings): List<DiscoverySeedInput> {
        val now = nowMs()
        val valid = candidates.filter { it.title.isNotBlank() }
        val completedWindow = now - settings.completedWindowDays * DAY_MS
        val activeWindow = now - settings.activeWindowDays * DAY_MS

        val completed = if (settings.useCompleted) {
            valid.filter { it.isCompleted && (it.completedAt ?: it.lastInteraction ?: 0L) >= completedWindow }
                .sortedByDescending { it.completedAt ?: it.lastInteraction ?: 0L }
        } else {
            emptyList()
        }

        val completedIds = completed.mapTo(HashSet()) { it.entryId }
        val active = if (settings.useActive14) {
            valid.filterNot { it.entryId in completedIds }
                .filter { !it.isCompleted && (it.lastInteraction ?: 0L) >= activeWindow }
                .sortedByDescending { it.lastInteraction ?: 0L }
        } else {
            emptyList()
        }

        val takenIds = completedIds + active.mapTo(HashSet()) { it.entryId }
        val added = if (settings.useAdded) {
            valid.filterNot { it.entryId in takenIds }.sortedByDescending { it.dateAdded }
        } else {
            emptyList()
        }

        return (completed + active + added)
            .distinctBy { it.entryId }
            .take(settings.maxSeeds.coerceIn(1, 5))
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
