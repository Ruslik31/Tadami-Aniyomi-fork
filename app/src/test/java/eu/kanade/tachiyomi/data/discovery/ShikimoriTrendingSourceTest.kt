package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test

class ShikimoriTrendingSourceTest {

    @Test
    fun `parseShikimoriItems uses russian title when in russian locale`() {
        val items = listOf(
            ShikimoriMediaItem(
                id = 5114L,
                name = "Fullmetal Alchemist: Brotherhood",
                russian = "Стальной алхимик: Братство",
                image = ShikimoriCover(original = "/system/animes/original/5114.jpg"),
                score = 9.1,
                genres = listOf(ShikimoriGenre(1L, "Action", "Экшен")),
            ),
        )

        val parsedRu = parseShikimoriItems(items, seasonLabel = "current", isRussianLocale = true)
        parsedRu.size shouldBe 1
        parsedRu.first().title shouldBe "Стальной алхимик: Братство"
        parsedRu.first().cleanTitle shouldBe "стальной алхимик братство"
        parsedRu.first().coverUrl shouldBe "https://shikimori.one/system/animes/original/5114.jpg"
        parsedRu.first().genres shouldBe listOf("Экшен")
        parsedRu.first().provider shouldBe "shikimori_trend"

        val parsedEn = parseShikimoriItems(items, seasonLabel = "current", isRussianLocale = false)
        parsedEn.first().title shouldBe "Fullmetal Alchemist: Brotherhood"
        parsedEn.first().cleanTitle shouldBe "fullmetal alchemist brotherhood"
    }

    @Test
    fun `parseShikimoriItems skips blank titles`() {
        val items = listOf(
            ShikimoriMediaItem(id = 1L, name = "", russian = null),
            ShikimoriMediaItem(id = 2L, name = "Valid Anime", russian = null),
        )
        val parsed = parseShikimoriItems(items)
        parsed.size shouldBe 1
        parsed.first().title shouldBe "Valid Anime"
    }
}
