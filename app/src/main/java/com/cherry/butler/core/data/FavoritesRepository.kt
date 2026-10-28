package com.cherry.butler.core.data

import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.CommunityRemoteSource
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.BrowseSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which characters you've favourited (fork feature), so every card can carry its heart.
 *
 * Janitor's browse rows don't say whether a character is a favourite, so the set is read once
 * from the Favorites filter (`favorites=true`, the list Browse already shows) and kept current
 * by the hearts themselves. A heart changes at once and goes back if Janitor says no.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val characters: CharacterRemoteSource,
    private val community: CommunityRemoteSource,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) {
    /** When you hearted each character in Butler (Janitor doesn't seem to say): id → millis. */
    private val hearted = context.getSharedPreferences("fork_favorites", android.content.Context.MODE_PRIVATE)

    /** Your whole favourites list with dates, for the Favorites sort (read with the ids). */
    private val _entries = MutableStateFlow<List<com.cherry.butler.core.model.FavoriteEntry>>(emptyList())
    val entries: StateFlow<List<com.cherry.butler.core.model.FavoriteEntry>> = _entries.asStateFlow()
    private val _reading = MutableStateFlow(false)
    val reading: StateFlow<Boolean> = _reading.asStateFlow()
    @Volatile private var stale = false

    /** Reads the list again if a heart changed since (the sorted Favorites view asks). */
    suspend fun freshEntries() { if (stale || !_known.value) ensureLoaded(force = true) }

    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /**
     * True once a read has finished, or failed: hearts are drawn from then on. A failed read
     * still shows them (empty), so the heart is never missing; tapping one just favourites.
     */
    private val _known = MutableStateFlow(false)
    val known: StateFlow<Boolean> = _known.asStateFlow()

    private val loading = Mutex()

    /** Taps made while a read is running: they win over what the read saw. */
    private val changedDuringRead = HashMap<String, Boolean>()

    suspend fun ensureLoaded(force: Boolean = false) = loading.withLock {
        if (_known.value && !force) return@withLock
        synchronized(changedDuringRead) { changedDuringRead.clear() }
        val found = HashSet<String>()
        val rows = ArrayList<com.cherry.butler.core.model.FavoriteEntry>()
        _reading.value = true
        stale = false
        runCatching {
            val query = BrowseQuery(source = BrowseSource.Favorites, special = null)
            for (page in 1..MAX_PAGES) {
                val got = characters.browse(query, page).data
                got.forEach { dto ->
                    if (found.add(dto.id)) rows += com.cherry.butler.core.model.FavoriteEntry(
                        character = dto.toCharacter(),
                        position = rows.size,
                        favoritedAt = com.cherry.butler.core.util.IsoTime.parseMillis(dto.favoritedAt ?: dto.favoriteCreatedAt)
                            ?: hearted.getLong(dto.id, 0L).takeIf { it > 0 },
                        createdAt = com.cherry.butler.core.util.IsoTime.parseMillis(dto.firstPublishedAt ?: dto.createdAt),
                    )
                }
                if (got.size < CharacterRemoteSource.PAGE_SIZE) break
            }
        }.onSuccess {
            val taps = synchronized(changedDuringRead) { HashMap(changedDuringRead) }
            _ids.value = found.apply { taps.forEach { (id, on) -> if (on) add(id) else remove(id) } }
            _entries.value = rows.filter { it.character.id in _ids.value }
        }
        _reading.value = false
        _known.value = true
    }

    fun isFavorite(characterId: String): Boolean = characterId in _ids.value

    /** Records what another screen learned (the character page asks Janitor directly). */
    fun mark(characterId: String, on: Boolean) {
        if (on != (characterId in _ids.value)) {
            stale = true
            if (on) hearted.edit().putLong(characterId, System.currentTimeMillis()).apply()
            else { hearted.edit().remove(characterId).apply(); _entries.update { list -> list.filterNot { it.character.id == characterId } } }
        }
        _ids.update { if (on) it + characterId else it - characterId }
        synchronized(changedDuringRead) { changedDuringRead[characterId] = on }
    }

    /** The heart on a card: at once on screen, then on Janitor; back as it was if that fails. */
    suspend fun set(characterId: String, on: Boolean): Result<Unit> {
        val before = isFavorite(characterId)
        mark(characterId, on)
        return runCatching { community.setFavorite(characterId, on) }
            .onFailure { mark(characterId, before) }
    }

    private companion object {
        /** 34 a page: up to a few thousand favourites, read in the background. */
        const val MAX_PAGES = 60
    }
}

/**
 * Creators you follow, read from the Following filter (fork): what the character page falls
 * back on when Janitor won't answer "do I follow them?", so the Follow button never vanishes.
 */
@Singleton
class FollowedCreators @Inject constructor(private val characters: CharacterRemoteSource) {
    private var known: MutableSet<String>? = null
    private val loading = Mutex()

    suspend fun isFollowing(creatorId: String): Boolean = loading.withLock {
        val set = known ?: HashSet<String>().also { found ->
            runCatching {
                val query = BrowseQuery(source = BrowseSource.Following, special = null)
                for (page in 1..20) {
                    val rows = characters.browse(query, page).data
                    rows.forEach { found += it.creatorId }
                    if (rows.size < CharacterRemoteSource.PAGE_SIZE) break
                }
            }
            known = found
        }
        creatorId in set
    }

    fun mark(creatorId: String, on: Boolean) {
        known?.let { if (on) it += creatorId else it -= creatorId }
    }
}
