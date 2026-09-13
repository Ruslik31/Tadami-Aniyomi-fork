package eu.kanade.tachiyomi.ui.reels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

/**
 * Voyager Java-serializes every stacked Screen into the activity saved state. A feed screen
 * carrying the user's whole reels-favorites list produced a ~604 KB parcel and crashed the
 * process with TransactionTooLargeException on activity stop; keep the payload lightweight.
 */
class ReelsFeedScreenSavedStateTest {

    @Test
    fun `pushed feed screen serializes to a small saved-state payload`() {
        val screen = ReelsFeedScreen(
            sourceId = 301L,
            offlinePlaylist = true,
            playlistSort = FavoritesSort.DateAsc,
            initialVideoId = "video-1",
            creator = "author",
            followingFeed = true,
            customFeedId = "feed-1",
            customFeedName = "My feed",
            nicheId = "niche-1",
            nicheName = "Niche",
        )

        val bytes = ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(screen) }
            buffer.toByteArray()
        }

        (bytes.size < 1024) shouldBe true
    }

    @Test
    fun `feed screen constructor carries no collection payloads`() {
        val hasCollectionParameter = ReelsFeedScreen::class.java.constructors
            .flatMap { constructor -> constructor.parameterTypes.toList() }
            .any { type -> Collection::class.java.isAssignableFrom(type) }

        hasCollectionParameter shouldBe false
    }
}
