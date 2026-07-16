package id.myapp.progresshubkt

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.max
import kotlin.random.Random

/** Real frosted-glass panel. The particle field behind every screen is
 * fully known (shared via [LocalParticleField]), so instead of faking a
 * blur with a translucent gradient, this card redraws that exact same
 * field — translated so it lines up with the card's own position on
 * screen — into its own local Canvas, then blurs *only that layer* with a
 * genuine RenderEffect blur (Modifier.blur, Android 12+). A soft tint and
 * a top-lit border sit on top of the blur, and the actual content (text,
 * inputs) is a separate, unblurred layer on top of everything — so the
 * glass looks properly frosted without ever blurring what you're reading. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorder,
    radius: androidx.compose.ui.unit.Dp = 20.dp,
    hero: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    val field = LocalParticleField.current
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                // The blur canvas below redraws + re-blurs the shared
                // particle field translated to this exact position, which
                // is expensive (a real RenderEffect blur pass). During a
                // drag (e.g. SwipeableWeekPanel), position changes on
                // every single layout frame, which would otherwise force
                // that redraw every frame for every visible card at once.
                // Since the blur radius here is 28-34dp, a few px of
                // position error in what it's blurring is completely
                // invisible — so only actually update (and thus only
                // re-blur) once the card has moved more than a small
                // threshold, cutting redraw frequency substantially while
                // dragging with no visible difference at rest.
                val p = coords.positionInRoot()
                if (kotlin.math.abs(p.x - positionInRoot.x) > 6f || kotlin.math.abs(p.y - positionInRoot.y) > 6f) {
                    positionInRoot = p
                }
            }
            .shadow(
                elevation = if (hero) 20.dp else 12.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = Color.Black.copy(alpha = 0.28f)
            )
            .clip(shape)
    ) {
        // Layer 1 — the actual frosted-glass illusion: the same particles
        // drifting behind this card, redrawn at this card's own offset and
        // blurred. Falls back to nothing (just the tint below) if the
        // shared field hasn't been measured yet.
        if (field != null && field.fieldSize.width > 0f) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    // Deeper than the original 16/20dp pass: real frosted
                    // glass diffracts light so thoroughly that shapes behind
                    // it barely register as more than soft color blooms.
                    // Pushed to 28/34dp so the bokeh reads as diffused light
                    // rather than recognizably-blurred dots — that's what
                    // actually sells "glass" over "translucent panel with a
                    // filter on it".
                    .blur(if (hero) 34.dp else 28.dp)
            ) {
                translate(left = -positionInRoot.x, top = -positionInRoot.y) {
                    drawParticleField(field.particles, field.time, field.fieldSize)
                }
            }
        }

        // Layer 2 — a much lighter top-to-bottom tint than before (matches
        // Dart's 0.055 / 0.02 / 0.56 stops) so bokeh stays visible through
        // the glass instead of being smothered by an opaque dark panel.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = 0.055f),
                            0.22f to Color.White.copy(alpha = 0.02f),
                            1f to BgDark.copy(alpha = if (hero) 0.66f else 0.60f)
                        )
                    )
                )
                // Per-side border, not a single vertical-gradient brush: the
                // top edge is noticeably brighter ("edge-lit", as if
                // catching light from above) while the other three sides
                // stay dim — mirrors Dart's `Border(top:..., left:...,
                // right:..., bottom:...)` rather than one brush shared by
                // all four sides.
                .border(
                    width = 1.1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            borderColor.copy(alpha = if (hero) 0.32f else 0.24f),
                            borderColor.copy(alpha = 0.10f)
                        )
                    ),
                    shape = shape
                )
                .padding(16.dp),
            content = content
        )

        // Layer 3 — a physical-material texture sitting above everything
        // else: fine grain (like sandblasted/acid-etched glass, which is
        // never perfectly smooth) plus a soft diagonal specular streak
        // (light catching the pane unevenly, rather than the uniform flat
        // tint reading as "a gray filter" instead of "glass"). Both are
        // computed once per card size (not per animation frame — neither
        // reads the particle clock), and kept at low enough alpha that
        // it's felt more than seen, so it doesn't interfere with reading
        // whatever's inside the card.
        val noiseSeed = remember { Random.nextInt() }
        Canvas(modifier = Modifier.matchParentSize()) {
            val rnd = Random(noiseSeed)
            val dotCount = ((size.width * size.height) / 850f).toInt().coerceIn(70, 260)
            repeat(dotCount) {
                val x = rnd.nextFloat() * size.width
                val y = rnd.nextFloat() * size.height
                val a = 0.012f + rnd.nextFloat() * 0.028f
                drawCircle(Color.White.copy(alpha = a), radius = 0.9f, center = Offset(x, y))
            }
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = if (hero) 0.05f else 0.035f),
                        1f to Color.White.copy(alpha = 0f)
                    ),
                    start = Offset(-size.width * 0.2f, -size.height * 0.3f),
                    end = Offset(size.width * 0.75f, size.height * 0.6f)
                )
            )
        }
    }
}

@Composable
fun PhaseBadge(name: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(name, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Header shown at the top of every tab: accent badge + weight range title,
 * phase badge, a settings shortcut, and an icon-led stat strip (week /
 * streak / current weight) separated by thin dividers — mirrors AppHeader
 * from the Flutter app. */
@Composable
fun AppHeader(
    phaseName: String,
    phaseColor: Color,
    week: Int,
    totalWeeks: Int,
    streak: Int,
    currentWeight: Double,
    startWeight: Double,
    goalWeight: Double,
    onSettingsClick: () -> Unit = {}
) {
    // The activity hides the system status bar entirely, but a physical
    // notch/camera cutout is a cutout in the display itself — hiding the
    // status bar doesn't move it. On a device with a real cutout, this
    // resolves to that cutout's actual height; on a device without one it
    // resolves to 0. It's combined with a small fixed minimum via max()
    // rather than addition, so the two don't stack: a real cutout already
    // provides more clearance than the header needs on its own, while a
    // phone with no cutout still gets a small, deliberate gap instead of
    // sitting flush against the very top edge. Stacking (cutout + a full
    // 16dp padding on every side) is what was making the header sit
    // noticeably lower than the notch actually required.
    val density = LocalDensity.current
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues(density).calculateTopPadding()
    val topClearance = max(cutoutTop, 10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x4D0C0F14), Color(0x2E0C0F14))
                )
            )
            .padding(horizontal = 16.dp)
            .padding(top = topClearance, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(AccentTeal, AccentTeal.copy(alpha = 0.55f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DirectionsBike, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("PROGRAM PENURUNAN BERAT", color = TextDim, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${startWeight.toInt()} → ${goalWeight.toInt()} KG",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(8.dp))
                    PhaseBadge(phaseName, phaseColor)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Pengaturan", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            HeaderStatItem(Icons.Filled.CalendarMonth, "$week/$totalWeeks", "MINGGU")
            HeaderStatDivider()
            HeaderStatItem(Icons.Filled.LocalFireDepartment, "$streak hari", "STREAK")
            HeaderStatDivider()
            HeaderStatItem(Icons.Filled.MonitorWeight, "%.1f kg".format(currentWeight), "BERAT")
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

@Composable
fun HeaderStatItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextDim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = TextDim, fontSize = 9.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
fun HeaderStatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

/** Circular progress ring with animated sweep + centered text, ported from
 * progress_ring.dart's CustomPainter. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    centerText: String,
    centerLabel: String,
    size: androidx.compose.ui.unit.Dp = 132.dp
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        animated.animateTo(fraction.coerceIn(0f, 1f), animationSpec = tween(900))
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val diameter = kotlin.math.min(this.size.width, this.size.height) - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(centerLabel, color = TextDim, fontSize = 9.5.sp, letterSpacing = 0.5.sp)
        }
    }
}
