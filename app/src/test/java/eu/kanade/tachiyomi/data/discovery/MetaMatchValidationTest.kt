package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test

class MetaMatchValidationTest {

    @Test
    fun `exact prefix and contains matches pass the threshold`() {
        metaMatchesTitle("Solo Leveling", listOf(null, "solo leveling")) shouldBe true
        metaMatchesTitle("Solo Leveling", listOf("Solo Leveling: Special Edition")) shouldBe true
        metaMatchesTitle("Frieren", listOf("Frieren: Beyond Journey's End")) shouldBe true
        metaMatchesTitle("Attack on Titan", listOf("Shingeki no Kyojin", "Attack on Titan")) shouldBe true
    }

    @Test
    fun `weak or absent matches are rejected`() {
        // token overlap 1/2 * 50 = 25 < threshold
        metaMatchesTitle("Solo Leveling", listOf("Leveling System")) shouldBe false
        metaMatchesTitle("Берсерк", listOf("Berserk")) shouldBe false
        metaMatchesTitle("Naruto", listOf("Bleach")) shouldBe false
        metaMatchesTitle("Re:Zero", listOf("", null)) shouldBe false
        metaMatchesTitle("Re:Zero", listOf("Some Other Novel")) shouldBe false
    }
}
