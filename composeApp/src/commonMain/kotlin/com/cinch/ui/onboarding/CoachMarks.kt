package com.cinch.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.cinch.shared.prefs.KeyValueStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** Ekrandaki bir öğeyi tanıtan balon. [key] hem hedefin adı hem "görüldü" kaydının anahtarı. */
data class CoachMark(val key: String, val title: String, val text: String)

/**
 * Hangi balonların görüldüğü. Her balon kendi başına kaydedilir: hedefi henüz ekranda olmayan balon (ör. kart
 * eklenmeden "Ekstre oku") atlanmaz, hedef ilk göründüğünde gösterilir.
 */
class CoachMarkProgress(private val prefs: KeyValueStore) {

    private val _resets = MutableStateFlow(0)

    /** Ayarlar'dan "yeniden göster" denince artar; açık ekranlar balonları yeniden okur. */
    val resets: StateFlow<Int> = _resets.asStateFlow()

    fun isSeen(key: String): Boolean = prefs.getBoolean(prefKey(key))

    fun markSeen(key: String) {
        prefs.putBoolean(prefKey(key), true)
    }

    fun resetAll() {
        CoachMarkSteps.ALL.forEach { prefs.putBoolean(prefKey(it.key), false) }
        _resets.value++
    }

    private fun prefKey(key: String) = "coach_mark_$key"
}

/** Sıradaki balon: sırayla, görülmemiş ve hedefi şu an ekranda olan ilk adım. */
internal fun nextCoachMark(steps: List<CoachMark>, seen: Set<String>, present: Set<String>): CoachMark? =
    steps.firstOrNull { it.key !in seen && it.key in present }

/** Ekrandaki hedeflerin pencere içindeki konumları. */
class CoachMarkTargets {
    internal val rects = mutableStateMapOf<String, Rect>()
}

val LocalCoachMarkTargets = staticCompositionLocalOf<CoachMarkTargets?> { null }

/** Bu öğeyi [key] adıyla balon hedefi yapar. [CoachMarks] dışında (ör. önizleme) hiçbir şey yapmaz. */
@Composable
fun Modifier.coachMarkTarget(key: String): Modifier {
    val targets = LocalCoachMarkTargets.current ?: return this
    DisposableEffect(targets, key) {
        onDispose { targets.rects.remove(key) }
    }
    return onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInWindow()
        if (rect.width > 0f && rect.height > 0f) {
            if (targets.rects[key] != rect) targets.rects[key] = rect
        } else {
            targets.rects.remove(key)
        }
    }
}

/** Ekran açıldıktan sonra hedeflerin yerleşmesi için beklenen süre; yoksa balon yükleme sırasında zıplar. */
private const val SETTLE_MS = 700L

/**
 * Ekranı sarar; [steps] içindeki görülmemiş balonları sırayla gösterir. Balon, hedefini karartılmış ekranda
 * aydınlatır; "İleri"/"Anladım" ya da boşluğa dokunmak sonrakine geçer, "Geç" bu ekranın hepsini görüldü sayar.
 */
@Composable
fun CoachMarks(steps: List<CoachMark>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = koinInject<CoachMarkProgress>()
    val resets by progress.resets.collectAsState()
    val targets = remember { CoachMarkTargets() }
    var seen by remember(resets) { mutableStateOf(steps.filter { progress.isSeen(it.key) }.map { it.key }.toSet()) }
    var shownCount by remember(resets) { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        delay(SETTLE_MS)
        settled = true
    }

    CompositionLocalProvider(LocalCoachMarkTargets provides targets) {
        Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            content()

            val present = targets.rects.keys.toSet()
            val current = if (settled && seen.size < steps.size) nextCoachMark(steps, seen, present) else null
            val rect = current?.let { targets.rects[it.key] }
            if (current != null && rect != null) {
                val remaining = steps.count { it.key !in seen && it.key in present }
                CoachMarkOverlay(
                    mark = current,
                    target = rect.translate(-origin),
                    position = shownCount + 1,
                    total = shownCount + remaining,
                    onNext = {
                        progress.markSeen(current.key)
                        seen = seen + current.key
                        shownCount++
                    },
                    onSkip = {
                        steps.forEach { progress.markSeen(it.key) }
                        seen = seen + steps.map { it.key }
                    },
                )
            }
        }
    }
}

@Composable
private fun CoachMarkOverlay(
    mark: CoachMark,
    target: Rect,
    position: Int,
    total: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val hole by animateRectAsState(
        Rect(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad),
        label = "hole",
    )
    val ring by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Restart),
        label = "ring",
    )
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(250)) }
    val next by rememberUpdatedState(onNext)
    val ringColor = MaterialTheme.colorScheme.primary
    val isLast = position >= total

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            // Alttaki ekran dokunuş almaz; boşluğa dokunmak sonraki balona geçer.
            .pointerInput(mark.key) { detectTapGestures { next() } },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val radius = CornerRadius(20.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.62f))
            drawRoundRect(Color.Black, topLeft = hole.topLeft, size = hole.size, cornerRadius = radius, blendMode = BlendMode.Clear)
            val grow = 10.dp.toPx() * ring
            drawRoundRect(
                color = ringColor.copy(alpha = 1f - ring),
                topLeft = hole.topLeft - Offset(grow, grow),
                size = Size(hole.width + 2 * grow, hole.height + 2 * grow),
                cornerRadius = CornerRadius(radius.x + grow),
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        // Balon hedefin altına sığıyorsa altına, sığmıyorsa üstüne yerleşir.
        Layout(
            content = {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp)) {
                        Text(mark.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            mark.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (total > 1) {
                                Text(
                                    "$position/$total",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (!isLast) TextButton(onClick = onSkip) { Text("Geç") }
                            Button(onClick = onNext) { Text(if (isLast) "Anladım" else "İleri") }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = 16.dp.roundToPx()
            val gap = 14.dp.roundToPx()
            val width = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)
            val balloon = measurables.single().measure(Constraints(minWidth = width, maxWidth = width))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val below = hole.bottom.roundToInt() + gap
                val above = hole.top.roundToInt() - gap - balloon.height
                val y = when {
                    below + balloon.height <= constraints.maxHeight - margin -> below
                    above >= margin -> above
                    else -> (constraints.maxHeight - balloon.height) / 2
                }
                balloon.place(margin, y)
            }
        }
    }
}
