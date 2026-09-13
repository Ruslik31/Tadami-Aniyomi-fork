package eu.kanade.tachiyomi.data.discovery

import io.kotest.matchers.shouldBe
import org.junit.Test
import kotlin.concurrent.thread

class DiscoveryLruCacheTest {

    @Test
    fun `cache respects maximum capacity and evicts eldest entry`() {
        val cache = DiscoveryLruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        cache.size() shouldBe 3
        cache.get("a") shouldBe 1

        // Accessing "a" makes "b" the eldest entry
        cache.put("d", 4)

        cache.size() shouldBe 3
        cache.get("b") shouldBe null
        cache.get("a") shouldBe 1
        cache.get("c") shouldBe 3
        cache.get("d") shouldBe 4
    }

    @Test
    fun `clear empties cache`() {
        val cache = DiscoveryLruCache<String, String>(5)
        cache.put("k1", "v1")
        cache.put("k2", "v2")
        cache.size() shouldBe 2

        cache.clear()
        cache.size() shouldBe 0
        cache.get("k1") shouldBe null
    }

    @Test
    fun `cache handles concurrent writes and reads safely`() {
        val cache = DiscoveryLruCache<Int, String>(100)
        val threads = (1..10).map { threadId ->
            thread {
                for (i in 0 until 500) {
                    val key = (threadId * 1000) + i
                    cache.put(key, "val-$key")
                    cache.get(key)
                }
            }
        }
        threads.forEach { it.join() }
        cache.size() shouldBe 100
    }
}
