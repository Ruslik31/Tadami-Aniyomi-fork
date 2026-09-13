package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.BackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryHidden
import eu.kanade.tachiyomi.data.backup.models.toBackupDiscoveryTag
import eu.kanade.tachiyomi.data.backup.models.toDomainEntry
import eu.kanade.tachiyomi.data.backup.restore.restorers.DiscoveryRestorer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryBlacklistEntry
import tachiyomi.domain.discovery.model.DiscoveryHiddenEntry
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion
import tachiyomi.domain.discovery.repository.DiscoveryRepository

class DiscoveryBackupRoundTripTest {

    @Test
    fun `discovery entries survive proto round trip`() {
        val backup = Backup(
            backupDiscoveryHidden = listOf(
                BackupDiscoveryHidden("anime", "solo leveling", 1_700_000_000_000L),
                BackupDiscoveryHidden("novel", "overlord", 1_700_000_000_001L),
            ),
            backupDiscoveryBlacklistTags = listOf(
                BackupDiscoveryTag("manga", "Гарем", 1_700_000_000_002L),
            ),
        )

        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

        decoded.backupDiscoveryHidden shouldBe backup.backupDiscoveryHidden
        decoded.backupDiscoveryBlacklistTags shouldBe backup.backupDiscoveryBlacklistTags
    }

    @Test
    fun `empty backup decodes without discovery data`() {
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup())
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        decoded.backupDiscoveryHidden shouldBe emptyList()
        decoded.backupDiscoveryBlacklistTags shouldBe emptyList()
    }

    @Test
    fun `mappers preserve all fields`() {
        val hidden = DiscoveryHiddenEntry("clean title", 42L)
        hidden.toBackupDiscoveryHidden("manga").toDomainEntry() shouldBe hidden
        val tag = DiscoveryBlacklistEntry("Фэнтези", 43L)
        tag.toBackupDiscoveryTag("novel").toDomainEntry() shouldBe tag
    }

    @Test
    fun `restorer groups entries by media type and keeps timestamps`() {
        val repo = FakeDiscoveryRepository()
        val restorer = DiscoveryRestorer(repo)

        runBlocking {
            restorer.restoreDiscovery(
                hidden = listOf(
                    BackupDiscoveryHidden("anime", "a1", 10L),
                    BackupDiscoveryHidden("anime", "a2", 11L),
                    BackupDiscoveryHidden("novel", "n1", 12L),
                    BackupDiscoveryHidden("bogus", "x", 13L),
                ),
                tags = listOf(
                    BackupDiscoveryTag("manga", "t1", 20L),
                ),
            )
        }

        repo.restoredHidden[DiscoveryMediaType.ANIME] shouldBe listOf(
            DiscoveryHiddenEntry("a1", 10L),
            DiscoveryHiddenEntry("a2", 11L),
        )
        repo.restoredHidden[DiscoveryMediaType.NOVEL] shouldBe listOf(DiscoveryHiddenEntry("n1", 12L))
        repo.restoredHidden.containsKey(DiscoveryMediaType.MANGA) shouldBe false
        repo.restoredTags[DiscoveryMediaType.MANGA] shouldBe listOf(DiscoveryBlacklistEntry("t1", 20L))
    }

    @Test
    fun `restorer on empty lists performs no writes`() {
        val repo = FakeDiscoveryRepository()
        runBlocking { DiscoveryRestorer(repo).restoreDiscovery(emptyList(), emptyList()) }
        repo.restoredHidden shouldBe emptyMap()
        repo.restoredTags shouldBe emptyMap()
    }
}

private class FakeDiscoveryRepository : DiscoveryRepository {
    val restoredHidden = mutableMapOf<DiscoveryMediaType, List<DiscoveryHiddenEntry>>()
    val restoredTags = mutableMapOf<DiscoveryMediaType, List<DiscoveryBlacklistEntry>>()

    override fun subscribe(mediaType: DiscoveryMediaType): Flow<List<DiscoverySuggestion>> =
        MutableStateFlow(emptyList())

    override fun subscribeHidden(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())

    override suspend fun replaceRows(
        mediaType: DiscoveryMediaType,
        rowType: DiscoveryRowType,
        items: List<DiscoverySuggestion>,
    ) = Unit

    override suspend fun getHiddenTitles(mediaType: DiscoveryMediaType): Set<String> = emptySet()
    override suspend fun hide(mediaType: DiscoveryMediaType, cleanTitle: String) = Unit
    override suspend fun unhide(mediaType: DiscoveryMediaType, cleanTitle: String) = Unit
    override suspend fun clearHidden(mediaType: DiscoveryMediaType) = Unit
    override suspend fun lastUpdatedAt(mediaType: DiscoveryMediaType): Long? = null
    override fun subscribeBlacklist(mediaType: DiscoveryMediaType): Flow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun getBlacklistedTags(mediaType: DiscoveryMediaType): Set<String> = emptySet()
    override suspend fun blacklistTag(mediaType: DiscoveryMediaType, tag: String) = Unit
    override suspend fun unblacklistTag(mediaType: DiscoveryMediaType, tag: String) = Unit
    override suspend fun clearBlacklist(mediaType: DiscoveryMediaType) = Unit
    override suspend fun getHiddenEntries(mediaType: DiscoveryMediaType): List<DiscoveryHiddenEntry> = emptyList()
    override suspend fun getBlacklistEntries(mediaType: DiscoveryMediaType): List<DiscoveryBlacklistEntry> = emptyList()

    override suspend fun restoreHiddenEntries(mediaType: DiscoveryMediaType, entries: List<DiscoveryHiddenEntry>) {
        restoredHidden[mediaType] = entries
    }

    override suspend fun restoreBlacklistEntries(
        mediaType: DiscoveryMediaType,
        entries: List<DiscoveryBlacklistEntry>,
    ) {
        restoredTags[mediaType] = entries
    }
}
