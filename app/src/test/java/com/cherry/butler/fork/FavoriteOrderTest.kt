package com.cherry.butler.fork

import com.cherry.butler.core.model.Character
import com.cherry.butler.core.model.FavoriteEntry
import com.cherry.butler.core.model.FavoriteOrder
import com.cherry.butler.core.model.FavoriteSorting
import com.cherry.butler.feature.browse.PageWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteOrderTest {
    private fun c(id: String, chats: Long = 0) = Character(
        id = id, name = id, avatar = null, creatorName = "", creatorVerified = false, creatorPlusBadge = false,
        chatCount = chats, messageCount = 0, isNsfw = false, isImageNsfw = false, tagNames = emptyList(), customTags = emptyList(),
    )

    private val list = listOf(
        FavoriteEntry(c("a", 5), 0, favoritedAt = null, createdAt = 300),
        FavoriteEntry(c("b", 9), 1, favoritedAt = 2_000, createdAt = 100),
        FavoriteEntry(c("c", 1), 2, favoritedAt = 1_000, createdAt = 200),
        FavoriteEntry(c("d", 3), 3, favoritedAt = null, createdAt = null),
    )

    private fun ids(o: FavoriteOrder) = FavoriteSorting.sort(list, o).map { it.character.id }

    @Test fun newestHeartsFirstThenJanitorOrder() = assertEquals(listOf("b", "c", "a", "d"), ids(FavoriteOrder.FavedNewest))
    @Test fun oldestHeartsKeepUndatedFirst() = assertEquals(listOf("d", "a", "c", "b"), ids(FavoriteOrder.FavedOldest))
    @Test fun botsByCreation() {
        assertEquals(listOf("a", "c", "b", "d"), ids(FavoriteOrder.BotNewest))
        assertEquals(listOf("b", "c", "a", "d"), ids(FavoriteOrder.BotOldest))
    }
    @Test fun mostChats() = assertEquals(listOf("b", "a", "d", "c"), ids(FavoriteOrder.MostChats))

    @Test fun pageWindow() {
        assertEquals(listOf(1, 2, 3), PageWindow.numbers(1, 3))
        assertEquals(listOf(1, 8, 9, 10, 11, 12, 20), PageWindow.numbers(10, 20))
        assertEquals(listOf(1), PageWindow.numbers(1, 1))
    }
}
