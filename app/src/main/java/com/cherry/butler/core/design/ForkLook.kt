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
)

val LocalForkLook = staticCompositionLocalOf { ForkLook() }
