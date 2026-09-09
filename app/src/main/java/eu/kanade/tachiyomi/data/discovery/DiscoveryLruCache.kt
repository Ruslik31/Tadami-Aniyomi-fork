package eu.kanade.tachiyomi.data.discovery

/**
 * Простой потокобезопасный LRU-кэш на чистом Kotlin/JVM.
 * Не зависит от Android SDK (android.util.LruCache), что делает его безопасным
 * для использования как в рантайме Android, так и в быстрых JVM юнит-тестах.
 */
class DiscoveryLruCache<K, V>(private val maxSize: Int) {
    init {
        require(maxSize > 0) { "maxSize must be > 0" }
    }

    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun snapshot(): Map<K, V> = LinkedHashMap(map)
}
