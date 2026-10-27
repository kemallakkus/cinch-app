package com.cinch.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import com.cinch.ui.theme.CinchTheme
import kotlin.random.Random

private class Piece(
    val vx: Float, // ekran genişliği oranında yatay hız
    val vy: Float, // ekran yüksekliği oranında ilk dikey hız (eksi = yukarı)
    val width: Float,
    val height: Float,
    val spin: Float,
    val colorIndex: Int,
)

/**
 * Kısa bir konfeti patlaması. [trigger] her arttığında yeniden patlar; 0 iken hiçbir şey çizmez.
 * Dokunuşları engellemez (yalnız çizer), içeriğin üstüne kaplama olarak konur.
 *
 * @param originY patlamanın dikey başlangıcı (0 üst, 1 alt).
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, originY: Float = 0.85f) {
    if (trigger == 0) return
    val colors = CinchTheme.colors.confetti
    val progress = remember(trigger) { Animatable(0f) }
    val pieces = remember(trigger) {
        val random = Random(trigger)
        List(PIECES) {
            Piece(
                vx = random.nextFloat() * 1.1f - 0.55f,
                vy = -(0.9f + random.nextFloat() * 0.8f),
                width = 10f + random.nextFloat() * 10f,
                height = 6f + random.nextFloat() * 6f,
                spin = random.nextFloat() * 2f - 1f,
                colorIndex = random.nextInt(colors.size),
            )
        }
    }
    LaunchedEffect(trigger) {
        progress.animateTo(1f, tween(durationMillis = 1_600, easing = LinearEasing))
    }

    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * originY)
        pieces.forEach { p ->
            // Basit atış hareketi: yukarı fırlar, yerçekimiyle döner ve düşer; sona doğru söner.
            val x = origin.x + p.vx * size.width * t
            val y = origin.y + (p.vy * t + GRAVITY * t * t) * size.height * 0.6f
            rotate(degrees = p.spin * 720f * t, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = colors[p.colorIndex],
                    topLeft = Offset(x - p.width / 2f, y - p.height / 2f),
                    size = Size(p.width, p.height),
                    cornerRadius = CornerRadius(3f, 3f),
                    alpha = (1f - t * t).coerceIn(0f, 1f),
                )
            }
        }
    }
}

private const val PIECES = 48
private const val GRAVITY = 1.6f
