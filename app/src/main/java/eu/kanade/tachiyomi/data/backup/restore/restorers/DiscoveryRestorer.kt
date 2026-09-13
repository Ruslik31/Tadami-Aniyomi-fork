package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toDomainEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Restore скрытых тайтлов и тегов «Для тебя»: один батч (транзакция) на медиатип,
 * insertOrIgnore — идемпотентный merge, существующие локальные записи не затираются.
 */
class DiscoveryRestorer(
    private val repository: DiscoveryRepository = Injekt.get(),
) {
    suspend fun restoreDiscovery(
        hidden: List<BackupDiscoveryHidden>,
        tags: List<BackupDiscoveryTag>,
    ) {
        hidden.groupBy { it.mediaType }.forEach { (mediaKey, items) ->
            val media = DiscoveryMediaType.fromKey(mediaKey) ?: return@forEach
            repository.restoreHiddenEntries(media, items.map { it.toDomainEntry() })
        }
        tags.groupBy { it.mediaType }.forEach { (mediaKey, items) ->
            val media = DiscoveryMediaType.fromKey(mediaKey) ?: return@forEach
            repository.restoreBlacklistEntries(media, items.map { it.toDomainEntry() })
        }
    }
}
