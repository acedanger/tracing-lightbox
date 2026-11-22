package com.tracinglightbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.svg.SvgDecoder
import coil3.toBitmap
import com.tracinglightbox.library.Picture
import com.tracinglightbox.library.assetUri
import com.tracinglightbox.library.loadCatalog
import com.tracinglightbox.lock.Box
import com.tracinglightbox.trace.Circle
import com.tracinglightbox.trace.Decoration
import com.tracinglightbox.trace.DecorationKind
import com.tracinglightbox.trace.MAX_DECORATIONS
import com.tracinglightbox.trace.PictureTransform
import com.tracinglightbox.trace.TransformStore
import com.tracinglightbox.trace.applyGesture
import com.tracinglightbox.trace.contentBounds
import com.tracinglightbox.trace.contentOnScreen
import com.tracinglightbox.trace.fitFrame
import com.tracinglightbox.trace.fromScreen
import com.tracinglightbox.trace.placeDecoration
import com.tracinglightbox.trace.toScreen
import com.tracinglightbox.trace.toggleMirror
import com.tracinglightbox.trace.withDecoration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val KEY_LOCKED = "locked"
private const val KEY_PICTURE = "picture_id"
private const val SAVE_DEBOUNCE_MS = 300L
private const val INFO_RASTER_PX = 256

/** What the decoration placer needs to know about a picture: its shape and where its lines are. */
data class PictureInfo(val aspect: Float, val content: Box)

class AppViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val store = TransformStore(app)
    private val imageLoader = ImageLoader.Builder(app).components { add(SvgDecoder.Factory()) }.build()

    val pictures: List<Picture> = loadCatalog(app)

    private val _currentPicture = MutableStateFlow<Picture?>(null)
    /** null means the Library screen is showing. */
    val currentPicture: StateFlow<Picture?> = _currentPicture.asStateFlow()

    private val _transform = MutableStateFlow(PictureTransform())
    val transform: StateFlow<PictureTransform> = _transform.asStateFlow()

    private val _decorations = MutableStateFlow<List<Decoration>>(emptyList())
    val decorations: StateFlow<List<Decoration>> = _decorations.asStateFlow()

    private val _pictureInfo = MutableStateFlow<PictureInfo?>(null)
    val pictureInfo: StateFlow<PictureInfo?> = _pictureInfo.asStateFlow()

    /** Survives configuration changes and process death, so a recreated Activity stays locked. */
    val locked: StateFlow<Boolean> = saved.getStateFlow(KEY_LOCKED, false)

    private val _showKioskNote = MutableStateFlow(false)
    val showKioskNote: StateFlow<Boolean> = _showKioskNote.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val id = saved.get<String>(KEY_PICTURE) ?: store.lastPictureId()
            pictures.firstOrNull { it.id == id }?.let { show(it) }
            _showKioskNote.value = !store.kioskNoteDismissed()
        }
    }

    fun openPicture(p: Picture) {
        viewModelScope.launch {
            show(p)
            store.setLastPictureId(p.id)
        }
    }

    private suspend fun show(p: Picture) {
        _transform.value = store.load(p.id) ?: PictureTransform()
        _decorations.value = store.loadDecorations(p.id)
        _pictureInfo.value = null
        _currentPicture.value = p
        saved[KEY_PICTURE] = p.id
        viewModelScope.launch {
            val info = loadInfo(p)
            if (_currentPicture.value?.id == p.id) _pictureInfo.value = info
        }
    }

    /** Rasterizes the picture small and finds where its lines are, for decoration placement. */
    private suspend fun loadInfo(p: Picture): PictureInfo? = runCatching {
        val request = ImageRequest.Builder(getApplication<Application>())
            .data(p.assetUri())
            .size(INFO_RASTER_PX)
            .allowHardware(false)
            .build()
        val bitmap = (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val content = contentBounds(pixels, bitmap.width, bitmap.height) ?: return null
        PictureInfo(bitmap.width.toFloat() / bitmap.height, content)
    }.getOrNull()

    fun closePicture() {
        if (locked.value) return
        _currentPicture.value = null
        saved[KEY_PICTURE] = null
    }

    fun onGesture(panX: Float, panY: Float, zoom: Float, rot: Float, w: Float, h: Float) {
        if (locked.value) return
        update(_transform.value.applyGesture(panX, panY, zoom, rot, w, h))
    }

    fun fit() = update(PictureTransform(mirrored = _transform.value.mirrored))

    fun toggleMirror() = update(_transform.value.toggleMirror())

    /**
     * Adds one decoration at a random free spot around the picture on a [screenW] x [screenH]
     * screen, keeping clear of the picture's lines, other decorations and the [forbidden] areas.
     * Returns false if it can't (locked, full, picture not measured yet, or no room).
     */
    fun addDecoration(kind: DecorationKind, screenW: Float, screenH: Float, forbidden: List<Box>): Boolean {
        if (locked.value) return false
        val id = _currentPicture.value?.id ?: return false
        val info = _pictureInfo.value ?: return false
        val current = _decorations.value
        if (current.size >= MAX_DECORATIONS) return false

        val frame = fitFrame(screenW, screenH, info.aspect)
        val t = _transform.value
        val radius = frame.decorationSizePx * t.scale / 2
        val occupied = current.map { d ->
            val (x, y) = t.toScreen(frame, d.x, d.y)
            Circle(x, y, radius)
        }
        val spot = placeDecoration(
            screen = Box(0f, 0f, screenW, screenH),
            content = t.contentOnScreen(frame, info.content),
            occupied = occupied,
            forbidden = forbidden,
            radius = radius,
            random = Random.Default,
        ) ?: return false
        val (ux, uy) = t.fromScreen(frame, spot.first, spot.second)
        val next = current.withDecoration(Decoration(kind, ux, uy))
        _decorations.value = next
        viewModelScope.launch { store.saveDecorations(id, next) }
        return true
    }

    fun clearDecorations() {
        if (locked.value) return
        val id = _currentPicture.value?.id ?: return
        _decorations.value = emptyList()
        viewModelScope.launch { store.saveDecorations(id, emptyList()) }
    }

    fun setLocked(value: Boolean) {
        saved[KEY_LOCKED] = value
    }

    fun dismissKioskNote() {
        _showKioskNote.value = false
        viewModelScope.launch { store.dismissKioskNote() }
    }

    private fun update(t: PictureTransform) {
        if (locked.value) return
        _transform.value = t
        val id = _currentPicture.value?.id ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            store.save(id, t)
        }
    }
}
