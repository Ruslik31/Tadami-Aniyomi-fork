package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * Первичная постановка periodic-воркера ленты «Для тебя» (discovery_enabled=true по умолчанию).
 * Версия = versionCode релиза, который везёт фичу (207 был текущим на 99cd141df).
 * Для dev-сборок и чистой установки дополнительно существует идемпотентная страховка
 * setupTask из HomeHubTab (slice 1, Task 8).
 */
class SetupDiscoveryUpdateMigration : Migration {
    override val version = 208f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        DiscoveryUpdateJob.setupTask(context)
        return true
    }
}
