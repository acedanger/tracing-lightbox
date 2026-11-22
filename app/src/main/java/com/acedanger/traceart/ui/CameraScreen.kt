package com.acedanger.traceart.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.acedanger.traceart.photos.paperFrame
import com.acedanger.traceart.photos.cropForFrame
import com.acedanger.traceart.photos.FrameRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import android.graphics.Matrix
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.acedanger.traceart.library.Picture
import com.acedanger.traceart.library.assetUri
import com.acedanger.traceart.photos.PhotoStore
import kotlinx.coroutines.launch

private val Cream = Color(0xFFFFF3E0)
private val Pink = Color(0xFFFF4F8B)

/**
 * Takes a photo of a finished drawing. Paper goes on a table, the tablet is held above it, the
 * drawing is lined up in the frame and the big button takes the picture. The photo is saved to
 * the Trace Art album right away; "Try again" removes it.
 */
@Composable
fun CameraScreen(
    traced: Picture?,
    store: PhotoStore,
    onClose: () -> Unit,
    onKeep: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted && !asked) launcher.launch(Manifest.permission.CAMERA) }

    // Paper is portrait, so the camera screen is too: the viewfinder never turns sideways mid-shot.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        onDispose { if (before != null) activity.requestedOrientation = before }
    }

    // The photo just taken, waiting for Keep it / Try again. Saved so rotation doesn't lose it.
    var taken by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun tryAgain() {
        val uri = taken ?: return
        taken = null
        scope.launch { store.delete(Uri.parse(uri)) }
    }
    BackHandler { if (taken != null) tryAgain() else onClose() }

    when {
        taken != null -> ReviewPhoto(Uri.parse(taken), traced, onKeep = onKeep, onTryAgain = ::tryAgain)
        granted -> Viewfinder(traced, store, onClose = onClose, onTaken = { taken = it.toString() })
        else -> NoPermission(
            asked = asked,
            onAsk = { launcher.launch(Manifest.permission.CAMERA) },
            onSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onClose = onClose,
        )
    }
}

@Composable
private fun Viewfinder(traced: Picture?, store: PhotoStore, onClose: () -> Unit, onTaken: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val sound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    DisposableEffect(Unit) { onDispose { sound.release() } }
    var cameraError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            // The back camera looks at the paper on the table; fall back to the front one.
            val selector = when {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                else -> CameraSelector.DEFAULT_FRONT_CAMERA
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
        }.onFailure { cameraError = true }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val insets = WindowInsets.safeDrawing
        val frame = paperFrame(
            viewW,
            viewH,
            top = insets.getTop(density) + with(density) { 110.dp.toPx() },
            bottom = insets.getBottom(density) + with(density) { 190.dp.toPx() },
            side = with(density) { 32.dp.toPx() },
        )

        fun shoot() {
            if (busy || cameraError) return
            busy = true
            sound.play(MediaActionSound.SHUTTER_CLICK)
            scope.launch {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(450))
            }
            capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        scope.launch {
                            val bmp = withContext(Dispatchers.Default) {
                                try {
                                    runCatching { framedBitmap(image, frame, viewW, viewH) }.getOrNull()
                                } finally {
                                    image.close()
                                }
                            }
                            val uri = bmp?.let { store.saveJpeg(it, traced?.id).also { _ -> it.recycle() } }
                            busy = false
                            if (uri != null) onTaken(uri)
                            else Toast.makeText(context, "Oops, the photo didn't save. Try again!", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        busy = false
                        Toast.makeText(context, "Oops, the photo didn't work. Try again!", Toast.LENGTH_LONG).show()
                    }
                },
            )
        }

        AndroidView({ previewView }, Modifier.fillMaxSize())
        FrameGuide(frame, Modifier.fillMaxSize())

        Text(
            if (cameraError) "The camera isn't working right now." else "Put your drawing inside the frame",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 40.dp, start = 140.dp, end = 24.dp)
                .background(Color(0x99000000), RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
        )
        FilledTonalButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(16.dp).height(56.dp),
        ) { Text("Back", fontSize = 20.sp) }

        ShutterButton(
            busy = busy,
            enabled = !cameraError,
            onClick = ::shoot,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 40.dp),
        )
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
    }
}

/** Turns the captured image upright and keeps only what was inside the on-screen frame. */
private fun framedBitmap(image: ImageProxy, frame: FrameRect, viewW: Float, viewH: Float): Bitmap {
    val raw = image.toBitmap()
    val degrees = image.imageInfo.rotationDegrees
    val upright = if (degrees == 0) raw else {
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }
    val c = cropForFrame(frame, viewW, viewH, upright.width, upright.height)
    return Bitmap.createBitmap(upright, c.x, c.y, c.width, c.height).also { if (it !== upright) upright.recycle() }
}

/** Dims everything outside the paper-shaped frame and draws corner brackets around it. */
@Composable
private fun FrameGuide(frame: FrameRect, modifier: Modifier) {
    Canvas(modifier) {
        val dim = Color(0x8C000000)
        drawRect(dim, Offset.Zero, Size(size.width, frame.top))
        drawRect(dim, Offset(0f, frame.bottom), Size(size.width, size.height - frame.bottom))
        drawRect(dim, Offset(0f, frame.top), Size(frame.left, frame.height))
        drawRect(dim, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))

        val len = minOf(frame.width, frame.height) * 0.14f
        val stroke = 12f
        fun corner(o: Offset, dx: Float, dy: Float) {
            drawLine(Color.White, o, o + Offset(dx * len, 0f), stroke, StrokeCap.Round)
            drawLine(Color.White, o, o + Offset(0f, dy * len), stroke, StrokeCap.Round)
        }
        corner(Offset(frame.left, frame.top), 1f, 1f)
        corner(Offset(frame.right, frame.top), -1f, 1f)
        corner(Offset(frame.left, frame.bottom), 1f, -1f)
        corner(Offset(frame.right, frame.bottom), -1f, -1f)
        drawRect(
            Color.White.copy(alpha = 0.4f),
            Offset(frame.left, frame.top),
            Size(frame.width, frame.height),
            style = Stroke(3f),
        )
    }
}

@Composable
private fun ShutterButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(128.dp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .border(10.dp, Pink, CircleShape)
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .semantics { contentDescription = "Take the photo" },
    ) {
        if (busy) CircularProgressIndicator(color = Pink, modifier = Modifier.size(56.dp))
        else Text("📷", fontSize = 48.sp)
    }
}

/** "Ta-da!" with confetti, the photo, and what she traced. */
@Composable
private fun ReviewPhoto(uri: Uri, traced: Picture?, onKeep: () -> Unit, onTryAgain: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Cream)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        ) {
            Text("Ta-da!", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Pink)
            Text(
                if (traced != null) "Your ${traced.name} looks amazing!" else "Your drawing looks amazing!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val outline: @Composable (Modifier) -> Unit = { m ->
                    Box(m, contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = traced?.assetUri(),
                            contentDescription = "The outline you traced",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.aspectRatio(1f).shadow(6.dp, RoundedCornerShape(20.dp))
                                .background(Color.White, RoundedCornerShape(20.dp)).padding(12.dp),
                        )
                    }
                }
                when {
                    traced == null -> FittedPhoto(uri, "Your drawing", Modifier.fillMaxSize())
                    // Portrait: small outline on top, her drawing big underneath.
                    maxHeight > maxWidth -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        outline(Modifier.weight(0.26f).fillMaxWidth())
                        Text("↓", fontSize = 40.sp, color = Pink)
                        FittedPhoto(uri, "Your drawing", Modifier.weight(0.74f).fillMaxWidth())
                    }
                    else -> Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        outline(Modifier.weight(0.35f).fillMaxHeight())
                        Text("→", fontSize = 48.sp, color = Pink)
                        FittedPhoto(uri, "Your drawing", Modifier.weight(0.65f).fillMaxHeight())
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                OutlinedButton(onClick = onTryAgain, modifier = Modifier.height(80.dp).widthIn(min = 200.dp)) {
                    Text("Try again", fontSize = 26.sp)
                }
                Button(
                    onClick = onKeep,
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                    modifier = Modifier.height(80.dp).widthIn(min = 240.dp),
                ) { Text("Keep it! ♥", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Celebration(key = uri)
    }
}

@Composable
private fun NoPermission(asked: Boolean, onAsk: () -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().background(Cream).safeDrawingPadding().padding(32.dp),
    ) {
        Text("📷", fontSize = 72.sp)
        Text(
            "Trace Art needs the camera to take photos of your drawings.",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (asked) {
            Text(
                "Ask a grown-up to tap Allow. If it doesn't ask, open Settings > Permissions > Camera.",
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 64.dp)) { Text("Back", fontSize = 22.sp) }
            Button(onClick = onAsk, modifier = Modifier.heightIn(min = 64.dp)) { Text("Allow camera", fontSize = 22.sp) }
            if (asked) {
                OutlinedButton(onClick = onSettings, modifier = Modifier.heightIn(min = 64.dp)) { Text("Settings", fontSize = 22.sp) }
            }
        }
    }
}
