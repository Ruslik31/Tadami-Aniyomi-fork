package eu.kanade.domain.easteregg.aurora

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Нормализация — единственный «контракт» между Kotlin и tools/aurora_forge.mjs.
 * Если этот тест падает — ваулт не откроется никогда.
 * ВАЖНО: здесь только нейтральные строки — ответов квеста в коде НЕТ.
 */
class AuroraNormalizeTest {

    @Test
    fun collapseWhitespaceAndTrim() {
        assertEquals("полярная ночь", AuroraVault.normalize("  Полярная   НОЧЬ "))
    }

    @Test
    fun yoMapsToYe() {
        assertEquals("зеленый луч", AuroraVault.normalize("Зелёный луч"))
    }

    @Test
    fun channelPrefixesSurvive() {
        assertEquals("sigil:1-2-3", AuroraVault.normalize("SIGIL:1-2-3"))
        assertEquals("категория:пример", AuroraVault.normalize("Категория:ПРИМЕР"))
    }

    // Task 13 (K2c): полный unicode-набор пробелов — зеркалирует JS `\s` в tools/aurora_forge.mjs.

    @Test
    fun unicodeSpacesCollapseAndTrim() {
        // NBSP в середине → одиночный ASCII-пробел
        assertEquals("полярная ночь", AuroraVault.normalize("Полярная\u00A0НОЧЬ"))
        // EM SPACE по краям → убирается trim'ом
        assertEquals("полярная ночь", AuroraVault.normalize("\u2003Полярная ночь\u2003"))
        // Смесь unicode-пробелов в середине → один пробел
        assertEquals("полярная ночь", AuroraVault.normalize("Полярная\u2000\u202Fночь"))
    }

    @Test
    fun bomAndZwnbspAreStrippedOrCollapsed() {
        // BOM-префикс → удаляется
        assertEquals("полярная ночь", AuroraVault.normalize("\uFEFFПолярная ночь"))
        // ZWNBSP в середине → одиночный пробел
        assertEquals("полярная ночь", AuroraVault.normalize("Полярная\uFEFFночь"))
        // BOM-суффикс → удаляется
        assertEquals("полярная ночь", AuroraVault.normalize("Полярная ночь\uFEFF"))
    }
}
