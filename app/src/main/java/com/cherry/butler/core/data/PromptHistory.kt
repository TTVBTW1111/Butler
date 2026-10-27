package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One saved version of a library prompt (fork feature). */
@Serializable
data class PromptSnapshot(val at: Long, val name: String, val content: String)

/**
 * Every version of your library prompts that was saved from this device, newest first, kept
 * in one small file in the app's own storage. Janitor keeps only the current text; this is how
 * an older edition of a prompt comes back. Nothing here leaves the device.
 */
@Singleton
class PromptHistory @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "prompt-history.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(PromptSnapshot.serializer()))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()

    private val _all = MutableStateFlow(read())
    val all: StateFlow<Map<String, List<PromptSnapshot>>> = _all.asStateFlow()

    fun of(promptId: String): List<PromptSnapshot> = _all.value[promptId].orEmpty()

    /** Keeps this version, unless it is the same as the newest one already kept. */
    fun record(promptId: String, name: String, content: String, at: Long = System.currentTimeMillis()) {
        _all.update { map ->
            val list = map[promptId].orEmpty()
            if (list.firstOrNull()?.let { it.content == content && it.name == name } == true) return@update map
            map + (promptId to (listOf(PromptSnapshot(at, name, content)) + list).take(MAX_VERSIONS))
        }
        persist()
    }

    fun forget(promptId: String) {
        _all.update { it - promptId }
        persist()
    }

    private fun read(): Map<String, List<PromptSnapshot>> =
        runCatching { if (file.isFile) json.decodeFromString(serializer, file.readText()) else emptyMap() }
            .getOrDefault(emptyMap())

    private fun persist() {
        val snapshot = _all.value
        scope.launch {
            writes.withLock {
                val tmp = File(file.parentFile, file.name + ".tmp")
                runCatching {
                    tmp.writeText(json.encodeToString(serializer, snapshot))
                    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                }
            }
        }
    }

    companion object {
        /** Per prompt. A 50K-character prompt at 40 versions is still only a few MB. */
        const val MAX_VERSIONS = 40

        /** What changed between two versions, in characters: added, removed (a quick size of the edit). */
        fun delta(old: String, new: String): Pair<Int, Int> {
            var start = 0
            val max = minOf(old.length, new.length)
            while (start < max && old[start] == new[start]) start++
            var endOld = old.length
            var endNew = new.length
            while (endOld > start && endNew > start && old[endOld - 1] == new[endNew - 1]) { endOld--; endNew-- }
            return (endNew - start) to (endOld - start)
        }
    }
}
