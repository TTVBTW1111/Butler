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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One version of who a persona is in chats (fork feature): fight night, casual, injured… */
@Serializable
data class Outfit(val id: String, val name: String, val appearance: String, val updatedAt: Long = 0)

/** A persona's outfits, and the one it's wearing now (whose text is on Janitor). */
@Serializable
data class Wardrobe(val outfits: List<Outfit> = emptyList(), val wornId: String? = null) {
    val worn: Outfit? get() = outfits.firstOrNull { it.id == wornId }
}

/**
 * Outfits for your personas (fork feature). Janitor has one description per persona; outfits
 * are several, kept on this device, and wearing one writes its text to the persona on Janitor,
 * so the next reply already sees it. Name, pronouns and picture are never touched.
 *
 * Keyed by persona id, or [DEFAULT] for the profile (the default persona).
 */
@Singleton
class PersonaOutfits @Inject constructor(
    @ApplicationContext context: Context,
    private val editing: PersonaEditing,
    private val personas: PersonaRepository,
    private val profiles: ProfileRepository,
) {
    private val file = File(context.filesDir, "persona-outfits.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), Wardrobe.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()

    private val _all = MutableStateFlow(read())
    val all: StateFlow<Map<String, Wardrobe>> = _all.asStateFlow()

    fun of(personaId: String?): Wardrobe = _all.value[key(personaId)] ?: Wardrobe()

    /** Keeps [appearance] as a new outfit named [name], and marks it worn (it's what's in the field). */
    fun add(personaId: String?, name: String, appearance: String): Outfit {
        val outfit = Outfit(id = System.currentTimeMillis().toString(36), name = name.trim().ifBlank { "Outfit" }, appearance = appearance, updatedAt = System.currentTimeMillis())
        change(personaId) { w -> w.copy(outfits = w.outfits + outfit, wornId = outfit.id) }
        return outfit
    }

    fun rename(personaId: String?, outfitId: String, name: String) =
        change(personaId) { w -> w.copy(outfits = w.outfits.map { if (it.id == outfitId) it.copy(name = name.trim().ifBlank { it.name }) else it }) }

    fun remove(personaId: String?, outfitId: String) =
        change(personaId) { w -> w.copy(outfits = w.outfits.filterNot { it.id == outfitId }, wornId = w.wornId.takeIf { it != outfitId }) }

    /** The editor saved the persona: the worn outfit takes the saved text, so it stays as edited. */
    fun saved(personaId: String?, appearance: String) =
        change(personaId) { w ->
            w.copy(outfits = w.outfits.map { if (it.id == w.wornId && it.appearance != appearance) it.copy(appearance = appearance, updatedAt = System.currentTimeMillis()) else it })
        }

    /** Marks [outfitId] worn without writing anything (the editor puts its text in the field first). */
    fun markWorn(personaId: String?, outfitId: String?) = change(personaId) { it.copy(wornId = outfitId) }

    /**
     * Puts the outfit on, on Janitor, right away: only the description changes. Used from a chat,
     * where there's no editor to save from.
     */
    suspend fun wear(personaId: String?, outfit: Outfit) {
        if (personaId == null) {
            val p = profiles.profile(refresh = true)
            editing.updateProfile(PersonaDraft(name = p.name, appearance = outfit.appearance, pronouns = null, picture = null))
        } else {
            val name = personas.options.value.firstOrNull { it.id == personaId }?.name
                ?: profiles.personas(refresh = true).firstOrNull { it.id == personaId }?.name
                ?: error("That persona isn't on your account any more.")
            editing.update(personaId, PersonaDraft(name = name, appearance = outfit.appearance, pronouns = null, picture = null))
        }
        markWorn(personaId, outfit.id)
    }

    private fun key(personaId: String?) = personaId ?: DEFAULT

    private fun change(personaId: String?, f: (Wardrobe) -> Wardrobe) {
        _all.update { map -> map + (key(personaId) to f(map[key(personaId)] ?: Wardrobe())) }
        persist()
    }

    private fun read(): Map<String, Wardrobe> =
        runCatching { if (file.isFile) json.decodeFromString(serializer, file.readText()) else emptyMap() }.getOrDefault(emptyMap())

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
        const val DEFAULT = "default"
    }
}
