package com.tracinglightbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracinglightbox.library.Picture
import com.tracinglightbox.trace.PictureTransform

@Composable
fun TraceScreen(
    picture: Picture,
    transform: PictureTransform,
    locked: Boolean,
    onGesture: (panX: Float, panY: Float, zoom: Float, rot: Float, w: Float, h: Float) -> Unit,
    onBack: () -> Unit,
    onFit: () -> Unit,
    onMirror: () -> Unit,
    onLock: () -> Unit,
    showKioskNote: Boolean = false,
    onDismissKioskNote: () -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
    // While locked, Back does nothing; otherwise it returns to the library.
    BackHandler(enabled = true) { if (!locked) onBack() }

    Box(Modifier.fillMaxSize()) {
        PictureView(
            picture = picture,
            transform = transform,
            modifier = Modifier.pointerInput(locked) {
                if (locked) return@pointerInput
                detectTransformGestures { _, pan, zoom, rotation ->
                    onGesture(pan.x, pan.y, zoom, rotation, size.width.toFloat(), size.height.toFloat())
                }
            },
        )

        if (!locked) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(16.dp),
            ) {
                FilledTonalButton(onClick = onBack, modifier = Modifier.height(56.dp)) { Text("Back", fontSize = 20.sp) }
                FilledTonalButton(onClick = onFit, modifier = Modifier.height(56.dp)) { Text("Fit", fontSize = 20.sp) }
                FilledTonalButton(onClick = onMirror, modifier = Modifier.height(56.dp)) { Text("Mirror", fontSize = 20.sp) }
            }
            Button(
                onClick = onLock,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(24.dp)
                    .height(80.dp)
                    .widthIn(min = 200.dp),
            ) { Text("Lock", fontSize = 32.sp) }

            if (showKioskNote) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(start = 16.dp, top = 88.dp, end = 16.dp)
                        .widthIn(max = 420.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Not set up as a kiosk yet — see the README to stop exits from pinning.",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismissKioskNote) { Text("OK") }
                    }
                }
            }
        }

        overlay()
    }
}
