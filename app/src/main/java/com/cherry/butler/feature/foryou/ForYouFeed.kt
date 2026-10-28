package com.cherry.butler.feature.foryou

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.FavoritesRepository
import com.cherry.butler.core.data.ForYouPick
import com.cherry.butler.core.data.ForYouRepository
import com.cherry.butler.core.data.PersonaRepository
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The For you feed's state (fork feature). */
data class ForYouState(
    val loading: Boolean = true,
    val picks: List<ForYouPick> = emptyList(),
    val index: Int = 0,
    val error: String? = null,
    val personaName: String? = null,
) {
    val current: ForYouPick? get() = picks.getOrNull(index)
    val atEnd: Boolean get() = !loading && error == null && index >= picks.size
}

@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val repository: ForYouRepository,
    private val favorites: FavoritesRepository,
    private val personas: PersonaRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ForYouState())
    val state: StateFlow<ForYouState> = _state.asStateFlow()
    val favoriteIds: StateFlow<Set<String>> = favorites.ids

    init {
        load()
        // Playing as someone else changes what fits: the feed is built again.
        viewModelScope.launch { personas.current.map { it?.id }.distinctUntilChanged().drop(1).collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.build() }
                .onSuccess { picks -> _state.value = ForYouState(loading = false, picks = picks, personaName = personas.current.value?.name) }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun next() {
        val s = _state.value
        s.current?.let { repository.markSeen(it.character.id) }
        if (s.index < s.picks.size) _state.update { it.copy(index = it.index + 1) }
    }

    fun previous() = _state.update { if (it.index > 0) it.copy(index = it.index - 1) else it }

    /** Not shown again, and its tags count a little against what comes next. */
    fun notForMe() {
        val pick = _state.value.current ?: return
        repository.notForMe(pick)
        _state.update { s -> s.copy(picks = s.picks.filterNot { it.character.id == pick.character.id }) }
    }

    fun toggleFavorite() {
        val id = _state.value.current?.character?.id ?: return
        viewModelScope.launch { favorites.set(id, !favorites.isFavorite(id)) }
    }

    fun startOver() {
        repository.reset()
        load()
    }
}

/**
 * For you (fork feature): one character at a time, picked from what you play and like, the way
 * the website's For you shows them. Up and down (or the arrow keys) go through them; the heart
 * favourites without leaving; "Not for me" teaches it what to leave out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForYouFeed(
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForYouViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown, Key.PageDown, Key.J -> { viewModel.next(); true }
                    Key.DirectionUp, Key.PageUp, Key.K -> { viewModel.previous(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            state.loading -> Notice("Finding characters for you…", "It looks at the bots you chat with and the ones you've favourited.")
            state.error != null -> Notice("For you couldn't load", state.error!!, action = "Try again", onAction = viewModel::load)
            state.picks.isEmpty() -> Notice(
                "Nothing to suggest yet",
                "Chat with a few characters or favourite some, and For you learns from them.",
                action = "Look again", onAction = viewModel::load,
            )
            state.atEnd -> Notice(
                "That's all for now",
                "You've been through every suggestion. Look again for a fresh set, or start over to see passed ones again.",
                action = "Look again", onAction = viewModel::load,
                secondary = "Start over", onSecondary = viewModel::startOver,
            )
            else -> {
                val pick = state.current!!
                val fav = pick.character.id in favorites
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth
                if (wide < 600.dp) {
                    // Phone: the card fills the width; the buttons sit along the bottom.
                    Box(Modifier.fillMaxSize()) {
                        PickCard(
                            pick = pick,
                            position = "${state.index + 1} of ${state.picks.size}",
                            personaName = state.personaName,
                            onOpen = { onOpenCharacter(pick.character.id) },
                            portraitHeight = (wide * 1.25f).coerceAtMost(560.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            bottomRoom = 96.dp,
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(ButlerTheme.colors.chrome)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RailButton(Icons.Rounded.KeyboardArrowUp, "Previous", enabled = state.index > 0, onClick = viewModel::previous)
                            RailButton(Icons.Rounded.Block, "Not for me", tint = ButlerTheme.colors.textMed, onClick = viewModel::notForMe)
                            RailButton(
                                if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                if (fav) "Unfavourite" else "Favourite",
                                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                onClick = viewModel::toggleFavorite,
                            )
                            RailButton(Icons.Rounded.OpenInNew, "Open", onClick = { onOpenCharacter(pick.character.id) })
                            RailButton(Icons.Rounded.KeyboardArrowDown, "Next", onClick = viewModel::next)
                        }
                    }
                } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PickCard(
                        pick = pick,
                        position = "${state.index + 1} of ${state.picks.size}",
                        personaName = state.personaName,
                        onOpen = { onOpenCharacter(pick.character.id) },
                        modifier = Modifier.weight(1f, fill = false).widthIn(max = 560.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.padding(top = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RailButton(Icons.Rounded.KeyboardArrowUp, "Previous", enabled = state.index > 0, onClick = viewModel::previous)
                        RailButton(Icons.Rounded.KeyboardArrowDown, "Next", onClick = viewModel::next)
                        Spacer(Modifier.height(12.dp))
                        RailButton(
                            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            if (fav) "Unfavourite" else "Favourite",
                            tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            onClick = viewModel::toggleFavorite,
                        )
                        RailButton(Icons.Rounded.OpenInNew, "Open", onClick = { onOpenCharacter(pick.character.id) })
                        RailButton(Icons.Rounded.Block, "Not for me", tint = ButlerTheme.colors.textMed, onClick = viewModel::notForMe)
                    }
                }
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickCard(
    pick: ForYouPick,
    position: String,
    personaName: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    portraitHeight: androidx.compose.ui.unit.Dp = 560.dp,
    bottomRoom: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val c = pick.character
    val panel = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(ButlerTheme.colors.surfaceHigh)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomRoom),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "For you · $position" + (personaName?.let { " · as $it" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = ButlerTheme.colors.textLow,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
        Column(panel.clickable(onClick = onOpen).padding(16.dp)) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("@${c.creatorName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Portrait(url = c.avatarUrl, obscured = c.isImageNsfw, chats = c.chatCount, onClick = onOpen, height = portraitHeight)
        Column(panel.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val facts = buildList {
                if (c.totalTokens > 0) add("%,d tokens".format(c.totalTokens))
                add(if (c.isProxyEnabled) "proxy allowed" else "no proxy")
                if (c.isNsfw) add("18+")
            }
            Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
            val tags = c.tagNames + c.customTags.map { "#$it" }
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.take(14).forEach { t ->
                        Text(
                            t,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ButlerTheme.colors.chrome).padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
        if (c.blurb.isNotBlank()) {
            Text(c.blurb, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = panel.padding(16.dp))
        }
        if (pick.reasons.isNotEmpty()) {
            Column(panel.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Why you're seeing this", style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed)
                pick.reasons.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed) }
            }
        }
    }
}

@Composable
private fun Portrait(url: String?, obscured: Boolean, chats: Long, onClick: () -> Unit, height: androidx.compose.ui.unit.Dp = 560.dp) {
    val context = LocalContext.current
    val widthPx = with(LocalDensity.current) { 560.dp.roundToPx() }
    val request = remember(url, obscured, widthPx) {
        ImageRequest.Builder(context)
            .data(JanitorConfig.sizedMediaUrl(url, if (obscured) 12 else widthPx))
            .memoryCacheKey("$url@foryou${if (obscured) "-obscured" else ""}")
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(ButlerTheme.colors.surfaceHigh)
            .clickable(onClick = onClick),
    ) {
        // The whole picture, never cropped: the frame around it is a soft blur of it.
        if (url != null) com.cherry.butler.ui.components.FittedPicture(request, com.cherry.butler.core.design.PictureFit.Whole, Modifier.fillMaxSize())
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ButlerTheme.colors.chrome.copy(alpha = 0.8f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(chats.compactCount(), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ButlerTheme.colors.surfaceHigh)
                .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) tint else ButlerTheme.colors.textLow, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun Notice(title: String, body: String, action: String? = null, onAction: () -> Unit = {}, secondary: String? = null, onSecondary: () -> Unit = {}) {
    Column(
        modifier = Modifier.widthIn(max = 460.dp).padding(top = 80.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (action != null) TextButton(onClick = onAction) { Text(action) }
            if (secondary != null) TextButton(onClick = onSecondary) { Text(secondary) }
        }
    }
}

/** "Browse | For you" over the browse page, as the website's "Home | For you" (fork feature). */
@Composable
fun BrowseForYouSwitch(forYou: Boolean, onPick: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(false to "Browse", true to "For you").forEach { (value, label) ->
            Text(
                label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = if (forYou == value) FontWeight.Bold else FontWeight.Normal,
                color = if (forYou == value) MaterialTheme.colorScheme.onBackground else ButlerTheme.colors.textLow,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = label) { onPick(value) },
            )
        }
    }
}
