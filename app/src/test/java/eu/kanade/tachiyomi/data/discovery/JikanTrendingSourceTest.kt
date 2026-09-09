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
}
