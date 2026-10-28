package com.cherry.butler.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How big a character card is: how many fit across follows from it. */
enum class TileSize(val label: String, val widest: Dp) {
    Small("Small", 150.dp),
    Medium("Medium", 196.dp),
    Large("Large", 250.dp),
}

/** The card picture's shape, height over width; [wire] is the CDN's crop for it. */
enum class TileShape(val label: String, val ratio: Float, val wire: String) {
    Classic("Classic", 1.08f, "25:27"),
    Tall("Tall", 1.4f, "5:7"),
    Square("Square", 1f, "1:1"),
}

/** How a picture sits in its frame. */
enum class PictureFit(val label: String, val blurb: String) {
    Fill("Fill the frame", "Cropped to the frame, as Butler always did"),
    Top("Fill, keep the top", "Cropped, but from the top, so faces stay in"),
    Whole("Whole picture", "Never cropped; the space around it is a soft blur of it"),
}

/** How wide Browse is on the PC: Butler's four-card column, six, or the whole window. */
enum class BrowseWidth(val label: String, val columns: Int?) {
    Classic("4 across", 4),
    Wide("6 across", 6),
    Full("Whole window", null),
}

/** Where the PC's dock (the rail of sections and recent chats) sits, and how. */
enum class DockStyle(val label: String, val blurb: String) {
    Rail("Rail", "Down the left edge, as Butler's own"),
    Floating("Floating dock", "A rounded island on the left, with room around it"),
    Right("Rail on the right", "Down the right edge"),
    Bottom("Bottom taskbar", "Centred along the bottom, like Windows 11"),
}

/**
 * The fork's look settings (ricing): every one optional, and the defaults are Butler's own
 * look exactly. Handed down by [ButlerTheme] as [LocalForkLook].
 */
@Immutable
data class ForkLook(
    val tileSize: TileSize = TileSize.Medium,
    val tileShape: TileShape = TileShape.Classic,
    val pictureFit: PictureFit = PictureFit.Fill,
    val browseWidth: BrowseWidth = BrowseWidth.Classic,
    /** The PC's wallpaper behind the whole window: a file in the app's own storage, or none. */
    val wallpaper: String? = null,
    /** Changes with each new wallpaper, so the picture is read again. */
    val wallpaperKey: Long = 0,
    /** How much darker the wallpaper is drawn (0 none, 0.8 nearly black). */
    val wallpaperDim: Float = 0.35f,
    /** Wallpaper blur, in dp. */
    val wallpaperBlur: Float = 0f,
    /** How see-through the panels are over the wallpaper (0 solid, as Butler). */
    val glass: Float = 0f,
    val dock: DockStyle = DockStyle.Rail,
    /** Whether the dock shows your recent chats beside the sections. */
    val dockRecents: Boolean = true,
) {
    val hasWallpaper: Boolean get() = wallpaper != null
}

val LocalForkLook = staticCompositionLocalOf { ForkLook() }
