package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuggestionCoordinatorTest {

    @Test
    fun `createSources should return correct sources for media type`() {
        val coordinator = SuggestionCoordinator()

        val animeSources = coordinator.createSources(SuggestionMediaType.ANIME)
        assertEquals(3, animeSources.size)
        assertTrue(animeSources.any { it.name == "AniList" })
        assertTrue(animeSources.any { it.name == "MyAnimeList" })
        assertTrue(animeSources.any { it.name == "Shikimori" })

        val mangaSources = coordinator.createSources(SuggestionMediaType.MANGA)
        assertEquals(3, mangaSources.size)
        assertTrue(mangaSources.any { it.name == "AniList" })
        assertTrue(mangaSources.any { it.name == "MangaUpdates" })
        assertTrue(mangaSources.any { it.name == "Shikimori" })

        val novelSources = coordinator.createSources(SuggestionMediaType.NOVEL)
        // NovelUpdates по умолчанию выключен (Cloudflare/ToS-риск, opt-in в настройках);
        // Shikimori ranobe/similar — первоисточник похожих новелл.
        assertEquals(3, novelSources.size)
        assertTrue(novelSources.any { it.name == "AniList" })
        assertTrue(novelSources.any { it.name == "MangaUpdates" })
        assertTrue(novelSources.any { it.name == "Shikimori" })
        assertTrue(novelSources.none { it.name == "NovelUpdates" })
    }
}
