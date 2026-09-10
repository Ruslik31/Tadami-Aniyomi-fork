package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test

/**
 * Task 5 (план aurora-heart-full-fix): контракты Map-рефакторинга AuroraLocalization.
 * Чистый overload translate(text, languageCode) — без Locale-моков.
 * Старая семантика when(text.trim()) заморожена байт-в-байт: null → null;
 * "ru" → вход verbatim (guard ДО trim); Q5: любой язык != "ru" → EN;
 * промах по таблице → исходный (не trim) текст.
 */
class AuroraLocalizationTest {

    // (a) Гигиена всех ключей таблицы: перевод непустой и отличен от ключа (русского оригинала)
    @Test
    fun everyUiKeyHasNonBlankDistinctTranslation() {
        AuroraLocalization.UI_KEYS.shouldNotBeEmpty()
        AuroraLocalization.UI_KEYS.forEach { (ru, en) ->
            en.shouldNotBeBlank()
            en shouldNotBe ru
        }
    }

    @Test
    fun everyUiKeyIsAppliedForEnAndGuardedForRu() {
        AuroraLocalization.UI_KEYS.forEach { (ru, en) ->
            AuroraLocalization.translate(ru, "en") shouldBe en
            AuroraLocalization.translate(ru, "ru") shouldBe ru
        }
    }

    // Сохранение байт-в-байт: литералы зафиксированы на ПРЕД-рефакторинговой реализации (Фаза A).
    // Task 13: vault-derived ключи (3 загадки, «Сердце Авроры», описание, «Хранитель Авроры»,
    // письмо, «Продолжить путь») удалены — их заменяют En-поля payload; ассерты переехали
    // в removedVaultDerivedKeysPassThroughUntranslated.
    @Test
    fun existingKeysTranslateByteForByte() {
        AuroraLocalization.translate("Эхо I — Бессонница", "en") shouldBe "Echo I — Insomnia"
        AuroraLocalization.translate("Эхо II — Ориентир", "en") shouldBe "Echo II — Landmark"
        AuroraLocalization.translate("Эхо Авроры", "en") shouldBe "Aurora Echo"
        AuroraLocalization.translate("КОДЕКС АВРОРЫ", "en") shouldBe "AURORA CODEX"
        AuroraLocalization.translate("Зов", "en") shouldBe "The Call"
        AuroraLocalization.translate("Эхо", "en") shouldBe "Echo"
        AuroraLocalization.translate("Письмо", "en") shouldBe "The Letter"
        AuroraLocalization.translate("Пережить манифестацию снова", "en") shouldBe "Relive the Manifestation"
        // identity-ключ убран из таблицы: наблюдаемое поведение то же — else-ветвь возвращает вход
        AuroraLocalization.translate("Aurora Prime", "en") shouldBe "Aurora Prime"
        AuroraLocalization.translate("Сохранить в сердце", "en") shouldBe "Save to Heart"
        AuroraLocalization.translate("если слова бессильны — удержи загадку", "en") shouldBe
            "if words are powerless — hold the riddle"
        AuroraLocalization.translate("Прочитать эхо заново", "en") shouldBe "Replay the echo"
        AuroraLocalization.translate("Аврора заметила тебя в этот час...", "en") shouldBe
            "Aurora noticed you at this hour..."
        AuroraLocalization.translate("Некоторые границы тоньше других.", "en") shouldBe
            "Some boundaries are thinner than others."
        AuroraLocalization.translate("Скрыто северным сиянием", "en") shouldBe "Hidden by the aurora"
    }

    // Task 13 (§4): translate удалённой vault-строки теперь возвращает вход (промах по таблице)
    @Test
    fun removedVaultDerivedKeysPassThroughUntranslated() {
        AuroraLocalization.translate("Сердце Авроры", "en") shouldBe "Сердце Авроры"
        AuroraLocalization.translate("Хранитель Авроры", "en") shouldBe "Хранитель Авроры"
        AuroraLocalization.translate("Продолжить путь", "en") shouldBe "Продолжить путь"
    }

    // Task 13 (Q5): localized(ru, en) — выбор локали для полей payload; чистый overload для тестов
    @Test
    fun localizedPicksEnWithRuFallback() {
        AuroraLocalization.localized("Сердце Авроры", "Heart of Aurora", isEnglish = true) shouldBe
            "Heart of Aurora"
        AuroraLocalization.localized("Сердце Авроры", null, isEnglish = true) shouldBe "Сердце Авроры"
        AuroraLocalization.localized("Сердце Авроры", "Heart of Aurora", isEnglish = false) shouldBe
            "Сердце Авроры"
        AuroraLocalization.localized(null, "Heart of Aurora", isEnglish = true) shouldBe "Heart of Aurora"
        AuroraLocalization.localized(null, null, isEnglish = true) shouldBe null
    }

    // (b) C1a: новый ключ «Зов Авроры» (echoTitle первой вспышки из AuroraHeartManager)
    @Test
    fun zovAuroryTranslatesToCallOfAurora() {
        AuroraLocalization.translate("Зов Авроры", "en") shouldBe "Call of Aurora"
        AuroraLocalization.translate("Зов Авроры", "ru") shouldBe "Зов Авроры"
    }

    // (c) Q5: любая не-"ru" локаль получает EN (семантика language != "ru" сохранена)
    @Test
    fun anyNonRussianLanguageGetsEnglishQ5() {
        AuroraLocalization.translate("КОДЕКС АВРОРЫ", "uk") shouldBe "AURORA CODEX"
        AuroraLocalization.translate("Зов", "de") shouldBe "The Call"
        AuroraLocalization.translate("Зов Авроры", "uk") shouldBe "Call of Aurora"
    }

    // (d) Неизвестная строка → исходная (не trim); null → null в обеих локалях
    @Test
    fun unknownTextPassesThroughUntrimmedAndNullStaysNull() {
        AuroraLocalization.translate("просто строка", "en") shouldBe "просто строка"
        AuroraLocalization.translate("  неизвестная строка  ", "en") shouldBe "  неизвестная строка  "
        AuroraLocalization.translate(null, "en") shouldBe null
        AuroraLocalization.translate(null, "ru") shouldBe null
    }

    // (e) C1c: шаблон темы вместо exact-ключа «Открыта тема: Aurora Prime»
    @Test
    fun themeUnlockUsesTemplatePerLanguage() {
        AuroraLocalization.translateThemeUnlock("Aurora Prime", "en") shouldBe "Theme unlocked: Aurora Prime"
        AuroraLocalization.translateThemeUnlock("Aurora Prime", "ru") shouldBe "Открыта тема: Aurora Prime"
        AuroraLocalization.translateThemeUnlock("Aurora Prime", "uk") shouldBe "Theme unlocked: Aurora Prime"
        AuroraLocalization.translateThemeUnlock(null, "en") shouldBe null
        AuroraLocalization.translateThemeUnlock("   ", "ru") shouldBe null
    }

    // (f) trim-семантика: lookup по trim, ru-guard возвращает вход verbatim (до trim)
    @Test
    fun trimAppliesToLookupOnlyAndRuReturnsInputVerbatim() {
        AuroraLocalization.translate("  Зов  ", "en") shouldBe "The Call"
        AuroraLocalization.translate("\nЭхо Авроры\t", "en") shouldBe "Aurora Echo"
        AuroraLocalization.translate("  Зов  ", "ru") shouldBe "  Зов  "
    }
}
