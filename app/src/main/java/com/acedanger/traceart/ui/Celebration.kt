package com.acedanger.traceart.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val ConfettiColors = listOf(
    Color(0xFFFF4F8B), Color(0xFFFFB300), Color(0xFF7C4DFF), Color(0xFF00BFA5), Color(0xFFFF7043), Color(0xFF42A5F5),
)
private const val CONFETTI_MS = 4_500L

private class Piece(
    val startX: Float, val vx: Float, val vy: Float, val spin: Float, val size: Float,
    val color: Color, val shape: Int, val delayMs: Long,
)

/** A burst of hearts, stars and dots that fall across the screen once, then disappear. */
@Composable
fun Celebration(key: Any, modifier: Modifier = Modifier.fillMaxSize(), count: Int = 90) {
    val pieces = remember(key) {
        List(count) {
            Piece(
                startX = Random.nextFloat(),
                vx = (Random.nextFloat() - 0.5f) * 0.25f,
                vy = 0.2f + Random.nextFloat() * 0.3f,
                spin = (Random.nextFloat() - 0.5f) * 540f,
                size = 18f + Random.nextFloat() * 26f,
                color = ConfettiColors.random(),
                shape = it % 3,
                delayMs = Random.nextLong(0, 1_200),
            )
        }
    }
    var elapsedMs by remember(key) { mutableLongStateOf(0L) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (elapsedMs < CONFETTI_MS) {
            elapsedMs = (withFrameNanos { it } - start) / 1_000_000
        }
    }
    if (elapsedMs >= CONFETTI_MS) return
    Canvas(modifier) {
        for (p in pieces) {
            val t = (elapsedMs - p.delayMs) / 1000f
            if (t < 0) continue
            val x = (p.startX + p.vx * t) * size.width
            val y = -60f + (p.vy * t + 0.35f * t * t) * size.height
            if (y > size.height + 60f) continue
            val alpha = (1f - (elapsedMs - (CONFETTI_MS - 600)).coerceAtLeast(0) / 600f).coerceIn(0f, 1f)
            translate(x, y) {
                rotate(p.spin * t, pivot = Offset.Zero) { drawPiece(p, alpha) }
            }
        }
    }
}

private fun DrawScope.drawPiece(p: Piece, alpha: Float) {
    val c = p.color.copy(alpha = alpha)
    when (p.shape) {
        0 -> drawPath(heartPath(p.size), c)
        1 -> drawPath(starPath(p.size), c)
        else -> drawCircle(c, radius = p.size / 3f, center = Offset.Zero)
    }
}

private fun heartPath(s: Float) = Path().apply {
    val r = s / 2
    moveTo(0f, 0.9f * r)
    cubicTo(-0.15f * r, 0.75f * r, -r, 0.3f * r, -r, -0.25f * r)
    cubicTo(-r, -0.7f * r, -0.45f * r, -0.95f * r, 0f, -0.5f * r)
    cubicTo(0.45f * r, -0.95f * r, r, -0.7f * r, r, -0.25f * r)
    cubicTo(r, 0.3f * r, 0.15f * r, 0.75f * r, 0f, 0.9f * r)
    close()
}

private fun starPath(s: Float) = Path().apply {
    val outer = s / 2
    val inner = outer * 0.45f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val x = (r * cos(a)).toFloat()
        val y = (r * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
