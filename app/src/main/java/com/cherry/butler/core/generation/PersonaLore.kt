package com.cherry.butler.core.generation

import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.ProxyConfig
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.dto.CharacterDetailDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The persona lore builder (fork feature): writes your persona into one character's world —
 * how you know each other, your history, your place in their setting — from the persona's
 * description, the character's definition and whatever you ask for. It goes through one of
 * your proxy presets, as the persona editor's ✦ does; the text is yours to edit after.
 */
@Singleton
class PersonaLore @Inject constructor(
    private val writer: DescriptionWriter,
    private val characters: CharacterRemoteSource,
    private val profiles: ProfileRepository,
) {
    data class Persona(val name: String, val appearance: String)

    suspend fun presets(): List<ProxyConfig> = writer.presets()

    var lastPreset: String?
        get() = writer.lastPreset
        set(value) { writer.lastPreset = value }

    suspend fun character(characterId: String): CharacterDetailDto = characters.detail(characterId)

    /** The persona as Janitor has it now: its name and description. Null id is the profile. */
    suspend fun persona(personaId: String?): Persona =
        if (personaId == null) {
            val p = profiles.profile()
            Persona(p.name, p.appearance.orEmpty())
        } else {
            val p = profiles.personas().firstOrNull { it.id == personaId } ?: profiles.personas(refresh = true).first { it.id == personaId }
            Persona(p.name, p.appearance.orEmpty())
        }

    fun write(persona: Persona, character: CharacterDetailDto, wishes: String, preset: ProxyConfig): Flow<String> =
        writer.write(system = INSTRUCTION, user = brief(persona, character, wishes), preset = preset, maxTokens = 600, temperature = 0.85)

    companion object {
        /** Long definitions are cut: the gist is in the first few thousand characters. */
        private const val PART = 2_500

        private fun cut(s: String?): String = s.orEmpty().trim().let { if (it.length > PART) it.take(PART) + "…" else it }

        fun brief(persona: Persona, c: CharacterDetailDto, wishes: String): String = buildString {
            val charName = c.chatName?.takeIf { it.isNotBlank() } ?: c.name
            append("THE PERSONA (the user plays them)\n")
            append("Name: ").append(persona.name.trim()).append('\n')
            append("Description:\n").append(cut(persona.appearance).ifBlank { "(none written)" }).append("\n\n")
            append("THE CHARACTER\n")
            append("Name: ").append(charName).append('\n')
            cut(c.personality).takeIf { it.isNotEmpty() }?.let { append("Personality:\n").append(it).append('\n') }
            cut(c.scenario).takeIf { it.isNotEmpty() }?.let { append("Scenario:\n").append(it).append('\n') }
            cut(c.firstMessages.firstOrNull { !it.isNullOrBlank() } ?: c.firstMessage).takeIf { it.isNotEmpty() }?.let { append("Opening message:\n").append(it).append('\n') }
            append('\n')
            if (wishes.isNotBlank()) append("WHAT THE USER WANTS (follow it exactly)\n").append(wishes.trim()).append('\n')
            append("\nWrite the lore entry for ").append(persona.name.trim()).append(" in ").append(charName).append("'s story.")
        }

        const val INSTRUCTION =
            "You write background lore that places a roleplay persona inside a character's story. You get the " +
                "persona the user plays, the character's definition, and sometimes what the user wants. Write one " +
                "lore entry about the persona: how they and the character know each other (or why they are about to " +
                "meet), the history they share, the persona's role and standing in the character's world, and one " +
                "or two threads the story can pick up. Fit the setting, its tone and its rules; never contradict the " +
                "persona's facts or the character's definition, and follow the user's wishes exactly. Third person, " +
                "about the persona by name, plain prose, no headings, lists or markdown, about 120 to 220 words. " +
                "This is background, not a scene: no dialogue, and nothing the character does or says now. Reply " +
                "with the entry only."
    }
}
