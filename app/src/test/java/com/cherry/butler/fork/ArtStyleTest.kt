package com.cherry.butler.fork

import com.cherry.butler.core.data.ArtStyleFeatures
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ArtStyleTest {
    private val side = 32

    /** Flat blocks of colour with black outlines: cel-shaded. */
    private fun cel(seed: Int, base: Int): IntArray {
        val r = Random(seed)
        val a = IntArray(side * side) { base }
        repeat(4) {
            val x0 = r.nextInt(side - 8); val y0 = r.nextInt(side - 8); val c = 0xFF000000.toInt() or r.nextInt(0xFFFFFF)
            for (y in y0 until y0 + 8) for (x in x0 until x0 + 8) a[y * side + x] = if (x == x0 || y == y0) 0xFF000000.toInt() else c
        }
        return a
    }

    /** Smooth gradients with grain: painterly / photographic. */
    private fun soft(seed: Int): IntArray {
        val r = Random(seed)
        return IntArray(side * side) { i ->
            val x = i % side; val y = i / side
            val v = (x * 4 + y * 3 + r.nextInt(12)).coerceIn(0, 255)
            0xFF000000.toInt() or (v shl 16) or ((v * 3 / 4) shl 8) or (v / 2)
        }
    }

    @Test fun sameRenderingIsCloserThanDifferentRendering() {
        val a = ArtStyleFeatures.of(cel(1, 0xFFE0C0A0.toInt()), side, side)
        val b = ArtStyleFeatures.of(cel(2, 0xFFE0C0A0.toInt()), side, side)
        val c = ArtStyleFeatures.of(soft(3), side, side)
        val spread = ArtStyleFeatures.spread(listOf(a, b, c))
        assertTrue(ArtStyleFeatures.distance(a, b, spread) < ArtStyleFeatures.distance(a, c, spread))
        assertTrue(ArtStyleFeatures.distance(a, b, spread, colours = false) < ArtStyleFeatures.distance(a, c, spread, colours = false))
    }

    @Test fun flatPictureReadsAsFlat() {
        val f = ArtStyleFeatures.of(cel(4, 0xFF336699.toInt()), side, side)
        val g = ArtStyleFeatures.of(soft(5), side, side)
        assertTrue(f[3] > g[3])
    }
}
