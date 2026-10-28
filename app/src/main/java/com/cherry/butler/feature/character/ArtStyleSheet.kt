package com.cherry.butler.feature.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.ArtStyleRepository
import com.cherry.butler.core.data.StyleMatch
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.model.CharacterDetail
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.CharacterTile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the art-style sheet shows. */
data class ArtStyleState(
    val matches: List<StyleMatch> = emptyList(),
    val working: Boolean = false,
    val measured: Int = 0,
    val of: Int = 0,
    val rounds: Int = 0,
    val colours: Boolean = true,
    val error: String? = null,
)

/** Characters drawn like this one (fork feature): see [ArtStyleRepository]. */
@HiltViewModel
class ArtStyleViewModel @Inject constructor(private val repo: ArtStyleRepository) : ViewModel() {
    private val _state = MutableStateFlow(ArtStyleState())
    val state: StateFlow<ArtStyleState> = _state.asStateFlow()

    private var seedId: String? = null
    private var seed: FloatArray? = null
    private var creatorId: String? = null
    private var tags: List<String> = emptyList()
    private var similar: List<Character> = emptyList()
    private val pool = LinkedHashMap<String, Character>()
    private var job: Job? = null

    fun start(detail: CharacterDetail, similarList: List<Character>) {
        if (seedId == detail.id) return
        seedId = detail.id
        creatorId = detail.creatorId
        tags = detail.tagNames
        similar = similarList
        pool.clear()
        seed = null
        _state.value = ArtStyleState()
        lookFurther(detail.avatarUrl)
    }

    /** Another round: further pages of each source. */
    fun lookFurther(avatarUrl: String? = null) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            val s = seed ?: repo.features(avatarUrl).also { seed = it }
            if (s == null) {
                _state.update { it.copy(working = false, error = "Couldn't read this character's picture.") }
                return@launch
            }
            val round = _state.value.rounds
            val found = repo.candidates(creatorId, tags, similar, round).filter { it.id != seedId && it.avatarUrl != null }
            found.forEach { pool.putIfAbsent(it.id, it) }
            val ranked = repo.rank(s, pool.values.toList(), _state.value.colours) { done, total ->
                _state.update { it.copy(measured = done, of = total) }
            }
            _state.update {
                it.copy(
                    matches = ranked.take(MAX_SHOWN),
                    working = false,
                    rounds = round + 1,
                    error = if (ranked.isEmpty()) "Nothing to compare with came back. Try again in a moment." else null,
                )
            }
        }
    }

    /** Palette on or off: re-orders what's measured, no new reads. */
    fun setColours(on: Boolean) {
        val s = seed ?: run { _state.update { it.copy(colours = on) }; return }
        val measured = pool.values.mapNotNull { c -> repo.known(c.avatarUrl)?.let { c to it } }
        _state.update { it.copy(colours = on, matches = repo.order(s, measured, on).take(MAX_SHOWN)) }
    }

    private companion object { const val MAX_SHOWN = 60 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtStyleSheet(
    detail: CharacterDetail,
    similar: List<Character>,
    onOpenCharacter: (String) -> Unit,
    onDismiss: () -> Unit,
    favoriteIds: Set<String>? = null,
    onFavorite: ((String) -> Unit)? = null,
    viewModel: ArtStyleViewModel = androidx.hilt.navigation.compose.hiltViewModel(key = "art-style-${detail.id}"),
) {
    LaunchedEffect(detail.id) { viewModel.start(detail, similar) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(url = detail.avatarUrl, name = detail.name, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Same art style", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Pictures drawn like ${detail.name}'s: line work, shading, colours. Closest first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ButlerTheme.colors.textMed,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.cherry.butler.feature.browse.Segment(
                    label = "Style and colours",
                    icon = androidx.compose.material.icons.Icons.Rounded.Palette,
                    selected = state.colours,
                    onClick = { viewModel.setColours(true) },
                )
                com.cherry.butler.feature.browse.Segment(
                    label = "Style only",
                    icon = androidx.compose.material.icons.Icons.Rounded.Brush,
                    selected = !state.colours,
                    onClick = { viewModel.setColours(false) },
                )
                Spacer(Modifier.weight(1f))
                com.cherry.butler.feature.chats.KeyButton(
                    label = if (state.working) "Looking…" else "Look further",
                    onClick = { viewModel.lookFurther() },
                    enabled = !state.working,
                )
            }
            if (state.working) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (state.of == 0) 0f else state.measured.toFloat() / state.of },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (state.of == 0) "Gathering characters to compare…" else "Looked at ${state.measured} of ${state.of} pictures",
                    style = MaterialTheme.typography.labelSmall,
                    color = ButlerTheme.colors.textLow,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(vertical = 16.dp))
            }
            Spacer(Modifier.height(10.dp))
            val width = 168.dp
            LazyVerticalGrid(
                columns = GridCells.Adaptive(width),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.matches, key = { it.character.id }) { m ->
                    val c = m.character
                    Box {
                        CharacterTile(
                            name = c.name,
                            avatarUrl = c.avatarUrl,
                            width = width,
                            onClick = { onDismiss(); onOpenCharacter(c.id) },
                            obscured = c.isImageNsfw,
                            nsfw = c.isNsfw,
                            chatCount = c.chatCount.takeIf { it > 0 }?.compactCount(),
                            favorite = favoriteIds?.let { c.id in it },
                            onFavorite = onFavorite?.let { f -> { f(c.id) } },
                        ) {
                            com.cherry.butler.ui.components.BrowseTileFooter(creatorName = c.creatorName, creatorVerified = c.creatorVerified, blurb = c.blurb, tags = c.tagNames, creatorColor = c.creatorColor)
                        }
                    }
                }
                if (state.matches.isNotEmpty() && !state.working) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Compared with ${state.of} characters so far. Look further for more.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ButlerTheme.colors.textLow,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
