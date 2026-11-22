package com.acedanger.traceart.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

/**
 * A photo scaled up or down to fill [modifier]'s space while keeping its own shape, with rounded
 * corners and a soft shadow that hug the photo itself (not the empty space around it).
 */
@Composable
fun FittedPhoto(model: Any?, contentDescription: String?, modifier: Modifier, shadow: Boolean = true) {
    val context = LocalContext.current
    // Big enough to look sharp full-screen on the tablet, small enough to keep memory in check.
    val request = remember(model) { ImageRequest.Builder(context).data(model).size(2048).build() }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsStateWithLifecycle()
    val size = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
    val shape = RoundedCornerShape(24.dp)
    Box(modifier, contentAlignment = Alignment.Center) {
        val ratio = size?.let { if (it.width > 0f && it.height > 0f) it.width / it.height else null }
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = if (ratio == null) Modifier else {
                val base = Modifier.aspectRatio(ratio)
                (if (shadow) base.shadow(10.dp, shape) else base).clip(shape)
            },
        )
    }
}
