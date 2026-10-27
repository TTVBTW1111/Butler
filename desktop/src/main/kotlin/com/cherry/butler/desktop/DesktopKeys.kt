package com.cherry.butler.desktop

import com.cherry.butler.ui.components.RefreshHandle

/** Window-wide shortcuts that reach into whichever screen is showing. */
object DesktopKeys {
    /** The refresh of whatever screen is on show, offered by the screens themselves. */
    val screen = RefreshHandle()

    /** The Chats archive's toggle, registered by the list itself. */
    val chatsArchive = RefreshHandle()

    /** Ctrl+F: search in the open chat, registered by the chat screen while it is shown. */
    val chatFind = RefreshHandle()

    /** Set by the shell: Ctrl+H, the Chats archive opened (from any section) or closed. */
    @Volatile var archive: (() -> Boolean)? = null

    /** Set by the shell: refreshes the screen on show, if it can. True when something refreshed. */
    @Volatile var refresh: (() -> Boolean)? = null
}
