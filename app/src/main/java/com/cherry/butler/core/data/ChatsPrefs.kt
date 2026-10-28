package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.model.ChatFilter
import com.cherry.butler.core.model.ChatLength
import com.cherry.butler.core.model.ChatPeriod
import com.cherry.butler.core.model.ChatSort
import com.cherry.butler.core.model.ChatsView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The Chats screen's sort, filters and view, kept on this device (fork). Personas aren't kept. */
@Singleton
class ChatsPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fork_chats", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> read(key: String, default: E): E =
        prefs.getString(key, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private val _filter = MutableStateFlow(
        ChatFilter(
            sort = read("sort", ChatSort.Recent),
            period = read("period", ChatPeriod.Any),
            length = read("length", ChatLength.Any),
            multiOnly = prefs.getBoolean("multi", false),
            pinnedOnly = prefs.getBoolean("pinned_only", false),
            pinnedFirst = prefs.getBoolean("pinned_first", true),
            inMessages = prefs.getBoolean("in_messages", false),
        ),
    )
    val filter: StateFlow<ChatFilter> = _filter.asStateFlow()

    private val _view = MutableStateFlow(read("view", ChatsView.List))
    val view: StateFlow<ChatsView> = _view.asStateFlow()

    fun setFilter(f: ChatFilter) {
        _filter.value = f
        prefs.edit()
            .putString("sort", f.sort.name).putString("period", f.period.name).putString("length", f.length.name)
            .putBoolean("multi", f.multiOnly).putBoolean("pinned_only", f.pinnedOnly)
            .putBoolean("pinned_first", f.pinnedFirst).putBoolean("in_messages", f.inMessages)
            .apply()
    }

    fun setView(v: ChatsView) { _view.value = v; prefs.edit().putString("view", v.name).apply() }
}
