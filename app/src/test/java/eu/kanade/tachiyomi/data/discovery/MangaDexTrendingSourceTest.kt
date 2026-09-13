package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class MangaDexTrendingSourceTest {

    @Test
    fun `parseMangaDexData resolves english title and cover url`() {
        val json = Json.parseToJsonElement(
            """
            {
              "result": "ok",
              "data": [
                {
                  "id": "manga-uuid-1",
                  "type": "manga",
                  "attributes": {
                    "title": { "en": "Chainsaw Man" },
                    "altTitles": [ { "ru": "Человек-бензопила" } ],
                    "tags": [
                      {
                        "id": "tag-1",
                        "attributes": {
                          "name": { "en": "Action" }
                        }
                      }
                    ]
                  },
                  "relationships": [
                    {
                      "id": "cover-uuid-1",
                      "type": "cover_art",
                      "attributes": {
                        "fileName": "cover1.jpg"
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        ) as JsonObject

        val parsedEn = parseMangaDexData(json, isRussianLocale = false)
        parsedEn.size shouldBe 1
        parsedEn.first().title shouldBe "Chainsaw Man"
        parsedEn.first().cleanTitle shouldBe "chainsaw man"
        parsedEn.first().coverUrl shouldBe "https://uploads.mangadex.org/covers/manga-uuid-1/cover1.jpg.512.jpg"
        parsedEn.first().genres shouldBe listOf("Action")
        parsedEn.first().provider shouldBe "mangadex_trend"

        val parsedRu = parseMangaDexData(json, isRussianLocale = true)
        parsedRu.first().title shouldBe "Человек-бензопила"
        parsedRu.first().cleanTitle shouldBe "человек бензопила"
    }

    @Test
    fun `parseMangaDexData returns empty list on empty data`() {
        val json = Json.parseToJsonElement("""{"result":"ok","data":[]}""") as JsonObject
        parseMangaDexData(json) shouldBe emptyList()
    }

    @Test
    fun `list url respects nsfw toggle`() {
        val filtered = mangadexListUrl("order[followedCount]=desc", offset = 0, nsfwAllowed = false)
        filtered.contains("contentRating[]=safe") shouldBe true
        filtered.contains("contentRating[]=suggestive") shouldBe true
        filtered.contains("erotica") shouldBe false
        filtered.contains("pornographic") shouldBe false

        val unfiltered = mangadexListUrl("order[rating]=desc", offset = 30, nsfwAllowed = true)
        unfiltered.contains("offset=30") shouldBe true
        unfiltered.contains("contentRating[]=erotica") shouldBe true
        unfiltered.contains("contentRating[]=pornographic") shouldBe true
    }
}
