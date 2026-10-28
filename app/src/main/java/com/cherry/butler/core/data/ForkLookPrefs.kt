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
            wallpaper = prefs.getString("wallpaper", null)?.takeIf { java.io.File(it).isFile },
            wallpaperKey = prefs.getLong("wallpaper_key", 0),
            wallpaperDim = prefs.getFloat("wallpaper_dim", 0.35f),
            wallpaperBlur = prefs.getFloat("wallpaper_blur", 0f),
            glass = prefs.getFloat("glass", 0f),
            dock = read("dock", com.cherry.butler.core.design.DockStyle.Rail),
            dockRecents = prefs.getBoolean("dock_recents", true),
        ),
    )
    private val files = context.filesDir
    private val resolver = context.contentResolver
    val look: StateFlow<ForkLook> = _look.asStateFlow()

    fun setTileSize(v: TileSize) = put("tile_size", v.name) { it.copy(tileSize = v) }
    fun setTileShape(v: TileShape) = put("tile_shape", v.name) { it.copy(tileShape = v) }
    fun setPictureFit(v: PictureFit) = put("picture_fit", v.name) { it.copy(pictureFit = v) }
    fun setBrowseWidth(v: BrowseWidth) = put("browse_width", v.name) { it.copy(browseWidth = v) }

    fun setWallpaperDim(v: Float) { prefs.edit().putFloat("wallpaper_dim", v).apply(); _look.update { it.copy(wallpaperDim = v) } }
    fun setWallpaperBlur(v: Float) { prefs.edit().putFloat("wallpaper_blur", v).apply(); _look.update { it.copy(wallpaperBlur = v) } }
    fun setDock(v: com.cherry.butler.core.design.DockStyle) = put("dock", v.name) { it.copy(dock = v) }
    fun setDockRecents(v: Boolean) { prefs.edit().putBoolean("dock_recents", v).apply(); _look.update { it.copy(dockRecents = v) } }
    fun setGlass(v: Float) { prefs.edit().putFloat("glass", v).apply(); _look.update { it.copy(glass = v) } }

    /** Copies the picture into the app's own storage, so it stays when the original moves. */
    suspend fun setWallpaper(uri: android.net.Uri) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val target = java.io.File(files, "fork-wallpaper")
        val tmp = java.io.File(files, "fork-wallpaper.tmp")
        val input = resolver.openInputStream(uri) ?: error("Couldn't open that picture.")
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        val key = System.currentTimeMillis()
        prefs.edit().putString("wallpaper", target.path).putLong("wallpaper_key", key).apply()
        _look.update { it.copy(wallpaper = target.path, wallpaperKey = key) }
    }

    fun clearWallpaper() {
        java.io.File(files, "fork-wallpaper").delete()
        prefs.edit().remove("wallpaper").apply()
        _look.update { it.copy(wallpaper = null) }
    }

    /** Everything back to Butler's own look (the wallpaper file goes too). */
    fun reset() {
        java.io.File(files, "fork-wallpaper").delete()
        prefs.edit().clear().apply()
        _look.value = ForkLook()
    }

    private fun put(key: String, value: String, change: (ForkLook) -> ForkLook) {
        prefs.edit().putString(key, value).apply()
        _look.update(change)
    }
}
