package eu.kanade.domain.easteregg.aurora

/**
 * Чистая логика счётчика УНИКАЛЬНЫХ ночей пасхалки «Сердце Авроры»
 * (Pillar 2 плана): ночь засчитывается один раз за локальные сутки.
 * Объект не знает ни о часах, ни о prefs — ключ суток и текущий счётчик
 * передаёт вызывающий (AuroraHeartManager), сравнение ключей СТРОКОВОЕ.
 */
object AuroraNights {

    /** Итог проверки ночи: счётчик, зафиксированный ключ суток и признак новой ночи. */
    data class NightAdvance(val count: Int, val lastNightKey: String, val isNewNight: Boolean)

    fun advance(lastNightKey: String?, todayKey: String, currentCount: Int): NightAdvance {
        val isNewNight = lastNightKey != todayKey
        return NightAdvance(
            count = if (isNewNight) currentCount + 1 else currentCount,
            lastNightKey = todayKey,
            isNewNight = isNewNight,
        )
    }
}
