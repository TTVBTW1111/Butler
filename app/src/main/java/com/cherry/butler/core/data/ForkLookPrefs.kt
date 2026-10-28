package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.design.BrowseWidth
import com.cherry.butler.core.design.ForkLook
import com.cherry.butler.core.design.PictureFit
import com.cherry.butler.core.design.TileShape
import com.cherry.butler.core.design.TileSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** The fork's look settings (ricing), kept on this device; nothing set means Butler's own look. */
@Singleton
class ForkLookPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fork_look", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> read(key: String, default: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private val _look = MutableStateFlow(
        ForkLook(
            tileSize = read("tile_size", TileSize.Medium),
            tileShape = read("tile_shape", TileShape.Classic),
            pictureFit = read("picture_fit", PictureFit.Fill),
            browseWidth = read("browse_width", BrowseWidth.Classic),
        ),
    )
    val look: StateFlow<ForkLook> = _look.asStateFlow()

    fun setTileSize(v: TileSize) = put("tile_size", v.name) { it.copy(tileSize = v) }
    fun setTileShape(v: TileShape) = put("tile_shape", v.name) { it.copy(tileShape = v) }
    fun setPictureFit(v: PictureFit) = put("picture_fit", v.name) { it.copy(pictureFit = v) }
    fun setBrowseWidth(v: BrowseWidth) = put("browse_width", v.name) { it.copy(browseWidth = v) }

    /** Everything back to Butler's own look. */
    fun reset() {
        prefs.edit().clear().apply()
        _look.value = ForkLook()
    }

    private fun put(key: String, value: String, change: (ForkLook) -> ForkLook) {
        prefs.edit().putString(key, value).apply()
        _look.update(change)
    }
}
