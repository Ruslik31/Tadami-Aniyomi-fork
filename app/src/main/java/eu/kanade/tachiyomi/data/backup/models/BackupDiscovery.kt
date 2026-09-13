package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry

/**
 * Пользовательские негативные сигналы «Для тебя» в backup (A-цикл):
 * скрытые тайтлы и теговый блэклист. Кэш подборок (discovery_suggestions)
 * не бэкапится — он регенерируем.
 */
@Serializable
data class BackupDiscoveryHidden(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val cleanTitle: String,
    @ProtoNumber(3) val hiddenAt: Long = 0L,
)

@Serializable
data class BackupDiscoveryTag(
    @ProtoNumber(1) val mediaType: String,
    @ProtoNumber(2) val tag: String,
    @ProtoNumber(3) val addedAt: Long = 0L,
)

fun DiscoveryHiddenEntry.toBackupDiscoveryHidden(mediaTypeKey: String) = BackupDiscoveryHidden(
    mediaType = mediaTypeKey,
    cleanTitle = cleanTitle,
    hiddenAt = hiddenAt,
)

fun DiscoveryBlacklistEntry.toBackupDiscoveryTag(mediaTypeKey: String) = BackupDiscoveryTag(
    mediaType = mediaTypeKey,
    tag = tag,
    addedAt = addedAt,
)

fun BackupDiscoveryHidden.toDomainEntry() = DiscoveryHiddenEntry(
    cleanTitle = cleanTitle,
    hiddenAt = hiddenAt,
)

fun BackupDiscoveryTag.toDomainEntry() = DiscoveryBlacklistEntry(
    tag = tag,
    addedAt = addedAt,
)
