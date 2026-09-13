package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Task 9 (план aurora-heart-full-fix): B3-фолбэки отображения.
 * auroraDisplayTitle — чистая функция (без Locale-моков): payload-заголовок
 * побеждает; blank/"???" из achievements.json → локализованный фолбэк;
 * обычный title — как есть (translate() вызывающий сохраняет отдельно).
 * AuroraPayload.fallback() — минимальный display-payload для DB-unlocked
 * без локального vault (restore): статические цвета Aurora Prime, letter=null.
 */
class AuroraDisplayTest {

    // Заголовок: заработанный payload-заголовок побеждает любой achievementTitle
    @Test
    fun payloadTitleWinsOverAchievementTitle() {
        AuroraLocalization.auroraDisplayTitle("Зов Севера", "???", isEnglish = false) shouldBe "Зов Севера"
        AuroraLocalization.auroraDisplayTitle("Зов Севера", null, isEnglish = true) shouldBe "Зов Севера"
    }

    // «???» (плейсхолдер achievements.json) → локализованный фолбэк RU/EN
    @Test
    fun placeholderTitleFallsBackPerLanguage() {
        AuroraLocalization.auroraDisplayTitle(null, "???", isEnglish = false) shouldBe "Сердце Авроры"
        AuroraLocalization.auroraDisplayTitle(null, "???", isEnglish = true) shouldBe "Heart of Aurora"
    }

    // blank payload/achievement-заголовки → тот же фолбэк
    @Test
    fun blankTitlesFallBackPerLanguage() {
        AuroraLocalization.auroraDisplayTitle("   ", "", isEnglish = false) shouldBe "Сердце Авроры"
        AuroraLocalization.auroraDisplayTitle(null, null, isEnglish = true) shouldBe "Heart of Aurora"
    }

    // Обычный (не blank, не «???») title → как есть
    @Test
    fun ordinaryAchievementTitlePassesThrough() {
        AuroraLocalization.auroraDisplayTitle(null, "Обычное имя", isEnglish = true) shouldBe "Обычное имя"
        AuroraLocalization.auroraDisplayTitle("", "Обычное имя", isEnglish = false) shouldBe "Обычное имя"
    }

    // fallback(): контракт минимального display-payload (B3-restore)
    @Test
    fun fallbackPayloadCarriesStaticDisplayContract() {
        val payload = AuroraPayload.fallback()

        payload.kind shouldBe "final"
        payload.achievementTitle shouldBe "Сердце Авроры"
        payload.holderTitle shouldBe "Хранитель Авроры"
        payload.themeName shouldBe "Aurora Prime"
        // Task 13: En-пары фолбэка — display-путь EN-юзеров (restore без payload)
        payload.achievementTitleEn shouldBe "Heart of Aurora"
        payload.holderTitleEn shouldBe "Aurora Keeper"
        // Статический лайм-набор: AuroraPrimeColorScheme.darkScheme / AuroraPublicPalette
        payload.themeColors?.get("primary") shouldBe "#B6F04C"
        payload.themeColors?.get("secondary") shouldBe "#C25CFF"
        payload.themeColors?.get("accent") shouldBe "#2FC9A0"
        payload.themeColors?.get("background") shouldBe "#030810"
        payload.themeColors?.get("surface") shouldBe "#081020"
        // Секреты не изобретаются: письмо/очки/материал утрачены (nullable-поля схемы)
        payload.letter shouldBe null
        payload.letterEn shouldBe null
        payload.bonusPoints shouldBe null
        payload.themeMaterial shouldBe null
        payload.achievementDescription shouldBe null
        payload.descriptionEn shouldBe null
        payload.riddle shouldBe null
        payload.riddleEn shouldBe null
        payload.echoTitle shouldBe null
    }
}
