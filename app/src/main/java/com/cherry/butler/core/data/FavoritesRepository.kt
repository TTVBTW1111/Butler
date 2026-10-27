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
) {
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
        runCatching {
            val query = BrowseQuery(source = BrowseSource.Favorites, special = null)
            for (page in 1..MAX_PAGES) {
                val rows = characters.browse(query, page).data
                rows.forEach { found += it.id }
                if (rows.size < CharacterRemoteSource.PAGE_SIZE) break
            }
        }.onSuccess {
            val taps = synchronized(changedDuringRead) { HashMap(changedDuringRead) }
            _ids.value = found.apply { taps.forEach { (id, on) -> if (on) add(id) else remove(id) } }
        }
        _known.value = true
    }

    fun isFavorite(characterId: String): Boolean = characterId in _ids.value

    /** Records what another screen learned (the character page asks Janitor directly). */
    fun mark(characterId: String, on: Boolean) {
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
