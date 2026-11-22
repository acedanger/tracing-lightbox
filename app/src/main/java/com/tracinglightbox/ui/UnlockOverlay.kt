package com.tracinglightbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracinglightbox.lock.Box as PxBox
import com.tracinglightbox.lock.UnlockState

private val RingSize = 96.dp
private val RingStroke = 10.dp

/**
 * Purely visual: the hold-progress ring in the top-left corner and the Unlock button.
 * Taps are handled in MainActivity.dispatchTouchEvent, never here. Draws nothing when Idle.
 */
@Composable
fun UnlockOverlay(state: UnlockState, confirmButton: PxBox) {
    when (state) {
        is UnlockState.Holding -> if (state.showRing) {
            Canvas(Modifier.size(RingSize)) {
                val stroke = RingStroke.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(Color(0xFFDDDDDD), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(Color(0xFF424242), -90f, 360f * state.progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            }
        }
        is UnlockState.Confirming -> {
            val density = LocalDensity.current
            val seconds = ((state.remainingMs + 999) / 1000).toInt()
            Box(Modifier.fillMaxSize()) {
                with(density) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(confirmButton.left.toDp(), confirmButton.top.toDp())
                            .size(
                                (confirmButton.right - confirmButton.left).toDp(),
                                (confirmButton.bottom - confirmButton.top).toDp(),
                            )
                            .background(Color(0xFF6750A4), RoundedCornerShape(32.dp)),
                    ) {
                        Text("Unlock ($seconds)", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        else -> Unit
    }
}
