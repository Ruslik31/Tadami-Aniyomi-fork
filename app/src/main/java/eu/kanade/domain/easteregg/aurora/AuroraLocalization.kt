package eu.kanade.domain.easteregg.aurora

import java.util.Locale

/**
 * Тексты пасхалки намеренно захардкожены в Kotlin вне :i18n/:i18n-aniyomi —
 * стелс-дизайн: осознанное отклонение от правила AGENTS.md «String Resources»,
 * чтобы таблица переводов не была видна в ресурсах приложения.
 * Task 13 исполнен: секретные тексты живут только в зашифрованном ваулте,
 * их EN-варианты — En-поля payload (выбор локали — [localized]).
 */
object AuroraLocalization {

    /** Q5 (решение пользователя): семантика заморожена — `language != "ru"`, все нерусские локали получают EN. */
    fun isEnglish(): Boolean = Locale.getDefault().language != "ru"

    /**
     * Task 9 (B3): чистый фолбэк заголовка карточки «Сердце Авроры».
     * Заработанный payload-заголовок побеждает; иначе если заголовок ачивки
     * из achievements.json blank или «???» (плейсхолдер) — локализованное
     * имя, чтобы ОТКРЫТАЯ ачивка никогда не осталась «???».
     * Перевод payload-заголовка вызывающий сохраняет через translate().
     */
    fun auroraDisplayTitle(payloadTitle: String?, achievementTitle: String?, isEnglish: Boolean): String {
        if (!payloadTitle.isNullOrBlank()) return payloadTitle
        if (achievementTitle.isNullOrBlank() || achievementTitle == "???") {
            return if (isEnglish) "Heart of Aurora" else "Сердце Авроры"
        }
        return achievementTitle
    }

    /**
     * Task 13 (Q5): выбор локали для полей vault-payload — EN-юзеру En-вариант,
     * при отсутствии En (приватный сценарий) фолбэк на RU. Чистый overload — для тестов.
     */
    fun localized(ru: String?, en: String?): String? = localized(ru, en, isEnglish())

    internal fun localized(ru: String?, en: String?, isEnglish: Boolean): String? =
        if (isEnglish) (en ?: ru) else ru

    /**
     * RU→EN таблица UI-микрокопи пасхалки (Task 13: vault-derived ключи — загадки,
     * финальные тексты, «Продолжить путь» — удалены, их заменяют En-поля payload).
     */
    internal val UI_KEYS: Map<String, String> = mapOf(
        // Echo titles
        "Эхо I — Бессонница" to "Echo I — Insomnia",
        "Эхо II — Ориентир" to "Echo II — Landmark",
        "Эхо Авроры" to "Aurora Echo",
        // C1a: echoTitle первой вспышки из AuroraHeartManager — EN-юзеры не должны видеть русский
        "Зов Авроры" to "Call of Aurora",

        // Codex titles
        "КОДЕКС АВРОРЫ" to "AURORA CODEX",
        "Зов" to "The Call",
        "Эхо" to "Echo",
        "Письмо" to "The Letter",
        "Пережить манифестацию снова" to "Relive the Manifestation",

        // UI microcopy + whispers
        "Сохранить в сердце" to "Save to Heart",
        "если слова бессильны — удержи загадку" to "if words are powerless — hold the riddle",
        "Прочитать эхо заново" to "Replay the echo",
        "Аврора заметила тебя в этот час..." to "Aurora noticed you at this hour...",
        "Некоторые границы тоньше других." to "Some boundaries are thinner than others.",
        "Скрыто северным сиянием" to "Hidden by the aurora",
    )

    fun translate(text: String?): String? = translate(text, Locale.getDefault().language)

    /**
     * Чистый overload без Locale — для тестов. Семантика 1-в-1 со старым `when(text.trim())`:
     * null → null; ru-guard возвращает вход ДО trim; промах — исходный (не trim) текст.
     */
    internal fun translate(text: String?, languageCode: String): String? {
        if (text == null) return null
        if (languageCode == "ru") return text
        return UI_KEYS[text.trim()] ?: text
    }

    fun translateThemeUnlock(themeName: String?): String? =
        translateThemeUnlock(themeName, Locale.getDefault().language)

    /**
     * C1c: шаблон вместо exact-ключа «Открыта тема: Aurora Prime».
     * null/blank → null — рендерить ли строку, решает вызывающий.
     */
    internal fun translateThemeUnlock(themeName: String?, languageCode: String): String? {
        if (themeName.isNullOrBlank()) return null
        return if (languageCode == "ru") "Открыта тема: $themeName" else "Theme unlocked: $themeName"
    }
}
