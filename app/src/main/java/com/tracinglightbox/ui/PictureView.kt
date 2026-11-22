package com.tracinglightbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.tracinglightbox.library.Picture
import com.tracinglightbox.library.assetUri
import com.tracinglightbox.trace.PictureTransform
import com.tracinglightbox.trace.offsetPx

/** The picture on pure white, positioned by [transform]. Rasterized at 2x so zooming stays sharp. */
@Composable
fun PictureView(picture: Picture, transform: PictureTransform, modifier: Modifier = Modifier) {
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
        AsyncImage(
            model = request,
            contentDescription = picture.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dx
                    translationY = dy
                    scaleX = if (transform.mirrored) -transform.scale else transform.scale
                    scaleY = transform.scale
                    rotationZ = transform.rotationDeg
                },
        )
    }
}
