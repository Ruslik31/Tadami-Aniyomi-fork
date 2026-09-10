package eu.kanade.domain.easteregg.aurora

import kotlinx.serialization.Serializable

/**
 * Содержимое расшифрованной ступени.
 * kind = "riddle" — промежуточное эхо со следующей загадкой.
 * kind = "final" — финал: достижение, титул, тема, письмо, очки.
 * Всё это существует только внутри шифртекста до момента разгадки.
 * En-поля (Task 13): null в приватном сценарии без En — экраны
 * фолбэчатся на RU (`en ?: ru`, AuroraLocalization.localized).
 */
@Serializable
data class AuroraPayload(
    val kind: String,
    val riddle: String? = null,
    val riddleEn: String? = null,
    val echoTitle: String? = null,
    val achievementTitle: String? = null,
    val achievementTitleEn: String? = null,
    val achievementDescription: String? = null,
    val descriptionEn: String? = null,
    val holderTitle: String? = null,
    val holderTitleEn: String? = null,
    val letter: String? = null,
    val letterEn: String? = null,
    val themeName: String? = null,
    val themeColors: Map<String, String>? = null,
    val themeMaterial: Map<String, String>? = null,
    val bonusPoints: Int? = null,
) {
    companion object {
        /**
         * Task 9 (B3): минимальный display-payload для случая «достижение
         * открыто в DB, но локальный vault пуст» (backup/restore: шифрованный
         * payload намеренно не входит в backup). ТОЛЬКО для display-пути
         * (AchievementCard → CodexScreen): шина/хуки/rewarder его НЕ получают.
         * Секретное письмо утрачено (letter=null — секция не рендерится);
         * цвета — статическая палитра Aurora Prime (AuroraPrimeColorScheme
         * darkScheme / AuroraPublicPalette), не секрет ваулта.
         */
        fun fallback(): AuroraPayload = AuroraPayload(
            kind = "final",
            achievementTitle = "Сердце Авроры",
            achievementTitleEn = "Heart of Aurora",
            holderTitle = "Хранитель Авроры",
            holderTitleEn = "Aurora Keeper",
            themeName = "Aurora Prime",
            themeColors = mapOf(
                "primary" to "#B6F04C",
                "secondary" to "#C25CFF",
                "accent" to "#2FC9A0",
                "background" to "#030810",
                "surface" to "#081020",
            ),
            themeMaterial = null,
            letter = null,
            bonusPoints = null,
        )
    }
}
