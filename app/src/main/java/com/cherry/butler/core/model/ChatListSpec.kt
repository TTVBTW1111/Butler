package com.cherry.butler.core.model

/** How the Chats list is ordered (fork). The ordinal is the DAO's sort number. */
enum class ChatSort(val label: String) {
    Recent("Recent activity"),
    Oldest("Oldest activity"),
    NameAz("Name A–Z"),
    NameZa("Name Z–A"),
    MostChats("Most chats"),
    MostMessages("Most messages"),
}

/** Last activity within a window (fork). */
enum class ChatPeriod(val label: String, val days: Int?, val olderThan: Boolean = false) {
    Any("Any time", null),
    Today("Today", 1),
    Week("Past week", 7),
    Month("Past month", 30),
    Quarter("Past 3 months", 90),
    Older("Older than 3 months", 90, olderThan = true),
}

/** Smallest conversation to show (fork), by messages across a character's chats. */
enum class ChatLength(val label: String, val min: Int) {
    Any("Any length", 0),
    Started("10+ messages", 10),
    Long("100+ messages", 100),
    Epic("500+ messages", 500),
}

/** View of the Chats list (fork). */
enum class ChatsView(val label: String) { List("List"), Cards("Cards") }

/** The filter settings you pick on the Chats screen; kept between visits (fork). */
data class ChatFilter(
    val sort: ChatSort = ChatSort.Recent,
    val period: ChatPeriod = ChatPeriod.Any,
    val length: ChatLength = ChatLength.Any,
    val multiOnly: Boolean = false,
    val pinnedOnly: Boolean = false,
    val pinnedFirst: Boolean = true,
    /** Search the words of messages too, not only names. */
    val inMessages: Boolean = false,
    /** Only chats played as these personas (null: any); with [profile], as your profile too. */
    val personaIds: List<String>? = null,
    val personaLabel: String? = null,
    val profile: Boolean = false,
) {
    /** Anything that changes which rows show or their order (not the search words). */
    val active: Boolean
        get() = sort != ChatSort.Recent || period != ChatPeriod.Any || length != ChatLength.Any ||
            multiOnly || pinnedOnly || !pinnedFirst || personaIds != null

    val count: Int
        get() = listOf(period != ChatPeriod.Any, length != ChatLength.Any, multiOnly, pinnedOnly, personaIds != null).count { it }
}

/** Everything the Chats query needs (fork): [ChatFilter] plus the tab and search words. */
data class ChatListSpec(
    val query: String = "",
    val inMessages: Boolean = false,
    val folder: String? = null,
    /** -1 both, 0 the main list, 1 the archive. */
    val archived: Int = 0,
    val personaIds: List<String>? = null,
    val orProfile: Boolean = false,
    val since: Long = 0,
    val until: Long = 0,
    val multiOnly: Boolean = false,
    val pinnedOnly: Boolean = false,
    val minMessages: Int = 0,
    val pinnedFirst: Boolean = true,
    val sort: ChatSort = ChatSort.Recent,
) {
    companion object {
        private const val DAY = 24L * 60 * 60 * 1000

        fun of(filter: ChatFilter, query: String, folder: String?, archived: Int, now: Long): ChatListSpec {
            val p = filter.period
            val edge = p.days?.let { now - it * DAY } ?: 0L
            return ChatListSpec(
                query = query.trim(),
                inMessages = filter.inMessages,
                folder = folder,
                archived = archived,
                personaIds = filter.personaIds,
                orProfile = filter.profile,
                since = if (p.days != null && !p.olderThan) edge else 0L,
                until = if (p.olderThan) edge else 0L,
                multiOnly = filter.multiOnly,
                pinnedOnly = filter.pinnedOnly,
                minMessages = filter.length.min,
                pinnedFirst = filter.pinnedFirst,
                sort = filter.sort,
            )
        }
    }
}
