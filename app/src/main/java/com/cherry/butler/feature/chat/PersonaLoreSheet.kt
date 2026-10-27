package com.cherry.butler.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.PersonaOutfits
import com.cherry.butler.core.data.ProxyConfig
import com.cherry.butler.core.data.remote.dto.CharacterDetailDto
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.generation.PersonaLore
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Who writes the lore (fork): all of them go the way this chat's replies go, with the whole chat. */
data class LoreWriter(val label: String, val writer: com.cherry.butler.core.data.Writer)

data class LoreState(
    val presets: List<LoreWriter>? = null,
    val preset: LoreWriter? = null,
    val persona: PersonaLore.Persona? = null,
    val characterName: String = "",
    val wishes: String = "",
    val text: String = "",
    val writing: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val done: String? = null,
)

@HiltViewModel
class PersonaLoreViewModel @Inject constructor(
    private val lore: PersonaLore,
    private val outfits: PersonaOutfits,
) : ViewModel() {
    private val _state = MutableStateFlow(LoreState())
    val state: StateFlow<LoreState> = _state.asStateFlow()
    private var character: CharacterDetailDto? = null
    private var personaId: String? = null
    private var chatId: Long = 0
    private var job: Job? = null

    fun prepare(chatId: Long, characterId: String, personaId: String?) {
        this.personaId = personaId
        this.chatId = chatId
        viewModelScope.launch {
            runCatching {
                val proxies = runCatching { lore.presets() }.getOrDefault(emptyList())
                val writers = listOf(
                    LoreWriter("Same as this chat", com.cherry.butler.core.data.Writer.SameAsChat),
                    LoreWriter("JanitorAI (JLLM)", com.cherry.butler.core.data.Writer.Jllm),
                ) + proxies.map { p -> LoreWriter(p.name.ifBlank { p.model.ifBlank { "Proxy" } }, com.cherry.butler.core.data.Writer.Proxy(p.clientId ?: p.id)) }
                val c = lore.character(characterId).also { character = it }
                val p = lore.persona(personaId)
                _state.update {
                    it.copy(
                        presets = writers,
                        preset = writers.first(),
                        persona = p,
                        characterName = c.chatName?.takeIf { n -> n.isNotBlank() } ?: c.name,
                    )
                }
            }.onFailure { e -> _state.update { it.copy(error = e.userMessage(), presets = it.presets ?: emptyList()) } }
        }
    }

    fun update(change: (LoreState) -> LoreState) = _state.update { change(it).copy(error = null, done = null) }

    fun write() {
        val s = _state.value
        if (character == null) return
        val persona = s.persona ?: return
        val preset = s.preset ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(text = "", writing = true, error = null, done = null) }
            val out = StringBuilder()
            runCatching {
                lore.writeFromChat(chatId, persona.name, s.characterName, s.wishes, preset.writer)
                    .collect { piece -> out.append(piece); _state.update { it.copy(text = out.toString().trimStart()) } }
            }
                .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) _state.update { it.copy(error = e.userMessage()) } }
            _state.update { it.copy(writing = false) }
        }
    }

    fun stop() { job?.cancel(); _state.update { it.copy(writing = false) } }

    /** Keeps it as an outfit: the description, then this history; worn at once. */
    fun saveAsOutfit() {
        val s = _state.value
        val persona = s.persona ?: return
        if (s.text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val appearance = persona.appearance.trimEnd() + "\n\n" + "History with ${s.characterName}:\n" + s.text.trim()
                val o = outfits.add(personaId, "With ${s.characterName}", appearance)
                outfits.wear(personaId, o)
            }.onSuccess { _state.update { it.copy(busy = false, done = "Saved as the outfit “With ${s.characterName}” and worn. Change outfits from the chat menu.") } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = "Not saved. ${e.userMessage()}") } }
        }
    }
}

/**
 * Persona lore (fork feature): your persona written into this character's world, through one
 * of your proxies. Edit it freely, write it again, then keep it in this chat's memory (only
 * this chat) or as an outfit (worn now, kept for this character).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PersonaLoreSheet(
    chatId: Long,
    characterId: String,
    personaId: String?,
    currentMemory: String?,
    onAddToMemory: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PersonaLoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(chatId, characterId, personaId) { viewModel.prepare(chatId, characterId, personaId) }
    var presetMenu by remember { mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { viewModel.stop(); onDismiss() },
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlateText("Persona lore", PlateLevel.Name)
            Text(
                if (state.persona == null) "Reading the persona and the character…"
                else "${state.persona!!.name} in ${state.characterName}'s story: how you know each other, your history, your place in their world.",
                style = MaterialTheme.typography.bodyMedium,
                color = ButlerTheme.colors.textMed,
            )
            OutlinedTextField(
                value = state.wishes,
                onValueChange = { v -> viewModel.update { it.copy(wishes = v) } },
                label = { Text("What you want (optional)") },
                placeholder = { Text("Leave empty to work it out from the story so far. Or: we're old sparring partners…") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.writing,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Written by", style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed)
                androidx.compose.foundation.layout.Box {
                    TextButton(onClick = { presetMenu = true }, enabled = !state.presets.isNullOrEmpty() && !state.writing) {
                        Text(state.preset?.label ?: "—")
                    }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        state.presets.orEmpty().forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label) },
                                onClick = { presetMenu = false; viewModel.update { it.copy(preset = p) } },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.writing) {
                    KeyButton(label = "Stop", onClick = viewModel::stop)
                } else {
                    KeyButton(
                        label = when {
                            state.text.isNotBlank() -> "Write again"
                            state.wishes.isBlank() -> "Write it from the story"
                            else -> "Write it"
                        },
                        onClick = viewModel::write,
                        enabled = state.persona != null && state.preset != null,
                        primary = true,
                    )
                }
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = { v -> viewModel.update { it.copy(text = v) } },
                label = { Text("Lore") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                enabled = !state.writing,
            )
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.danger) }
            state.done?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyButton(
                    label = "Add to this chat's memory",
                    enabled = state.text.isNotBlank() && !state.writing && !state.busy,
                    onClick = {
                        val block = "${state.persona?.name ?: "Persona"} and ${state.characterName}:\n" + state.text.trim()
                        onAddToMemory(listOfNotNull(currentMemory?.takeIf { it.isNotBlank() }, block).joinToString("\n\n"))
                        viewModel.update { it.copy(done = "Added to this chat's memory. See it under Chat memory.") }
                    },
                )
                KeyButton(
                    label = "Save as outfit",
                    enabled = state.text.isNotBlank() && !state.writing && !state.busy,
                    onClick = viewModel::saveAsOutfit,
                )
            }
            Text(
                "Memory: only this chat, sent with every reply. Outfit: your description plus this history, worn now, kept for next time.",
                style = MaterialTheme.typography.labelMedium,
                color = ButlerTheme.colors.textLow,
            )
        }
    }
}
