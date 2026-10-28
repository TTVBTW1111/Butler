package com.cherry.butler.feature.chats

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.markdown.fillNames
import com.cherry.butler.core.model.ChatFilter
import com.cherry.butler.core.model.ChatGroup
import com.cherry.butler.core.model.ChatLength
import com.cherry.butler.core.model.ChatPeriod
import com.cherry.butler.core.model.ChatSort
import com.cherry.butler.core.model.ChatsView
import com.cherry.butler.core.util.RelativeTime
import com.cherry.butler.feature.browse.DropSegment
import com.cherry.butler.feature.browse.Segment
import com.cherry.butler.ui.components.CharacterTile

/**
 * The Chats toolbar (fork): sort, when, length, persona and the quick filters, then the
 * archive, the view and select. Scrolls sideways where the window is narrow.
 */
@Composable
fun ChatsToolbar(
    filter: ChatFilter,
    view: ChatsView,
    searching: Boolean,
    archivedCount: Int,
    inArchive: Boolean,
    personas: List<PersonaOption>,
    onFilter: ((ChatFilter) -> ChatFilter) -> Unit,
    onPersona: (PersonaOption?) -> Unit,
    onClear: () -> Unit,
    onView: (ChatsView) -> Unit,
    onArchive: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Group {
            val sorts = ChatSort.entries
            DropSegment(
                label = filter.sort.label, icon = Icons.Rounded.SwapVert, selected = filter.sort != ChatSort.Recent,
                options = sorts.map { it.label }, chosen = sorts.indexOf(filter.sort),
                onPick = { i -> onFilter { it.copy(sort = sorts[i]) } },
                modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 150.dp),
            )
            val periods = ChatPeriod.entries
            DropSegment(
                label = filter.period.label, icon = Icons.Rounded.Schedule, selected = filter.period != ChatPeriod.Any,
                options = periods.map { it.label }, chosen = periods.indexOf(filter.period),
                onPick = { i -> onFilter { it.copy(period = periods[i]) } },
                modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 120.dp),
            )
            val lengths = ChatLength.entries
            DropSegment(
                label = filter.length.label, icon = Icons.Rounded.Notes, selected = filter.length != ChatLength.Any,
                options = lengths.map { it.label }, chosen = lengths.indexOf(filter.length),
                onPick = { i -> onFilter { it.copy(length = lengths[i]) } },
                modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 128.dp),
            )
            val personaLabels = remember(personas) { listOf("Any persona") + personas.map { it.name } }
            DropSegment(
                label = filter.personaLabel ?: "Any persona", icon = Icons.Rounded.Person, selected = filter.personaIds != null,
                options = personaLabels,
                chosen = filter.personaLabel?.let { l -> personas.indexOfFirst { it.name == l } + 1 } ?: 0,
                onPick = { i -> onPersona(if (i == 0) null else personas[i - 1]) },
                modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 130.dp),
            )
        }
        Group {
            Segment("Several chats", Icons.Rounded.Forum, filter.multiOnly, onClick = { onFilter { it.copy(multiOnly = !it.multiOnly) } }, modifier = Modifier.padding(horizontal = 4.dp))
            Segment("Pinned only", Icons.Rounded.PushPin, filter.pinnedOnly, onClick = { onFilter { it.copy(pinnedOnly = !it.pinnedOnly) } }, modifier = Modifier.padding(horizontal = 4.dp))
            Segment("Pinned on top", Icons.Rounded.VerticalAlignTop, filter.pinnedFirst, onClick = { onFilter { it.copy(pinnedFirst = !it.pinnedFirst) } }, modifier = Modifier.padding(horizontal = 4.dp))
            Segment(
                if (searching) "Search in messages" else "Search messages too",
                Icons.Rounded.ManageSearch, filter.inMessages,
                onClick = { onFilter { it.copy(inMessages = !it.inMessages) } },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Group {
            Segment(
                if (archivedCount > 0) "Archived · $archivedCount" else "Archived",
                Icons.Outlined.Archive, inArchive, onClick = onArchive, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Segment(
                view.label,
                if (view == ChatsView.List) Icons.Rounded.ViewList else Icons.Rounded.GridView, false,
                onClick = { onView(if (view == ChatsView.List) ChatsView.Cards else ChatsView.List) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Segment("Select", Icons.Rounded.Checklist, false, onClick = onSelect, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (filter.active) {
            Segment("Clear", Icons.Rounded.FilterAltOff, false, onClick = onClear, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.border(1.dp, ButlerTheme.colors.rule, MaterialTheme.shapes.medium).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** In select mode: how many, and what to do with them all. */
@Composable
fun SelectionBar(
    count: Int,
    allPinned: Boolean,
    inArchive: Boolean,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onFolder: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (count == 0) "Pick chats" else "$count selected",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 6.dp),
        )
        Group {
            Segment(if (allPinned && count > 0) "Unpin" else "Pin", Icons.Rounded.PushPin, false, onClick = onPin, modifier = Modifier.padding(horizontal = 4.dp))
            Segment(if (inArchive) "Unarchive" else "Archive", Icons.Outlined.Archive, false, onClick = onArchive, modifier = Modifier.padding(horizontal = 4.dp))
            Segment("Add to folder", Icons.Rounded.CreateNewFolder, false, onClick = onFolder, modifier = Modifier.padding(horizontal = 4.dp))
            Segment("Delete", Icons.Rounded.Delete, false, onClick = onDelete, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Group { Segment("Done", Icons.Rounded.Close, false, onClick = onDone, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
fun DeleteChatsDialog(count: Int, chats: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $chats ${if (chats == 1) "chat" else "chats"}?") },
        text = {
            Text(
                "Every chat with ${if (count == 1) "this character" else "these $count characters"} is deleted, on Janitor too. This can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = ButlerTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

/** The card view: a portrait per character, newest first (or as sorted). */
@Composable
fun ChatCards(
    groups: LazyPagingItems<ChatGroup>,
    fallbackPersonaName: String?,
    selecting: Boolean,
    selected: Set<String>,
    onOpen: (ChatGroup) -> Unit,
    onToggle: (ChatGroup) -> Unit,
    onMore: (ChatGroup) -> Unit,
) {
    val width = 176.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(width),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(count = groups.itemCount, key = { i -> groups.peek(i)?.characterId ?: i }) { i ->
            val g = groups[i] ?: return@items
            val on = g.characterId in selected
            CharacterTile(
                name = (if (selecting) (if (on) "☑ " else "☐ ") else if (g.pinned) "📌 " else "") + g.characterName.ifBlank { "Character" },
                avatarUrl = g.avatarUrl,
                width = width,
                onClick = { if (selecting) onToggle(g) else onOpen(g) },
                chatCount = "${g.chatCount}",
                modifier = if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            g.lastMessageAt?.let { RelativeTime.short(it) } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = ButlerTheme.colors.textLow,
                            modifier = Modifier.weight(1f),
                        )
                        if (!selecting) Text(
                            "⋯",
                            style = MaterialTheme.typography.titleMedium,
                            color = ButlerTheme.colors.textMed,
                            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { onMore(g) }.padding(horizontal = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        remember(g.lastMessagePreview, g.personaName, fallbackPersonaName) {
                            g.lastMessagePreview?.fillNames(user = g.personaName ?: fallbackPersonaName, char = g.characterName)?.toString().orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ButlerTheme.colors.textMed,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (g.messageTotal > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("${g.messageTotal} messages", style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
                    }
                }
            }
        }
        if (groups.itemCount == 0) item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(1.dp)) }
    }
}
