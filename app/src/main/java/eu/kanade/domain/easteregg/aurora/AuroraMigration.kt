package eu.kanade.domain.easteregg.aurora

/**
 * Task 9 (B3): чистая логика решения миграции ваулта «Сердца Авроры» —
 * без SharedPreferences/Context, тестируемо изолированно (AuroraMigrationTest).
 *
 * Правило (дословно из Плана v2):
 *  - `storedRev == currentRev && (!unlocked || payloadHasThemeMaterial)` → noop;
 *  - иначе → wipe прогресс-ключей, preserve DONE+PAYLOAD для уже открытого
 *    квеста (restore не должен превращать заработанное достижение в «???»)
 *    и updateRev.
 *
 * Примечание: в ветке «rev совпал, но payload без themeMaterial» preserve
 * сохраняет устаревший payload — допустимо (отображение работает через
 * фолбэки; Task 12 сделает отсутствие themeMaterial ошибкой форжа).
 */
object AuroraMigration {

    data class Decision(val wipe: Boolean, val preserveDoneAndPayload: Boolean, val updateRev: Boolean)

    fun decide(
        storedRev: Int,
        currentRev: Int,
        unlocked: Boolean,
        payloadHasThemeMaterial: Boolean,
    ): Decision {
        val upToDate = storedRev == currentRev && (!unlocked || payloadHasThemeMaterial)
        return if (upToDate) {
            Decision(wipe = false, preserveDoneAndPayload = false, updateRev = false)
        } else {
            Decision(wipe = true, preserveDoneAndPayload = unlocked, updateRev = true)
        }
    }
}
