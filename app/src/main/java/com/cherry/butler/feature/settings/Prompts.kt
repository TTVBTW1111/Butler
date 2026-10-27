package com.cherry.butler.feature.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.Prompt
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.feature.chat.DeleteConfirmDialog
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.dropLastPixel
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SkeletonRosterRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---- library ------------------------------------------------------------------------

@HiltViewModel
class PromptsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    /** null until the settings have been read once: the screen shows a skeleton, not "none". */
    val prompts: StateFlow<List<Prompt>?> = repository.settings
        .map { it?.prompts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.settings.value?.prompts)

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    /** Fork: every proxy (a model) with the prompt it carries, for the editions overview. */
    val proxies: StateFlow<List<com.cherry.butler.core.data.ProxyConfig>> = repository.settings
        .map { it?.proxies.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.settings.value?.proxies.orEmpty())

    private val _linkError = MutableStateFlow<String?>(null)
    val linkError: StateFlow<String?> = _linkError.asStateFlow()

    fun link(proxyId: String, promptId: String?) {
        viewModelScope.launch {
            _linkError.value = null
            runCatching { repository.linkPrompt(proxyId, promptId) }
                .onFailure { _linkError.value = "Not changed. ${it.userMessage()}" }
        }
    }

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _error.value = null
            runCatching { repository.load() }.onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
        }
    }
}

@Composable
fun PromptsScreen(onBack: () -> Unit, onOpen: (String?) -> Unit, viewModel: PromptsViewModel = hiltViewModel()) {
    // Ctrl+R / F5 on the desktop.
    com.cherry.butler.ui.components.RegisterRefresh { viewModel.reload() }
    val loaded by viewModel.prompts.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val prompts = loaded.orEmpty()
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val linkError by viewModel.linkError.collectAsStateWithLifecycle()
    var linking by remember { mutableStateOf<com.cherry.butler.core.data.ProxyConfig?>(null) }
    linking?.let { proxy ->
        PromptPickerSheet(
            prompts = prompts.filter { it.kind != "prefill" },
            selectedId = proxy.promptId,
            onPick = { id -> viewModel.link(proxy.id, id); linking = null },
            onDismiss = { linking = null },
        )
    }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            PlateText(text = "Prompt library", level = PlateLevel.Name, modifier = Modifier.weight(1f))
            KeyButton(label = "New", onClick = { onOpen(null) }, primary = true, modifier = Modifier.heightIn(min = 40.dp))
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp)) {
            if (loaded == null && error != null) {
                InlineErrorCard(error!!, onRetry = viewModel::reload, modifier = Modifier.padding(16.dp))
            } else if (loaded == null) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) { repeat(4) { SkeletonRosterRow(withMargin = false) } }
            } else if (prompts.isEmpty()) {
                Text(
                    "No prompts yet. A prompt is the instruction a proxy sends ahead of the chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(16.dp),
                )
            }
            // Fork: which edition each model runs with, changed in one place.
            if (proxies.isNotEmpty() && loaded != null) {
                Text(
                    "Which prompt each model uses",
                    style = MaterialTheme.typography.labelMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .card(shape = MaterialTheme.shapes.large)
                        .dropLastPixel(),
                ) {
                    proxies.forEach { proxy ->
                        val linked = prompts.firstOrNull { it.id == proxy.promptId }
                        LinkRow(
                            title = proxy.name.ifBlank { proxy.model.ifBlank { "Proxy" } },
                            subtitle = (if (proxy.model.isNotBlank() && proxy.model != proxy.name) proxy.model + "  ·  " else "") +
                                (linked?.name?.ifBlank { "Untitled" } ?: proxy.promptName ?: "No prompt"),
                            onClick = { linking = proxy },
                        )
                    }
                }
                linkError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.danger, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                Text(
                    "Library",
                    style = MaterialTheme.typography.labelMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                )
            }
            if (prompts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .card(shape = MaterialTheme.shapes.large)
                        .dropLastPixel(),
                ) {
                    prompts.forEach { p ->
                        LinkRow(
                            title = p.name.ifBlank { "Untitled" },
                            subtitle = (if (p.kind == "prefill") "Prefill · " else "") + "%,d chars · ".format(p.content.length) + p.content.lines().joinToString(" ").take(80),
                            onClick = { onOpen(p.id) },
                        )
                    }
                }
            }
        }
    }
}

// ---- editor -------------------------------------------------------------------------

data class PromptForm(
    val name: String = "",
    val kind: String = "system",
    val content: String = "",
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: ApiError? = null,
)

@HiltViewModel
class PromptEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SettingsRepository,
    private val history: com.cherry.butler.core.data.PromptHistory,
) : ViewModel() {
    val promptId: String? = savedStateHandle.get<String>(ARG)?.takeIf { it != NEW }
    val isNew: Boolean get() = promptId == null

    private val _form = MutableStateFlow(PromptForm())
    val form: StateFlow<PromptForm> = _form.asStateFlow()

    /** Fork: the versions saved from this device, newest first. */
    val versions: StateFlow<List<com.cherry.butler.core.data.PromptSnapshot>> = history.all
        .map { all -> promptId?.let { all[it] }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, promptId?.let { history.of(it) }.orEmpty())

    init {
        viewModelScope.launch {
            val p = runCatching { repository.load() }.getOrNull()?.prompts?.firstOrNull { it.id == promptId }
            _form.value = if (p != null) PromptForm(p.name, p.kind, p.content, loaded = true) else PromptForm(loaded = true)
            // The text as Janitor has it is the first version, so the first edit can be undone.
            if (p != null && history.of(p.id).isEmpty()) history.record(p.id, p.name, p.content)
        }
    }

    /** Puts an older version in the editor; it's only kept once you press Save. */
    fun restore(v: com.cherry.butler.core.data.PromptSnapshot) = _form.update { it.copy(name = v.name, content = v.content, error = null) }

    /** A file's text into the editor (an edition kept as .txt or .md); the name from the file when there's none yet. */
    fun importText(text: String, fileName: String?) = _form.update { f ->
        f.copy(
            content = text.replace("\r\n", "\n"),
            name = f.name.ifBlank { fileName?.substringBeforeLast('.')?.trim().orEmpty() },
            error = null,
        )
    }

    fun update(change: (PromptForm) -> PromptForm) = _form.update { change(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.saving || f.name.isBlank()) return
        viewModelScope.launch {
            _form.update { it.copy(saving = true) }
            runCatching { repository.savePrompt(promptId, f.name, f.kind, f.content) }
                .onSuccess { saved -> history.record(saved.id, saved.name, saved.content); onDone() }
                .onFailure { e -> _form.update { it.copy(saving = false, error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = promptId ?: return
        viewModelScope.launch {
            _form.update { it.copy(saving = true) }
            runCatching { repository.deletePrompt(id) }
                .onSuccess { onDone() }
                .onFailure { e -> _form.update { it.copy(saving = false, error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    companion object {
        const val ARG = "promptId"
        const val NEW = "new"
    }
}

@Composable
fun PromptEditorScreen(onBack: () -> Unit, viewModel: PromptEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    // Fork: files in and out, and older versions.
    val versions by viewModel.versions.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    var fileNote by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val saveFile = com.cherry.butler.ui.components.rememberFileSaver { fileNote = it }
    val openFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text != null) viewModel.importText(text, android.net.Uri.decode(uri.lastPathSegment)?.substringAfterLast('/')) else fileNote = "Couldn't read that file."
        }
    }
    if (showHistory) {
        PromptHistorySheet(
            versions = versions,
            current = form.content,
            onRestore = { viewModel.restore(it); showHistory = false; fileNote = "Older version in the editor. Save to keep it." },
            onDismiss = { showHistory = false },
        )
    }
    if (confirmDelete) {
        DeleteConfirmDialog(
            count = 1,
            title = "Delete prompt",
            body = "\"${form.name}\" will be removed from your library. Proxies using it will run without a prompt.",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    EditorScaffold(
        title = if (viewModel.isNew) "New prompt" else "Edit prompt",
        onBack = onBack,
        saving = form.saving,
        canSave = form.loaded && form.name.isNotBlank(),
        onSave = { viewModel.save(onBack) },
        error = form.error?.let { "Not saved. ${it.userMessage()}" } ?: fileNote,
        menu = { close ->
            EditorMenuItem("Import from file", androidx.compose.material.icons.Icons.Outlined.FileOpen, enabled = !form.saving) {
                close(); fileNote = null
                openFile.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream", "*/*"))
            }
            EditorMenuItem("Save to file", androidx.compose.material.icons.Icons.Outlined.SaveAlt, enabled = form.content.isNotEmpty()) {
                close(); fileNote = null
                saveFile("${form.name.ifBlank { "prompt" }.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()}.txt", form.content)
            }
            EditorMenuItem("History (${versions.size})", androidx.compose.material.icons.Icons.Outlined.History, enabled = versions.isNotEmpty()) {
                close(); showHistory = true
            }
            if (!viewModel.isNew) {
                EditorMenuItem("Delete", androidx.compose.material.icons.Icons.Outlined.Delete, danger = true, enabled = !form.saving) { close(); confirmDelete = true }
            }
        },
    ) {
        FieldBlock("Name", form.name, { v -> viewModel.update { it.copy(name = v) } })
        if (viewModel.isNew) {
            Text(
                "Kind",
                style = MaterialTheme.typography.labelMedium,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            )
            com.cherry.butler.ui.components.SegmentedChoice(
                options = listOf("System", "Prefill"),
                selected = if (form.kind == "prefill") 1 else 0,
                onSelect = { i -> viewModel.update { it.copy(kind = if (i == 1) "prefill" else "system") } },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        FieldBlock(
            label = "Content",
            value = form.content,
            onChange = { v -> viewModel.update { it.copy(content = v) } },
            singleLine = false,
            minLines = 10,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        PromptSize(form.content)
    }
}

/**
 * Fork: how big the prompt is as you type. Janitor's library takes up to 500,000 characters;
 * past [PROMPT_WARN] the website's own editor has been seen to refuse a save, so it's flagged.
 */
@Composable
private fun PromptSize(text: String) {
    val chars = text.length
    val over = chars > PROMPT_WARN
    Text(
        text = "%,d characters  ·  about %,d tokens".format(chars, (chars + 3) / 4) +
            if (over) "  ·  over %,d: may not save on the website".format(PROMPT_WARN) else "",
        style = MaterialTheme.typography.labelMedium,
        color = if (over) ButlerTheme.colors.warn else ButlerTheme.colors.textLow,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private const val PROMPT_WARN = 52_000

/** Fork: the versions kept on this device, newest first; restoring fills the editor. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PromptHistorySheet(
    versions: List<com.cherry.butler.core.data.PromptSnapshot>,
    current: String,
    onRestore: (com.cherry.butler.core.data.PromptSnapshot) -> Unit,
    onDismiss: () -> Unit,
) {
    val format = remember { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            PlateText("History", PlateLevel.Name, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            versions.forEachIndexed { i, v ->
                val older = versions.getOrNull(i + 1)
                val change = older?.let { com.cherry.butler.core.data.PromptHistory.delta(it.content, v.content) }
                val same = v.content == current
                ChoiceRow(
                    title = format.format(java.util.Date(v.at)) + if (i == versions.lastIndex) "  ·  first kept" else "",
                    subtitle = "%,d chars".format(v.content.length) +
                        (change?.let { (added, removed) -> "  ·  +%,d / −%,d".format(added, removed) } ?: "") +
                        (if (v.name.isNotBlank()) "  ·  ${v.name}" else ""),
                    selected = same,
                    onClick = { onRestore(v) },
                    trailing = { TextButton(onClick = { onRestore(v) }, enabled = !same) { Text(if (same) "Current" else "Restore") } },
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        }
    }
}
