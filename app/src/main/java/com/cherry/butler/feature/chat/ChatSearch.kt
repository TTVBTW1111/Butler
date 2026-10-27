package com.cherry.butler.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.ui.components.SearchBox

/**
 * Search in the open chat (fork feature). It looks at what the chat shows: every line of
 * yours and the chosen reply of each turn, not the swipes behind it. A turn matches when
 * every word of the query is in it, in any order, case aside.
 */
object ChatSearch {
    /** The words looked for: split on spaces, empty ones and repeats dropped. */
    fun terms(query: String): List<String> =
        query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    fun matches(text: String, terms: List<String>): Boolean =
        terms.isNotEmpty() && terms.all { text.contains(it, ignoreCase = true) }

    /** The indices of the turns that match, oldest first. */
    fun hits(turns: List<Turn>, terms: List<String>): List<Int> {
        if (terms.isEmpty()) return emptyList()
        return turns.indices.filter { i -> matches(textOf(turns[i]), terms) }
    }

    fun textOf(turn: Turn): String = when (turn) {
        is Turn.User -> turn.message.text
        is Turn.Bot -> turn.shown.text
    }

    /** One step through [count] hits from [at]; wraps at both ends. -1 when there are none. */
    fun step(at: Int, by: Int, count: Int): Int =
        if (count == 0) -1 else ((if (at < 0) count else at) + by).mod(count)
}

/**
 * The row under the chat's title while searching: the box, where you are among the hits,
 * up for older and down for newer, and close. Enter goes to the next older hit, Shift+Enter
 * back toward the newest, as in a browser's find.
 */
@Composable
internal fun ChatSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    position: Int,
    total: Int,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, bottom = 8.dp)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    if (e.isShiftPressed) onNewer() else onOlder()
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchBox(
            value = query,
            onValueChange = onQuery,
            placeholder = "Search this chat",
            onSubmit = onOlder,
            focusRequester = focusRequester,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                query.isBlank() -> ""
                total == 0 -> "None"
                else -> "${position + 1} of $total"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (query.isNotBlank() && total == 0) MaterialTheme.colorScheme.error else ButlerTheme.colors.textMed,
            maxLines = 1,
        )
        IconButton(onClick = onOlder, enabled = total > 0) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Older match", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onNewer, enabled = total > 0) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Newer match", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Close search", tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
        }
    }
}
