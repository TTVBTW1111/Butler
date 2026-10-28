package com.cherry.butler.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cherry.butler.core.data.CharacterRepository
import com.cherry.butler.core.data.remote.TagRemoteSource
import com.cherry.butler.core.data.remote.dto.TagDto
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.model.CharacterSort
import com.cherry.butler.core.model.NsfwMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import com.cherry.butler.core.data.LastPlace
import com.cherry.butler.core.model.BrowseSource
import com.cherry.butler.core.model.SpecialMode
import androidx.paging.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.drop
import javax.inject.Inject
import com.cherry.butler.ui.components.userMessage

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: CharacterRepository,
    private val tagRemoteSource: TagRemoteSource,
    private val lastPlace: LastPlace,
    browsePrefs: com.cherry.butler.core.data.BrowsePrefs,
    private val favorites: com.cherry.butler.core.data.FavoritesRepository,
) : ViewModel() {

    /** Fork: your favourites for the hearts on the cards; null until they've been read. */
    val favoriteIdsOrNull: StateFlow<Set<String>?> = combine(favorites.ids, favorites.known) { ids, known -> if (known) ids else null }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    private val _forYou = MutableStateFlow(false)
    /** Fork: whether the page shows the For you feed instead of the grid. */
    val forYou: StateFlow<Boolean> = _forYou.asStateFlow()
    fun setForYou(on: Boolean) { _forYou.value = on }

    private val _favoriteError = MutableStateFlow<String?>(null)
    /** Why a heart didn't stick, for a moment under the grid. */
    val favoriteError: StateFlow<String?> = _favoriteError.asStateFlow()

    fun toggleFavorite(characterId: String) {
        val on = !favorites.isFavorite(characterId)
        viewModelScope.launch {
            favorites.set(characterId, on).onFailure { e ->
                val why = e.userMessage()
                _favoriteError.value = "Not ${if (on) "favourited" else "unfavourited"}. $why".trim()
                kotlinx.coroutines.delay(5_000)
                _favoriteError.value = null
            }
        }
    }

    /**
     * The grid's scroll state lives here, not in the screen: opening a character takes the
     * tab out of composition, and a state remembered there came back at the top.
     */
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val gridState = androidx.compose.foundation.lazy.grid.LazyGridState(
        prefetchStrategy = com.cherry.butler.ui.components.AheadGridPrefetchStrategy(2),
    )

    private var scrolledFor: BrowseQuery? = null

    /** True once per new search, sort or filter, when the grid should go back to the top. */
    fun takeFreshQuery(query: BrowseQuery): Boolean {
        if (scrolledFor == query) return false
        scrolledFor = query
        return true
    }

    private var itemsSeenFor: BrowseQuery? = null
    fun sawItems(query: BrowseQuery) { itemsSeenFor = query }
    /** Results for [query] were on screen before: a momentary empty list is a re-read, not "nothing". */
    fun hadItems(query: BrowseQuery): Boolean = itemsSeenFor == query

    /** One page at a time instead of the endless scroll (Settings › Look). */
    val paged: StateFlow<Boolean> = browsePrefs.paged

    /** The paged Home's page: its number, what's on it, and whether another follows. */
    data class Page(
        val number: Int = 1,
        val items: List<Character> = emptyList(),
        val loading: Boolean = true,
        val error: Throwable? = null,
        val hasMore: Boolean = false,
        /** Fork: how many pages there are, when that's known (the sorted Favorites list). */
        val total: Int? = null,
        /** Fork: the furthest page seen so far, so the numbers can offer it. */
        val furthest: Int = number,
    )

    /** Fork: the Pages / Scroll switch on Browse itself (same setting as Settings › Look). */
    fun setPaged(on: Boolean) = browsePrefs.setPaged(on)

    /** Fork: the Favorites order. Anything but Janitor's is sorted here, from the whole list. */
    val favoriteOrder: StateFlow<com.cherry.butler.core.model.FavoriteOrder> = browsePrefs.favoriteOrder
    fun setFavoriteOrder(order: com.cherry.butler.core.model.FavoriteOrder) {
        browsePrefs.setFavoriteOrder(order)
        _favPage.value = 1
    }
    private val _favPage = MutableStateFlow(1)

    /** The sorted Favorites list as a page (all of it in one, with endless scroll), or null when not in use. */
    val favoriteView: StateFlow<Page?> by lazy {
        combine(_query, favoriteOrder, favorites.entries, favorites.reading, combine(_favPage, paged) { a, b -> a to b }) { q, order, entries, reading, (pageNo, pagedOn) ->
            if (q.source != BrowseSource.Favorites || order == com.cherry.butler.core.model.FavoriteOrder.Janitor) return@combine null
            val chosenTags = _tags.value.filter { it.id in q.tagIds }.map { it.name.lowercase() }
            val words = q.search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            val kept = com.cherry.butler.core.model.FavoriteSorting.sort(entries, order).map { it.character }.filter { c ->
                (words == null || c.name.lowercase().contains(words) || c.creatorName.lowercase().contains(words)) &&
                    chosenTags.all { t -> c.tagNames.any { it.lowercase() == t } } &&
                    q.customTags.all { t -> t in c.customTags } &&
                    (q.mode != com.cherry.butler.core.model.NsfwMode.Sfw || !c.isNsfw) &&
                    c.messageCount >= q.minMessages && c.totalTokens >= q.minTokens && (!q.proxyOnly || c.isProxyEnabled)
            }
            if (reading && entries.isEmpty()) return@combine Page(loading = true)
            if (!pagedOn) return@combine Page(number = 1, items = kept, loading = false, total = 1)
            val total = maxOf(1, (kept.size + FAV_PAGE - 1) / FAV_PAGE)
            val n = pageNo.coerceIn(1, total)
            Page(number = n, items = kept.drop((n - 1) * FAV_PAGE).take(FAV_PAGE), loading = false, hasMore = n < total, total = total, furthest = total)
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)
    }

    fun favoritePage(n: Int) { _favPage.value = n.coerceAtLeast(1) }
    fun refreshFavorites() { viewModelScope.launch { favorites.freshEntries() } }

    /** Jump straight to a page (the numbers under the grid, or "Go to"). */
    fun goToPage(n: Int) { if (n >= 1 && !_page.value.loading) loadPage(n) }

    private val _page = MutableStateFlow(Page())
    val page: StateFlow<Page> = _page.asStateFlow()
    private var pageJob: kotlinx.coroutines.Job? = null
    private var pageQuery: BrowseQuery? = null

    fun nextPage() { if (_page.value.hasMore && !_page.value.loading) loadPage(_page.value.number + 1) }
    fun previousPage() { if (_page.value.number > 1 && !_page.value.loading) loadPage(_page.value.number - 1) }
    fun retryPage() = loadPage(_page.value.number)

    private fun loadPage(number: Int) {
        val query = pageQuery ?: _query.value.server()
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            val furthest = if (pageQuery == query) maxOf(_page.value.furthest, number) else number
            _page.value = Page(number = number, loading = true, furthest = furthest)
            val result = runCatching { repository.page(query, number) }
            val filters = _query.value
            _page.value = result.fold(
                onSuccess = { (items, more) ->
                    val kept = items.filter {
                        it.messageCount >= filters.minMessages && it.totalTokens >= filters.minTokens && (!filters.proxyOnly || it.isProxyEnabled)
                    }
                    Page(number = number, items = kept, loading = false, hasMore = more, furthest = if (more) maxOf(furthest, number + 1) else number)
                },
                onFailure = { Page(number = number, loading = false, error = it, furthest = furthest) },
            )
        }
    }

    /** Opens on the search, sort and filters the user left, not the defaults. */
    private val _query = MutableStateFlow(lastPlace.browseQuery())
    val query: StateFlow<BrowseQuery> = _query.asStateFlow()

    /** What the text field shows, kept separate from the debounced [query] it feeds. */
    private val _searchInput = MutableStateFlow(_query.value.search.orEmpty())
    val searchInput: StateFlow<String> = _searchInput.asStateFlow()

    /**
     * Debounced so typing doesn't fire a request per keystroke, and
     * [distinctUntilChanged] so re-selecting the current sort is a no-op rather than a
     * refresh. `cachedIn` keeps the paged data alive across configuration changes.
     */
    private val pages: Flow<PagingData<Character>> = _query
        .map { it.server() to it }
        .debounce { (server, _) -> if (server.search.isNullOrBlank()) 0L else SEARCH_DEBOUNCE_MS }
        // The server's side and the phone-side minimums; a change to the minimums alone reads
        // the mirror again (the cache is fresh, so nothing is fetched).
        .map { (server, all) -> server.copy(minMessages = all.minMessages, minTokens = all.minTokens, proxyOnly = all.proxyOnly) }
        .distinctUntilChanged()
        .flatMapLatest { repository.browse(it) }
        .cachedIn(viewModelScope)

    /** The server's pages, with the phone-side minimums applied (in the mirror's query) as Janitor's app does. */
    val characters: Flow<PagingData<Character>> = pages

    fun onSearchChanged(value: String) {
        _searchInput.value = value
        _query.value = _query.value.copy(search = value.takeIf { it.isNotBlank() })
    }

    fun onClearSearch() {
        _searchInput.value = ""
        _query.value = _query.value.copy(search = null)
    }

    /**
     * The tag vocabulary. Failing to load it must not break browsing, so a failure
     * leaves the list empty and the picker simply offers nothing — the rest of the
     * screen keeps working.
     */
    private val _tags = MutableStateFlow<List<TagDto>>(emptyList())
    val tags: StateFlow<List<TagDto>> = _tags.asStateFlow()

    init {
        viewModelScope.launch { favorites.ensureLoaded() }
        viewModelScope.launch { _query.collect { lastPlace.saveBrowseQuery(it) } }
        // Paged Home: a new query starts over at page 1; the phone-side minimums re-filter in place.
        viewModelScope.launch {
            combine(
                _query.map { it.server() }.debounce { if (it.search.isNullOrBlank()) 0L else SEARCH_DEBOUNCE_MS }.distinctUntilChanged(),
                paged,
            ) { q, on -> q to on }.collect { (q, on) ->
                if (pageQuery != q) _page.value = Page()
                pageQuery = q
                if (on) loadPage(1)
            }
        }
        viewModelScope.launch {
            _query.map { Triple(it.minMessages, it.minTokens, it.proxyOnly) }.distinctUntilChanged().drop(1)
                .collect { if (paged.value) loadPage(_page.value.number) }
        }
        viewModelScope.launch {
            runCatching { tagRemoteSource.tags() }
                .onSuccess { loaded -> _tags.value = loaded.sortedBy { it.name.lowercase() } }
        }
    }

    fun onTagToggled(tagId: Int) {
        val current = _query.value.tagIds
        val next = if (tagId in current) current - tagId else current + tagId
        // Server-enforced ceiling; going over is a 400.
        _query.value = _query.value.copy(tagIds = next.take(MAX_TAGS))
    }

    fun onClearTags() {
        _query.value = _query.value.copy(tagIds = emptyList())
    }

    /** The custom tags most used in the results on screen, offered as one-tap additions. */
    val topCustomTags: StateFlow<List<String>> get() = repository.topCustomTags

    /** Adds a creator's tag as typed: `#`, spaces and case don't matter. */
    fun onCustomTagAdded(raw: String) {
        val tag = normalizeCustomTag(raw) ?: return
        val current = _query.value.customTags
        if (tag in current || current.size >= MAX_CUSTOM_TAGS) return
        _query.value = _query.value.copy(customTags = current + tag)
    }

    fun onCustomTagRemoved(tag: String) {
        _query.value = _query.value.copy(customTags = _query.value.customTags - tag)
    }

    /** A "Popular" dropdown pick; leaves Trending and Hidden Gems. */
    fun onSortSelected(sort: CharacterSort) {
        _query.value = _query.value.copy(sort = sort, special = null)
    }

    /** A Trending window or Hidden Gems. */
    fun onSpecialSelected(special: SpecialMode) {
        _query.value = _query.value.copy(special = special)
    }

    fun onModeSelected(mode: NsfwMode) {
        _query.value = _query.value.copy(mode = mode)
    }

    fun onSourceSelected(source: BrowseSource) {
        _query.value = _query.value.copy(source = source)
    }

    fun onMinMessages(value: Long) {
        _query.value = _query.value.copy(minMessages = value.coerceAtLeast(0))
    }

    fun onProxyOnly(on: Boolean) {
        _query.value = _query.value.copy(proxyOnly = on)
    }

    fun onMinTokens(value: Int) {
        _query.value = _query.value.copy(minTokens = value.coerceAtLeast(0))
    }

    /** The filter sheet's eraser: back to Janitor's defaults, search and dropdowns untouched. */
    fun onResetFilters() {
        _query.value = _query.value.copy(mode = NsfwMode.All, minMessages = 0, minTokens = 0, proxyOnly = false)
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
        /** The sorted Favorites list, a page at a time. */
        private const val FAV_PAGE = 36
        /** `tag_id[] must contain no more than 53 elements` — server-enforced. */
        private const val MAX_TAGS = 53
        /** A guard, not Janitor's limit (none seen): past a few, nothing matches anyway. */
        private const val MAX_CUSTOM_TAGS = 10

        /** Custom tags come back lowercase with no spaces (`kinktober2026`); typed ones match that. */
        fun normalizeCustomTag(raw: String): String? =
            raw.trim().removePrefix("#").lowercase().filterNot { it.isWhitespace() }.takeIf { it.isNotEmpty() }
    }
}
