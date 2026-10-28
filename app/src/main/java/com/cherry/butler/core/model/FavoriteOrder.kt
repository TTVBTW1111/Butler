package com.cherry.butler.core.model

/**
 * How the Favorites list is ordered (fork feature). [Janitor] is the server's own order;
 * every other one is sorted in the app from your whole favourites list.
 */
enum class FavoriteOrder(val label: String, val short: String) {
    Janitor("Janitor's order", "Default"),
    FavedNewest("Favourited: newest first", "Newest ♥"),
    FavedOldest("Favourited: oldest first", "Oldest ♥"),
    BotNewest("Bot created: newest first", "Newest bots"),
    BotOldest("Bot created: oldest first", "Oldest bots"),
    MostChats("Most chats", "Most chats"),
    Name("Name A–Z", "A–Z"),
}

/**
 * One favourite with what ordering needs. [favoritedAt] is when you hearted it, if Janitor
 * says or Butler saw you do it; [position] is where Janitor listed it.
 */
data class FavoriteEntry(
    val character: Character,
    val position: Int,
    val favoritedAt: Long? = null,
    val createdAt: Long? = null,
)

object FavoriteSorting {
    /**
     * Sorted per [order]. Favourites with no known heart date keep Janitor's order among
     * themselves, after the dated ones (newest first) or before them (oldest first).
     */
    fun sort(entries: List<FavoriteEntry>, order: FavoriteOrder): List<FavoriteEntry> = when (order) {
        FavoriteOrder.Janitor -> entries.sortedBy { it.position }
        FavoriteOrder.FavedNewest -> entries.sortedWith(compareByDescending<FavoriteEntry> { it.favoritedAt ?: Long.MIN_VALUE }.thenBy { it.position })
        FavoriteOrder.FavedOldest -> entries.sortedWith(compareBy<FavoriteEntry> { it.favoritedAt ?: Long.MIN_VALUE }.thenByDescending { it.position })
        FavoriteOrder.BotNewest -> entries.sortedWith(compareByDescending<FavoriteEntry> { it.createdAt ?: Long.MIN_VALUE }.thenBy { it.position })
        FavoriteOrder.BotOldest -> entries.sortedWith(compareBy<FavoriteEntry> { it.createdAt ?: Long.MAX_VALUE }.thenBy { it.position })
        FavoriteOrder.MostChats -> entries.sortedWith(compareByDescending<FavoriteEntry> { it.character.chatCount }.thenBy { it.position })
        FavoriteOrder.Name -> entries.sortedWith(compareBy<FavoriteEntry> { it.character.name.lowercase() }.thenBy { it.position })
    }
}
