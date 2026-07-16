package id.myapp.progresshubkt

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random

/** Real bokeh (out-of-focus light) isn't uniformly circular — camera iris
 * shape, motion, and overlapping light sources produce rings, elongated
 * streaks, and soft rounded-square blobs alongside plain circles. Mixing
 * these in reads as photographic bokeh rather than "a bunch of blurred
 * dots", which is what a same-shape-every-time field tends to look like
 * however deep the blur is. */
enum class ParticleShape { CIRCLE, RING, CAPSULE, SQUIRCLE }

/** Ported from `particle_background.dart`'s `_Bokeh`: a drifting, wobbling
 * bokeh dot. Motion is expressed as a pure function of a continuously
 * increasing clock (`xFrac0`/`baseYFrac` + `time`), rather than mutated
 * frame by frame — mathematically identical to the Dart version's
 * per-frame position updates, without needing an imperative mutable-list
 * tick. */
data class Particle(
    val xFrac0: Float,        // 0..1 starting horizontal position
    val driftSpeedFrac: Float,// fraction of screen width drifted per second
    val baseYFrac: Float,     // 0..1 vertical anchor the wobble oscillates around
    val wobbleAmpPx: Float,   // px, vertical wobble amplitude
    val wobbleSpeed: Float,   // rad/sec
    val phase: Float,         // random phase offset so particles don't sync
    val radius: Float,        // px, core dot radius
    val alpha: Float,         // 0..1 peak opacity
    val color: Color,
    val shape: ParticleShape = ParticleShape.CIRCLE,
    val rotation: Float = 0f  // rad, static per-particle tilt for capsule/squircle
) {
    // Same idea as Dart's cached `corePaint`/`outerPaint`: built once per
    // particle (not once per frame per particle), since only position
    // changes frame to frame, never color/radius/blur.
    val corePaint: android.graphics.Paint by lazy {
        android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = this@Particle.color.copy(alpha = this@Particle.alpha).toArgb()
            maskFilter = BlurMaskFilter(radius * 0.7f, BlurMaskFilter.Blur.NORMAL)
            if (shape == ParticleShape.RING) style = android.graphics.Paint.Style.STROKE
            if (shape == ParticleShape.RING) strokeWidth = radius * 0.55f
        }
    }
    val outerPaint: android.graphics.Paint by lazy {
        android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = this@Particle.color.copy(alpha = this@Particle.alpha * 0.28f).toArgb()
            maskFilter = BlurMaskFilter(radius * 0.7f * 1.3f, BlurMaskFilter.Blur.NORMAL)
            if (shape == ParticleShape.RING) style = android.graphics.Paint.Style.STROKE
            if (shape == ParticleShape.RING) strokeWidth = radius * 0.8f
        }
    }
}

// Same four bokeh hues as Dart's `_colors` in particle_background.dart.
private val particlePalette = listOf(
    Color(0xFF9CE8D4), // mint
    Color(0xFFF6CF85), // gold
    Color(0xFFEF9D84), // coral
    Color(0xFF8FB8F0), // blue
)

internal fun generateParticles(count: Int, seed: Int = 42): List<Particle> {
    val rnd = Random(seed)
    val minR = 5f
    val maxR = 16f
    // Weighted so most particles stay plain circles (matches real bokeh —
    // it's mostly round dots with occasional standout shapes, not an even
    // mix), while rings/capsules/squircles add variety without the field
    // looking like a shape-sampler grid.
    fun randomShape(): ParticleShape {
        val r = rnd.nextFloat()
        return when {
            r < 0.62f -> ParticleShape.CIRCLE
            r < 0.78f -> ParticleShape.RING
            r < 0.91f -> ParticleShape.SQUIRCLE
            else -> ParticleShape.CAPSULE
        }
    }
    return List(count) {
        Particle(
            xFrac0 = rnd.nextFloat(),
            driftSpeedFrac = 0.008f + rnd.nextFloat() * 0.022f,
            baseYFrac = rnd.nextFloat(),
            wobbleAmpPx = 10f + rnd.nextFloat() * 34f,
            wobbleSpeed = 0.4f + rnd.nextFloat() * 0.9f,
            phase = rnd.nextFloat() * (2 * kotlin.math.PI).toFloat(),
            radius = minR + rnd.nextFloat() * (maxR - minR),
            alpha = 0.45f + rnd.nextFloat() * 0.40f,
            color = particlePalette[rnd.nextInt(particlePalette.size)],
            shape = randomShape(),
            rotation = rnd.nextFloat() * (2 * kotlin.math.PI).toFloat()
        )
    }
}

/** Screen area (in dp²) mapped to a particle count, mirroring Dart's
 * `area / 9000` density so bigger screens naturally get more bokeh. */
internal fun particleCountForArea(widthDp: Float, heightDp: Float): Int =
    ((widthDp * heightDp) / 9000f).toInt().coerceIn(16, 120)

/** The single source of truth for "what the particle field looks like right
 * now" — one shared particle list, one shared animation clock, and the pixel
 * size of the full-screen field they're laid out against. Hoisted once at
 * the app root and handed down via [LocalParticleField] so that every
 * [GlassCard] can redraw the exact same particles (translated to its own
 * position) and blur that redraw. */
internal data class ParticleFieldState(
    val particles: List<Particle>,
    val time: Float,
    val fieldSize: Size
)

internal val LocalParticleField = compositionLocalOf<ParticleFieldState?> { null }

/** Drives the shared particle animation clock as a continuously increasing
 * "seconds elapsed" value (never wraps) — same role as Dart's
 * `AnimationController` + `_t += 1/60`, but driven by actual frame timing
 * rather than an assumed fixed frame rate. */
@Composable
internal fun rememberParticleTime(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nanos = withFrameNanos { it }
            t += (nanos - lastNanos) / 1_000_000_000f
            lastNanos = nanos
        }
    }
    return t
}

/** Draws [particles] positioned against a field of size [fieldSize] into
 * whatever [DrawScope] is current — used both by the full-screen backdrop
 * and by each [GlassCard]'s local blurred echo of the same field.
 *
 * Motion mirrors Dart's bokeh: slow horizontal drift that wraps around, plus
 * an organic sinusoidal vertical wobble — not a one-directional loop. Each
 * dot is drawn as two layered, softly blurred circles (native
 * BlurMaskFilter, matching Dart's MaskFilter.blur) — a wide, faint outer
 * glow plus a tighter, brighter core — so they read as glowing bokeh rather
 * than flat colored discs. */
internal fun DrawScope.drawParticleField(particles: List<Particle>, time: Float, fieldSize: Size) {
    val w = fieldSize.width
    val h = fieldSize.height
    if (w <= 0f || h <= 0f) return
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        for (p in particles) {
            val xFrac = (p.xFrac0 + time * p.driftSpeedFrac).mod(1f)
            val x = xFrac * w
            val y = p.baseYFrac * h + kotlin.math.sin(time * p.wobbleSpeed + p.phase) * p.wobbleAmpPx
            when (p.shape) {
                ParticleShape.CIRCLE -> {
                    native.drawCircle(x, y, p.radius * 2.1f, p.outerPaint)
                    native.drawCircle(x, y, p.radius, p.corePaint)
                }
                ParticleShape.RING -> {
                    // Classic "donut" bokeh — an unfocused point light with a
                    // stroked rather than filled paint, so the middle shows
                    // whatever's behind it instead of solid color.
                    native.drawCircle(x, y, p.radius * 1.9f, p.outerPaint)
                    native.drawCircle(x, y, p.radius, p.corePaint)
                }
                ParticleShape.SQUIRCLE -> {
                    // Soft rounded-square blob — mimics a rounded aperture
                    // blade shape rather than a perfect circle.
                    val cornerOuter = p.radius * 2.1f * 0.4f
                    val cornerCore = p.radius * 0.4f
                    native.save()
                    native.rotate(Math.toDegrees(p.rotation.toDouble()).toFloat(), x, y)
                    val outerRect = android.graphics.RectF(x - p.radius * 2.1f, y - p.radius * 2.1f, x + p.radius * 2.1f, y + p.radius * 2.1f)
                    native.drawRoundRect(outerRect, cornerOuter, cornerOuter, p.outerPaint)
                    val coreRect = android.graphics.RectF(x - p.radius, y - p.radius, x + p.radius, y + p.radius)
                    native.drawRoundRect(coreRect, cornerCore, cornerCore, p.corePaint)
                    native.restore()
                }
                ParticleShape.CAPSULE -> {
                    // Elongated pill/streak shape, tilted per-particle — an
                    // organic stand-in for motion-streaked or lens-vignetted
                    // bokeh, so not every light in the field reads as a
                    // point source.
                    native.save()
                    native.rotate(Math.toDegrees(p.rotation.toDouble()).toFloat(), x, y)
                    val lenOuter = p.radius * 2.1f * 1.8f
                    val halfHOuter = p.radius * 2.1f * 0.62f
                    val outerRect = android.graphics.RectF(x - lenOuter, y - halfHOuter, x + lenOuter, y + halfHOuter)
                    native.drawRoundRect(outerRect, halfHOuter, halfHOuter, p.outerPaint)
                    val lenCore = p.radius * 1.8f
                    val halfHCore = p.radius * 0.62f
                    val coreRect = android.graphics.RectF(x - lenCore, y - halfHCore, x + lenCore, y + halfHCore)
                    native.drawRoundRect(coreRect, halfHCore, halfHCore, p.corePaint)
                    native.restore()
                }
            }
        }
    }
}

/** Full-screen layer of slow drifting bokeh dots. When [particles]/[time]
 * aren't supplied, it generates and animates its own (handy for previews or
 * standalone use) — but the app root supplies its own shared field via
 * [LocalParticleField] so [GlassCard] panels can echo it. */
@Composable
fun ParticleBackground(
    modifier: Modifier = Modifier,
    count: Int = 46,
    particles: List<Particle> = remember { generateParticles(count) },
    time: Float = rememberParticleTime()
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawParticleField(particles, time, size)
    }
}
