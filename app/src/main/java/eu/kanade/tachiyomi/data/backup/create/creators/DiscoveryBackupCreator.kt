package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryTag
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Собирает скрытые тайтлы и теговый блэклист «Для тебя» по всем медиатипам.
 * Кэш подборок не собирается — он регенерируем (решение A-спека).
 */
class DiscoveryBackupCreator(
    private val repository: DiscoveryRepository = Injekt.get(),
) {
    suspend operator fun invoke(): Pair<List<BackupDiscoveryHidden>, List<BackupDiscoveryTag>> {
        val hidden = mutableListOf<BackupDiscoveryHidden>()
        val tags = mutableListOf<BackupDiscoveryTag>()
        DiscoveryMediaType.entries.forEach { media ->
            repository.getHiddenEntries(media).forEach { hidden += it.toBackupDiscoveryHidden(media.key) }
            repository.getBlacklistEntries(media).forEach { tags += it.toBackupDiscoveryTag(media.key) }
        }
        return hidden to tags
    }
}
