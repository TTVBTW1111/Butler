package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.CommunityRemoteSource
import com.cherry.butler.core.data.remote.dto.CharacterDto
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.BrowseSource
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.model.CharacterSort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.max

/** One card of the For you feed (fork feature): the character, and in plain words why it's here. */
data class ForYouPick(val character: Character, val score: Double, val reasons: List<String>)

/**
 * The For you scoring (fork feature), kept free of Android and the network so it can be tested.
 *
 * What you like is read from what you do: the characters you chat with (more messages, more
 * recently, and as the persona you're playing now, count more) and your favourites. Those are
 * the seeds. A candidate scores from the seeds Janitor calls it similar to, from the tags it
 * shares with your seeds, from being by a creator you follow, and from fitting your persona's
 * point of view; tags you've said "not for me" to count against it.
 */
object ForYouScoring {

    data class Seed(val id: String, val name: String, val weight: Double)

    /** A candidate and where it came from: seeds it is similar to, or other sources. */
    data class Candidate(
        val dto: CharacterDto,
        val similarTo: MutableList<Seed> = mutableListOf(),
        var followed: Boolean = false,
        var byTag: Boolean = false,
    )

    /** How much a character you chat with counts: messages, softened, and how recently. */
    fun chatWeight(messages: Int, lastAt: Long?, now: Long, samePersona: Boolean): Double {
        if (messages <= 0) return 0.0
        val days = if (lastAt == null) 365.0 else max(0.0, (now - lastAt) / 86_400_000.0)
        val recency = 0.45 + 0.55 / (1.0 + days / 21.0) // a month ago still counts about two thirds
        return ln(1.0 + messages) * recency * (if (samePersona) 1.6 else 1.0)
    }

    const val FAVORITE_WEIGHT = 2.5

    /** A tag's name without the emoji and spacing Janitor puts in front: "👤 AnyPOV" → "anypov". */
    fun tagKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Whose point of view a character is written for, from its tags; null when the tags don't say.
     * Janitor's tags read MalePOV, FemPOV (or FemalePOV), AnyPOV.
     */
    fun povOf(tags: List<String>): String? {
        val keys = tags.map(::tagKey)
        return when {
            keys.any { it == "anypov" } -> "any"
            keys.any { it == "malepov" } -> "male"
            keys.any { it == "fempov" || it == "femalepov" } -> "female"
            else -> null
        }
    }

    /** The persona's point of view from its pronouns: he → male, she → female, else any. */
    fun povOfPronoun(subjective: String?): String? = when (subjective?.trim()?.lowercase()) {
        "he" -> "male"
        "she" -> "female"
        null, "" -> null
        else -> "any"
    }

    /** +0.8 when the character is written for you (or anyone), −1.5 when it's written for someone else. */
    fun povFit(characterPov: String?, personaPov: String?): Double = when {
        characterPov == null || personaPov == null -> 0.0
        characterPov == "any" || characterPov == personaPov -> 0.8
        personaPov == "any" -> 0.0
        else -> -1.5
    }

    /** Tag weights from the seeds' tags: each seed's weight shared over its tags. */
    fun tagProfile(seedTags: Map<String, List<String>>, seeds: List<Seed>): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (seed in seeds) {
            val tags = seedTags[seed.id].orEmpty().map(::tagKey).filter { it.isNotEmpty() && povOf(listOf(it)) == null }
            if (tags.isEmpty()) continue
            val share = seed.weight / tags.size
            for (t in tags) out[t] = (out[t] ?: 0.0) + share
        }
        return out
    }

    fun score(
        c: Candidate,
        tagWeights: Map<String, Double>,
        personaPov: String?,
        dislikedTags: Set<String>,
    ): Pair<Double, List<String>> {
        val tags = c.dto.tags.map { it.name } + c.dto.customTags
        val keys = tags.map(::tagKey).toSet()
        val top = tagWeights.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val shared = keys.mapNotNull { k -> tagWeights[k]?.let { k to it / top } }.sortedByDescending { it.second }
        var s = 0.0
        s += c.similarTo.sumOf { it.weight }
        s += 1.2 * shared.take(6).sumOf { it.second }
        if (c.followed) s += 1.5
        if (c.byTag) s += 0.4
        s += povFit(povOf(tags), personaPov)
        s -= 1.0 * keys.count { it in dislikedTags }
        s += ln(1.0 + c.dto.stats.chat.toDouble()) / 25.0 // a nudge toward bots people actually play

        val reasons = buildList {
            c.similarTo.sortedByDescending { it.weight }.take(2).map { it.name }.takeIf { it.isNotEmpty() }?.let {
                add("Like " + it.joinToString(" and ") + ", which you play")
            }
            if (c.followed) add("From a creator you follow")
            shared.take(3).map { (k, _) -> tags.first { t -> tagKey(t) == k } }.takeIf { it.isNotEmpty() }?.let {
                add("You like " + it.joinToString(", ") { t -> t.trim().trimStart { ch -> !ch.isLetterOrDigit() } })
            }
            if (povFit(povOf(tags), personaPov) > 0) add("Fits the persona you're playing")
        }
        return s to reasons
    }
}

/**
 * Builds the For you feed (fork feature) from Janitor's own reads: your chat list (on this
 * device), your favourites, `/characters/{id}/similar`, Following, and tag searches. Nothing
 * is sent anywhere new; the "not for me" and seen marks stay on this device.
 */
@Singleton
class ForYouRepository @Inject constructor(
    db: ButlerDatabase,
    private val characters: CharacterRemoteSource,
    private val community: CommunityRemoteSource,
    private val favorites: FavoritesRepository,
    private val personas: PersonaRepository,
    @ApplicationContext context: Context,
) {
    private val chatDao = db.chatDao()
    private val prefs = context.getSharedPreferences("for_you", Context.MODE_PRIVATE)
    private val seedTags = HashMap<String, List<String>>()
    private val seedTagIds = HashMap<String, List<Int>>()

    /** Characters you've passed on ("not for me"), never shown again. */
    private fun notForMe(): Set<String> = prefs.getStringSet(KEY_NOT_FOR_ME, emptySet()).orEmpty()
    /** Tags of what you passed on: each counts a little against similar bots. */
    private fun dislikedTags(): Set<String> = prefs.getStringSet(KEY_DISLIKED_TAGS, emptySet()).orEmpty()
    /** Characters shown and scrolled past, with when: left out for two weeks. */
    private fun seen(): Map<String, Long> = prefs.getString(KEY_SEEN, "").orEmpty().split(';')
        .mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { (id, at) -> at.toLongOrNull()?.let { id to it } } }
        .toMap()

    fun markSeen(characterId: String) {
        val now = System.currentTimeMillis()
        val keep = seen().filterValues { now - it < SEEN_FOR } + (characterId to now)
        prefs.edit().putString(KEY_SEEN, keep.entries.toList().takeLast(2_000).joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    fun notForMe(pick: ForYouPick) {
        prefs.edit()
            .putStringSet(KEY_NOT_FOR_ME, notForMe() + pick.character.id)
            .putStringSet(KEY_DISLIKED_TAGS, (dislikedTags() + pick.character.tagNames.map(ForYouScoring::tagKey).filter { it.isNotEmpty() && ForYouScoring.povOf(listOf(it)) == null }).take(300).toSet())
            .apply()
    }

    /** Starts over: forgets what was passed on and what was seen. */
    fun reset() = prefs.edit().clear().apply()

    suspend fun build(limit: Int = 80): List<ForYouPick> = coroutineScope {
        val now = System.currentTimeMillis()
        val persona = personas.current.value
        val personaPov = ForYouScoring.povOfPronoun(persona?.pronouns?.subjective)
        runCatching { favorites.ensureLoaded() }
        val favIds = favorites.ids.value

        // Seeds: what you chat with (as this persona counts more), and your favourites.
        val activity = runCatching { chatDao.activityByCharacter() }.getOrDefault(emptyList())
        val chatted = activity.map { it.characterId }.toSet()
        val weights = HashMap<String, Double>()
        val names = HashMap<String, String>()
        for (a in activity) {
            weights[a.characterId] = (weights[a.characterId] ?: 0.0) +
                ForYouScoring.chatWeight(a.messages, a.lastAt, now, samePersona = persona != null && a.personaId == persona.id)
            names[a.characterId] = a.characterName
        }
        for (id in favIds) weights[id] = (weights[id] ?: 0.0) + ForYouScoring.FAVORITE_WEIGHT
        val seeds = weights.entries.sortedByDescending { it.value }.take(14)
            .map { ForYouScoring.Seed(it.key, names[it.key] ?: "a favourite", it.value) }

        // Candidates: similar to each seed, from creators you follow, and from your top tags.
        val gate = Semaphore(4)
        val pool = LinkedHashMap<String, ForYouScoring.Candidate>()
        fun add(dto: CharacterDto, f: (ForYouScoring.Candidate) -> Unit) {
            f(pool.getOrPut(dto.id) { ForYouScoring.Candidate(dto) })
        }
        val similar = seeds.map { seed -> async { gate.withPermit { seed to runCatching { community.similar(seed.id) }.getOrDefault(emptyList()) } } }
        val details = seeds.take(8).filter { it.id !in seedTags }.map { seed ->
            async {
                gate.withPermit {
                    runCatching { characters.detail(seed.id) }.getOrNull()?.let { d ->
                        synchronized(seedTags) {
                            seedTags[seed.id] = d.tags.map { it.name } + d.customTags.orEmpty()
                            seedTagIds[seed.id] = d.tags.map { it.id }
                        }
                    }
                }
            }
        }
        val following = async { gate.withPermit { runCatching { characters.browse(BrowseQuery(source = BrowseSource.Following, special = null, sort = CharacterSort.Latest), 1).data }.getOrDefault(emptyList()) } }
        details.forEach { it.await() }
        val tagWeights = ForYouScoring.tagProfile(seedTags, seeds)
        // The two strongest Janitor tags across the seeds, searched by popularity.
        val topTagIds = seeds.flatMap { s -> seedTagIds[s.id].orEmpty().map { it to s.weight } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }.take(2).map { it.key }
        val byTags = topTagIds.map { tag ->
            async { gate.withPermit { runCatching { characters.browse(BrowseQuery(special = null, tagIds = listOf(tag)), 1).data }.getOrDefault(emptyList()) } }
        }
        for ((seed, list) in similar.map { it.await() }) list.forEach { dto -> add(dto) { it.similarTo += seed } }
        following.await().forEach { dto -> add(dto) { it.followed = true } }
        byTags.forEach { job -> job.await().forEach { dto -> add(dto) { it.byTag = true } } }

        val skip = notForMe()
        val seenRecently = seen().filterValues { now - it < SEEN_FOR }.keys
        val disliked = dislikedTags()
        pool.values
            .filter { it.dto.id !in chatted && it.dto.id !in favIds && it.dto.id !in skip && it.dto.id !in seenRecently }
            .map { c ->
                val (score, reasons) = ForYouScoring.score(c, tagWeights, personaPov, disliked)
                ForYouPick(c.dto.toCharacter(), score, reasons)
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private companion object {
        const val KEY_NOT_FOR_ME = "not_for_me"
        const val KEY_DISLIKED_TAGS = "disliked_tags"
        const val KEY_SEEN = "seen"
        const val SEEN_FOR = 14L * 86_400_000L
    }
}
