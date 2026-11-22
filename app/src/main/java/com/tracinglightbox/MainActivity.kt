package com.tracinglightbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.tracinglightbox.ui.LibraryScreen
import com.tracinglightbox.ui.TraceScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            setSingletonImageLoaderFactory { ctx ->
                ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build()
            }
            MaterialTheme(colorScheme = lightColorScheme()) { App(vm) }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val picture by vm.currentPicture.collectAsStateWithLifecycle()
    val transform by vm.transform.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()

    val current = picture
    if (current == null) {
        LibraryScreen(vm.pictures, onOpen = vm::openPicture)
    } else {
        TraceScreen(
            picture = current,
            transform = transform,
            locked = locked,
            onGesture = vm::onGesture,
            onBack = vm::closePicture,
            onFit = vm::fit,
            onMirror = vm::toggleMirror,
            onLock = { vm.setLocked(true) },
        )
    }
}
