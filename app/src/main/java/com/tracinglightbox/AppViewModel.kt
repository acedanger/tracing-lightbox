package com.tracinglightbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tracinglightbox.library.Picture
import com.tracinglightbox.library.loadCatalog
import com.tracinglightbox.trace.PictureTransform
import com.tracinglightbox.trace.TransformStore
import com.tracinglightbox.trace.applyGesture
import com.tracinglightbox.trace.toggleMirror
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val KEY_LOCKED = "locked"
private const val KEY_PICTURE = "picture_id"
private const val SAVE_DEBOUNCE_MS = 300L

class AppViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val store = TransformStore(app)

    val pictures: List<Picture> = loadCatalog(app)

    private val _currentPicture = MutableStateFlow<Picture?>(null)
    /** null means the Library screen is showing. */
    val currentPicture: StateFlow<Picture?> = _currentPicture.asStateFlow()

    private val _transform = MutableStateFlow(PictureTransform())
    val transform: StateFlow<PictureTransform> = _transform.asStateFlow()

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
        _currentPicture.value = p
        saved[KEY_PICTURE] = p.id
    }

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
