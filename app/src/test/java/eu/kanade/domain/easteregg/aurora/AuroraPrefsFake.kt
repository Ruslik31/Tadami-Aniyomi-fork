package eu.kanade.domain.easteregg.aurora

import android.content.SharedPreferences

/**
 * In-memory [SharedPreferences] для JVM-тестов пасхалки «Сердце Авроры»
 * (без Robolectric и вызовов заглушек android.jar): все геттеры читают
 * одну карту, apply() мгновенный, commit() всегда возвращает true.
 * Шаблон: InMemorySharedPreferences из UnlockableManagerTest (:data).
 *
 * Общая утилита всех aurora-тестов плана aurora-heart-full-fix.
 * Слушатели в aurora-коде не используются (grep 2026-09-10: ноль вызовов
 * register/unregisterOnSharedPreferenceChangeListener) — регистрация
 * бросает, чтобы новое использование не прошло в тестах молча.
 */
internal class AuroraPrefsFake(initial: Map<String, Any> = emptyMap()) : SharedPreferences {

    private val backing = HashMap<String, Any?>(initial)

    override fun getAll(): Map<String, *> = backing.toMap()

    override fun getString(key: String, defValue: String?): String? =
        backing[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST")
        (backing[key] as? Set<String>) ?: defValues

    override fun getInt(key: String, defValue: Int): Int =
        (backing[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (backing[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (backing[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (backing[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = backing.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = throw UnsupportedOperationException("AuroraPrefsFake: listeners are unused by aurora code")

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = throw UnsupportedOperationException("AuroraPrefsFake: listeners are unused by aurora code")

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removals = LinkedHashSet<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

        override fun remove(key: String) = apply {
            removals += key
            pending.remove(key)
        }

        override fun clear() = apply {
            clearAll = true
            pending.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) backing.clear()
            removals.forEach { backing.remove(it) }
            backing.putAll(pending)
        }
    }
}
