package com.cherry.butler.core.data

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.CreatorRemoteSource
import com.cherry.butler.core.data.remote.TagRemoteSource
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.model.CharacterSort
import com.cherry.butler.core.model.SpecialMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A picture's art style as numbers (fork feature): how it's drawn more than what it shows.
 *
 * - **Rendering**: how much line work, how many hard edges and dark outlines, how much of it
 *   is flat colour (cel shading) against soft gradients (painting, 3D, photos), how many
 *   distinct colours it uses.
 * - **Tone**: how saturated and how bright, and how much each varies.
 * - **Palette**: which hues it leans on.
 *
 * It sees the picture, not the style's name: two anime pictures in one artist's hand match
 * well; "the same artist" is not something pixels can promise.
 */
object ArtStyleFeatures {
    const val HUES = 12
    /** Rendering features, then tone, then the hue histogram. */
    const val RENDER = 7
    const val TONE = 4
    const val SIZE = RENDER + TONE + HUES

    /** [pixels] are ARGB, [w] by [h] (small: 48–96 px a side is plenty). */
    fun of(pixels: IntArray, w: Int, h: Int): FloatArray {
        val n = w * h
        val lum = FloatArray(n)
        val sat = FloatArray(n)
        val hue = FloatArray(HUES)
        val colours = HashSet<Int>()
        for (i in 0 until n) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val v = mx
            val s = if (mx <= 0f) 0f else (mx - mn) / mx
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
            sat[i] = s
            colours += ((p shr 20 and 0xF) shl 8) or ((p shr 12 and 0xF) shl 4) or (p shr 4 and 0xF)
            if (mx - mn > 0.04f && v > 0.08f) {
                val d = mx - mn
                var hh = when (mx) {
                    r -> ((g - b) / d) % 6f
                    g -> (b - r) / d + 2f
                    else -> (r - g) / d + 4f
                } / 6f
                if (hh < 0f) hh += 1f
                hue[(hh * HUES).toInt().coerceIn(0, HUES - 1)] += s
            }
        }
        var edgeSum = 0f; var strong = 0; var dark = 0; var flat = 0; var soft = 0; var counted = 0
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val i = y * w + x
            val dx = abs(lum[i + 1] - lum[i]); val dy = abs(lum[i + w] - lum[i])
            val gr = sqrt(dx * dx + dy * dy)
            counted++
            edgeSum += gr
            if (gr > 0.22f) { strong++; if (lum[i] < 0.22f) dark++ }
            val c = max(colourDiff(pixels[i], pixels[i + 1]), colourDiff(pixels[i], pixels[i + w]))
            if (c < 6) flat++ else if (c < 26) soft++
        }
        val k = counted.coerceAtLeast(1).toFloat()
        val out = FloatArray(SIZE)
        out[0] = edgeSum / k
        out[1] = strong / k
        out[2] = if (strong == 0) 0f else dark.toFloat() / strong
        out[3] = flat / k
        out[4] = soft / k
        out[5] = colours.size.toFloat() / n
        out[6] = meanAbsLaplacian(lum, w, h)
        val (sm, ss) = meanStd(sat); val (lm, ls) = meanStd(lum)
        out[7] = sm; out[8] = ss; out[9] = lm; out[10] = ls
        val total = hue.sum()
        for (j in 0 until HUES) out[RENDER + TONE + j] = if (total > 0f) hue[j] / total else 0f
        return out
    }

    /**
     * How far apart two styles are (0 is identical). [spread] is each feature's spread over the
     * pictures being compared, so no one feature drowns the rest. [colours] false compares the
     * way it's drawn only, not the palette.
     */
    fun distance(a: FloatArray, b: FloatArray, spread: FloatArray, colours: Boolean = true): Float {
        var d = 0f
        for (j in 0 until RENDER) d += 3f * abs(a[j] - b[j]) / spread[j]
        for (j in RENDER until RENDER + TONE) d += (if (colours) 1.5f else 0.5f) * abs(a[j] - b[j]) / spread[j]
        if (colours) {
            var hueL1 = 0f
            for (j in RENDER + TONE until SIZE) hueL1 += abs(a[j] - b[j])
            d += 4f * hueL1
        }
        return d
    }

    /** Each feature's spread (standard deviation, never zero) over [all]. */
    fun spread(all: List<FloatArray>): FloatArray = FloatArray(SIZE) { j ->
        val xs = FloatArray(all.size) { all[it][j] }
        meanStd(xs).second.coerceAtLeast(1e-3f)
    }

    private fun colourDiff(a: Int, b: Int): Int =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))

    private fun meanAbsLaplacian(l: FloatArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f
        var s = 0f; var c = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            s += abs(4 * l[i] - l[i - 1] - l[i + 1] - l[i - w] - l[i + w]); c++
        }
        return s / c
    }

    private fun meanStd(xs: FloatArray): Pair<Float, Float> {
        if (xs.isEmpty()) return 0f to 0f
        val m = xs.average().toFloat()
        var v = 0f
        for (x in xs) v += (x - m) * (x - m)
        return m to sqrt(v / xs.size)
    }
}

/** One look-alike and how close it is (0 is the same). */
data class StyleMatch(val character: Character, val distance: Float)

/**
 * Finds characters drawn like a given one (fork feature). Candidates come from where a style
 * is likeliest to repeat: the same creator's other characters, Janitor's similar list, the
 * character's own tags, then what's popular and trending. Each round looks further.
 */
@Singleton
class ArtStyleRepository @Inject constructor(
    private val client: OkHttpClient,
    private val characters: CharacterRemoteSource,
    private val creators: CreatorRemoteSource,
    private val tagSource: TagRemoteSource,
) {
    /** By avatar file: a picture is measured once a session. Null: it couldn't be read. */
    private val measured = ConcurrentHashMap<String, FloatArray>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(8)

    /** Measures [avatarUrl]'s picture (a small copy from the CDN). */
    suspend fun features(avatarUrl: String?): FloatArray? {
        val url = avatarUrl ?: return null
        measured[url]?.let { return it }
        if (url in failed) return null
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val small = JanitorConfig.sizedMediaUrl(url, 96) ?: url
                    val bytes = client.newCall(Request.Builder().url(small).get().build()).execute().use { r ->
                        check(r.isSuccessful) { "HTTP ${r.code}" }
                        r.body!!.bytes()
                    }
                    val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("not a picture")
                    val side = 64
                    val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, side, side, true)
                    val px = IntArray(side * side)
                    scaled.getPixels(px, 0, side, 0, 0, side, side)
                    if (scaled !== decoded) scaled.recycle()
                    decoded.recycle()
                    ArtStyleFeatures.of(px, side, side)
                }.onSuccess { measured[url] = it }.onFailure { failed += url }.getOrNull()
            }
        }
    }

    /** Where round [round] looks (0 first): each round adds pages further in. */
    suspend fun candidates(creatorId: String?, tagNames: List<String>, similar: List<Character>, round: Int): List<Character> = coroutineScope {
        val tagIds = if (tagNames.isEmpty()) emptyList() else runCatching {
            val wanted = tagNames.map { it.lowercase().filter { ch -> ch.isLetterOrDigit() } }.toSet()
            tagSource.tags().filter { t -> t.name.lowercase().filter { it.isLetterOrDigit() } in wanted }.map { it.id }
        }.getOrDefault(emptyList())
        val pages = buildList<suspend () -> List<Character>> {
            if (round == 0) add { similar }
            if (!creatorId.isNullOrBlank()) {
                val p = round + 1
                add { creators.characters(creatorId, p).data.map { it.toCharacter() } }
            }
            tagIds.take(3).forEach { id ->
                add { characters.browse(BrowseQuery(tagIds = listOf(id), special = null, sort = CharacterSort.Popular), round + 1).data.map { it.toCharacter() } }
            }
            add { characters.browse(BrowseQuery(special = SpecialMode.Trending24), round + 1).data.map { it.toCharacter() } }
            add { characters.browse(BrowseQuery(special = null, sort = CharacterSort.Popular), round + 1).data.map { it.toCharacter() } }
            if (round > 0) add { characters.browse(BrowseQuery(special = SpecialMode.TrendingWeek), round).data.map { it.toCharacter() } }
        }
        pages.map { load -> async { runCatching { load() }.getOrDefault(emptyList()) } }.awaitAll().flatten().distinctBy { it.id }
    }

    /**
     * Ranks [pool] by how close each picture's style is to [seed]'s. [onMeasured] reports
     * progress (measured so far, of how many).
     */
    suspend fun rank(
        seed: FloatArray,
        pool: List<Character>,
        colours: Boolean,
        onMeasured: (Int, Int) -> Unit = { _, _ -> },
    ): List<StyleMatch> = coroutineScope {
        var done = 0
        val measuredPool = pool.map { c ->
            async {
                val f = features(c.avatarUrl)
                synchronized(this@ArtStyleRepository) { done++; onMeasured(done, pool.size) }
                f?.let { c to it }
            }
        }.awaitAll().filterNotNull()
        order(seed, measuredPool, colours)
    }

    /** Re-orders already measured pictures (switching colours on or off needs no new reads). */
    fun order(seed: FloatArray, measuredPool: List<Pair<Character, FloatArray>>, colours: Boolean): List<StyleMatch> {
        if (measuredPool.isEmpty()) return emptyList()
        val spread = ArtStyleFeatures.spread(measuredPool.map { it.second } + listOf(seed))
        return measuredPool.map { (c, f) -> StyleMatch(c, ArtStyleFeatures.distance(seed, f, spread, colours)) }.sortedBy { it.distance }
    }

    /** The measurement for an avatar already read, without reading it. */
    fun known(avatarUrl: String?): FloatArray? = avatarUrl?.let { measured[it] }
}
