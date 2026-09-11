package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class JikanTrendingSourceTest {

    @Test
    fun `parseJikanAnimePage extracts title and cover url`() {
        val json = Json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "mal_id": 52991,
                  "title": "Sousou no Frieren",
                  "title_english": "Frieren: Beyond Journey's End",
                  "images": {
                    "jpg": {
                      "large_image_url": "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg"
                    }
                  },
                  "genres": [
                    { "name": "Adventure" },
                    { "name": "Fantasy" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        ) as JsonObject

        val parsed = parseJikanAnimePage(json, seasonLabel = "current")
        parsed.size shouldBe 1
        parsed.first().title shouldBe "Sousou no Frieren"
        parsed.first().cleanTitle shouldBe "sousou no frieren"
        parsed.first().coverUrl shouldBe "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg"
        parsed.first().anilistId shouldBe 52991L
        parsed.first().genres shouldBe listOf("Adventure", "Fantasy")
        parsed.first().provider shouldBe "jikan_trend"
        parsed.first().seasonLabel shouldBe "current"
    }

    @Test
    fun `parseJikanAnimePage returns empty list on empty data`() {
        val json = Json.parseToJsonElement("""{"data":[]}""") as JsonObject
        parseJikanAnimePage(json) shouldBe emptyList()
    }

    @Test
    fun `parseJikanAnimePage drops Rx hentai items when filter enabled`() {
        val json = Json.parseToJsonElement(
            """
            {
              "data": [
                { "mal_id": 1, "title": "Normal Anime", "rating": "PG-13 - Teens 13 or older" },
                { "mal_id": 2, "title": "Hentai Title", "rating": "Rx - Hentai" },
                { "mal_id": 3, "title": "No Rating Field" }
              ]
            }
            """.trimIndent(),
        ) as JsonObject

        parseJikanAnimePage(json, dropRx = true).map { it.title } shouldBe listOf("Normal Anime", "No Rating Field")
        parseJikanAnimePage(json, dropRx = false).size shouldBe 3
    }
}
