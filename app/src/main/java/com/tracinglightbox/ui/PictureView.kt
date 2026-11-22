package com.tracinglightbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.tracinglightbox.library.Picture
import com.tracinglightbox.library.assetUri
import com.tracinglightbox.trace.Decoration
import com.tracinglightbox.trace.PictureTransform
import com.tracinglightbox.trace.fitFrame
import com.tracinglightbox.trace.offsetPx
import kotlin.math.roundToInt

fun Decoration.assetUri(): String = "file:///android_asset/decorations/${kind.file}"

/**
 * The picture on pure white, plus its decorations, all positioned by [transform] as one piece.
 * Rasterized at 2x so zooming stays sharp.
 */
@Composable
fun PictureView(
    picture: Picture,
    transform: PictureTransform,
    modifier: Modifier = Modifier,
    decorations: List<Decoration> = emptyList(),
    aspect: Float = 1f,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val context = LocalContext.current
        val request = remember(picture.id, wPx, hPx) {
            ImageRequest.Builder(context)
                .data(picture.assetUri())
                .size(Size((wPx * 2).toInt().coerceAtLeast(1), (hPx * 2).toInt().coerceAtLeast(1)))
                .build()
        }
        val (dx, dy) = transform.offsetPx(wPx, hPx)
        val frame = fitFrame(wPx, hPx, aspect)
        val decoPx = frame.decorationSizePx
        val decoDp = with(density) { decoPx.toDp() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dx
                    translationY = dy
                    scaleX = if (transform.mirrored) -transform.scale else transform.scale
                    scaleY = transform.scale
                    rotationZ = transform.rotationDeg
                },
        ) {
            AsyncImage(
                model = request,
                contentDescription = picture.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            decorations.forEach { d ->
                AsyncImage(
                    model = d.assetUri(),
                    contentDescription = null,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (wPx / 2 + d.x * frame.imageW - decoPx / 2).roundToInt(),
                                (hPx / 2 + d.y * frame.imageH - decoPx / 2).roundToInt(),
                            )
                        }
                        .size(decoDp),
                )
            }
        }
    }
}
