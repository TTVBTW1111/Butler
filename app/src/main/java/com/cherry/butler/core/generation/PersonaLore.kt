package com.cherry.butler.core.generation

import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.ProxyConfig
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.dto.CharacterDetailDto
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.Writer
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.stream.ThinkSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
    db: ButlerDatabase,
    private val transport: GenerationTransport,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()

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

    /**
     * Fork: the same lore, written the way the chat's memory is (docs/JANITOR_API.md §19.2):
     * through Janitor's own generation route with the whole chat, its character and persona,
     * by whatever writes this chat's replies (or [writer]), with the lore instruction after.
     * With no wishes it works from the story so far, as a summary does.
     */
    fun writeFromChat(chatId: Long, personaName: String, characterName: String, wishes: String, writer: Writer = Writer.SameAsChat): Flow<String> = flow {
        val chat = chatDao.get(chatId) ?: throw ApiError.Api(code = 404, janitorCode = "BUTLER_NO_CHAT", serverMessage = "That chat isn't on this device yet.")
        val history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }
        if (history.isEmpty()) throw ApiError.Api(code = 400, janitorCode = "BUTLER_EMPTY_CHAT", serverMessage = "The chat has no messages yet; pick a proxy to write from the definitions alone.")
        val profile = profiles.profile()
        val played = profiles.playedPersona(chat, history)
        val (userConfig, target) = profiles.inputsFor(profile, writer)
        val jllm = (userConfig["api"] as? JsonPrimitive)?.contentOrNull.let { it == null || it == "janitor" }
        val instruction = chatInstruction(personaName, characterName, wishes)
        val envelope = GenerationEnvelope.build(
            chatId = chat.id,
            characterId = chat.characterId,
            userId = chat.userId.ifEmpty { profile.id },
            summary = chat.summary,
            summaryChatId = chat.summaryChatId,
            history = history,
            profile = EnvelopeProfile(
                id = profile.id, name = profile.name, userName = profile.userName,
                userAppearance = played.appearance, defaultAppearance = chat.defaultPersonaAppearance ?: profile.appearance.orEmpty(),
            ),
            userConfig = userConfig,
            mode = GenerateMode.SummaryFull,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            extraUserLine = if (jllm) instruction else null,
            persona = played.persona,
            knownPersonas = profiles.personas(),
        )
        val proxy = if (jllm) null else target?.then { payload -> ask(payload, instruction) }
        val text = StringBuilder()
        val thought = StringBuilder()
        val splitter = ThinkSplitter(text, thought)
        var sent = 0
        transport.generate(envelope, proxy).collect { event ->
            if (event is GenerationEvent.Delta) {
                splitter.feed(event.text)
                if (text.length > sent) { emit(text.substring(sent)); sent = text.length }
            }
        }
        splitter.finish()
        if (text.length > sent) emit(text.substring(sent))
    }.flowOn(Dispatchers.IO)

    /** The assembled payload, the lore instruction after the chat, and room for the entry. */
    private fun ask(payload: JsonObject, instruction: String): JsonObject {
        val messages = payload["messages"]?.jsonArray.orEmpty() + buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", JsonPrimitive(instruction))
        }
        val cap = runCatching { payload["max_tokens"]?.jsonPrimitive?.intOrNull }.getOrNull()
        val room = if (cap != null && cap < 700) mapOf("max_tokens" to JsonPrimitive(700)) else emptyMap()
        return JsonObject(payload + mapOf("messages" to JsonArray(messages)) + room)
    }

    companion object {
        /** The ask after the chat (fork): the lore from the story so far, or as the user wants it. */
        fun chatInstruction(personaName: String, characterName: String, wishes: String): String = buildString {
            append("[Out of character. Stop the roleplay for this one answer.] ")
            append("Write a background lore entry for ").append(personaName).append(", the character I play, in ")
            append(characterName).append("'s story. ")
            if (wishes.isNotBlank()) {
                append("Build it on what I want, exactly: ").append(wishes.trim()).append(" ")
                append("Use the character's definition and the story so far for everything else. ")
            } else {
                append("Work it out from the character's definition and everything that has happened in this chat so far, ")
                append("as a summary would: who ").append(personaName).append(" is to ").append(characterName)
                append(", what they've been through together, and where things stand now. ")
            }
            append("Cover how they know each other, their shared history, ").append(personaName)
            append("'s role and standing in this world, and one or two threads the story can pick up. ")
            append("Never contradict the definitions or the chat. Third person, plain prose, no headings, lists or markdown, ")
            append("about 120 to 220 words. No dialogue, no new scene. Reply with the entry only.")
        }

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
