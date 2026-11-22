package com.tracinglightbox.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tracinglightbox.library.Picture
import com.tracinglightbox.trace.Decoration
import com.tracinglightbox.trace.DecorationKind
import com.tracinglightbox.trace.MAX_DECORATIONS
import com.tracinglightbox.trace.PictureTransform
import com.tracinglightbox.lock.Box as PxBox

/** Width below which the decoration buttons sit above the Lock button instead of beside it. */
private val WideLayout = 720.dp
private val UnlockCorner = 120.dp

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
    decorations: List<Decoration> = emptyList(),
    aspect: Float = 1f,
    decorationsReady: Boolean = false,
    onAddDecoration: (DecorationKind, w: Float, h: Float, forbidden: List<PxBox>) -> Boolean = { _, _, _, _ -> false },
    onClearDecorations: () -> Unit = {},
    showKioskNote: Boolean = false,
    onDismissKioskNote: () -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
    // While locked, Back does nothing; otherwise it returns to the library.
    BackHandler(enabled = true) { if (!locked) onBack() }

    val context = LocalContext.current
    val density = LocalDensity.current
    // Screen areas covered by buttons, so decorations are never placed underneath them.
    val buttonAreas = remember { mutableStateMapOf<String, PxBox>() }
    fun Modifier.trackArea(key: String) = onGloballyPositioned {
        val b = it.boundsInRoot()
        buttonAreas[key] = PxBox(b.left, b.top, b.right, b.bottom)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val wide = maxWidth >= WideLayout

        PictureView(
            picture = picture,
            transform = transform,
            decorations = decorations,
            aspect = aspect,
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
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .trackArea("top"),
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
                    .widthIn(min = 200.dp)
                    .trackArea("lock"),
            ) { Text("Lock", fontSize = 32.sp) }

            // Decoration buttons: each tap adds one, up to MAX_DECORATIONS.
            val full = decorations.size >= MAX_DECORATIONS
            // FlowRow wraps the counter and Clear onto a second line on narrow screens.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(start = 24.dp, end = if (wide) 260.dp else 24.dp, bottom = if (wide) 24.dp else 120.dp)
                    .trackArea("decorations"),
            ) {
                DecorationKind.entries.forEach { kind ->
                    FilledTonalButton(
                        onClick = {
                            val forbidden = buttonAreas.values.toList() +
                                with(density) { PxBox(0f, 0f, UnlockCorner.toPx(), UnlockCorner.toPx()) }
                            if (!onAddDecoration(kind, wPx, hPx, forbidden)) {
                                Toast.makeText(context, "No room for another one. Try zooming the picture out.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = decorationsReady && !full,
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.size(72.dp),
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/decorations/${kind.file}",
                            contentDescription = "Add ${kind.name.lowercase()}",
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                Text("${decorations.size}/$MAX_DECORATIONS", fontSize = 20.sp, maxLines = 1, softWrap = false)
                if (decorations.isNotEmpty()) {
                    TextButton(onClick = onClearDecorations) { Text("Clear", fontSize = 20.sp) }
                }
            }

            if (showKioskNote) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(start = 16.dp, top = 88.dp, end = 16.dp)
                        .widthIn(max = 420.dp)
                        .trackArea("note"),
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
