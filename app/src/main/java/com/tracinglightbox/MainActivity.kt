package com.tracinglightbox

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.tracinglightbox.lock.Box
import com.tracinglightbox.lock.LockController
import com.tracinglightbox.lock.PointerAction
import com.tracinglightbox.lock.PointerSample
import com.tracinglightbox.lock.UnlockConfig
import com.tracinglightbox.lock.UnlockGestureDetector
import com.tracinglightbox.lock.UnlockState
import com.tracinglightbox.ui.LibraryScreen
import com.tracinglightbox.ui.TraceScreen
import com.tracinglightbox.ui.UnlockOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val ZONE_DP = 72f
private const val PALM_MM = 20f
private const val CONFIRM_W_DP = 320f
private const val CONFIRM_H_DP = 120f
private const val TICK_MS = 50L
private const val TAP_SLOP_DP = 24f

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private lateinit var lock: LockController

    private var detector: UnlockGestureDetector? = null
    private var detectorConfig: UnlockConfig? = null
    private val unlockState = MutableStateFlow<UnlockState>(UnlockState.Idle)
    private val confirmBox = MutableStateFlow(Box(0f, 0f, 0f, 0f))

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val d = detector ?: return
            d.onTick(SystemClock.uptimeMillis())
            publish(d)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // picture area is the whole window in both modes, so locking never resizes it
        super.onCreate(savedInstanceState)
        lock = LockController(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.locked.collect { locked ->
                    if (locked) lock.applyLocked() else lock.applyUnlocked()
                    detector?.reset()
                    unlockState.value = UnlockState.Idle
                }
            }
        }

        setContent {
            setSingletonImageLoaderFactory { ctx ->
                ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build()
            }
            MaterialTheme(colorScheme = lightColorScheme()) { App(vm, unlockState, confirmBox, lock.isKiosk) }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    /**
     * While locked, no touch ever reaches the UI: every event goes only to the unlock detector
     * and is consumed here.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!vm.locked.value) return super.dispatchTouchEvent(ev)
        val d = detectorFor(window.decorView.width.toFloat(), window.decorView.height.toFloat())
        for (sample in samplesOf(ev)) d.onPointer(sample)
        publish(d)
        return true
    }

    private fun publish(d: UnlockGestureDetector) {
        val state = d.state
        unlockState.value = state
        handler.removeCallbacks(tick)
        when (state) {
            is UnlockState.Holding, is UnlockState.Confirming -> handler.postDelayed(tick, TICK_MS)
            UnlockState.Unlocked -> {
                d.reset()
                unlockState.value = UnlockState.Idle
                vm.setLocked(false)
            }
            UnlockState.Idle -> Unit
        }
    }

    private fun detectorFor(w: Float, h: Float): UnlockGestureDetector {
        val dm = resources.displayMetrics
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm)
        val bw = dp(CONFIRM_W_DP)
        val bh = dp(CONFIRM_H_DP)
        val button = Box((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2)
        val config = UnlockConfig(
            zoneSizePx = dp(ZONE_DP),
            maxTouchMajorPx = PALM_MM / 25.4f * dm.xdpi,
            confirmButton = button,
            tapSlopPx = dp(TAP_SLOP_DP),
        )
        if (config != detectorConfig || detector == null) {
            detectorConfig = config
            detector = UnlockGestureDetector(config)
            confirmBox.value = button
        }
        return detector!!
    }

    private fun samplesOf(ev: MotionEvent): List<PointerSample> {
        fun sample(action: PointerAction, i: Int) = PointerSample(
            action, ev.getPointerId(i), ev.getX(i), ev.getY(i), ev.getTouchMajor(i), ev.eventTime,
        )
        return when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                listOf(sample(PointerAction.DOWN, ev.actionIndex))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                listOf(sample(PointerAction.UP, ev.actionIndex))
            MotionEvent.ACTION_MOVE -> (0 until ev.pointerCount).map { sample(PointerAction.MOVE, it) }
            MotionEvent.ACTION_CANCEL -> (0 until ev.pointerCount).map { sample(PointerAction.CANCEL, it) }
            else -> emptyList()
        }
    }
}

@Composable
private fun App(
    vm: AppViewModel,
    unlockStateFlow: StateFlow<UnlockState>,
    confirmBoxFlow: StateFlow<Box>,
    isKiosk: Boolean,
) {
    val kioskNote by vm.showKioskNote.collectAsStateWithLifecycle()
    val picture by vm.currentPicture.collectAsStateWithLifecycle()
    val transform by vm.transform.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val unlockState by unlockStateFlow.collectAsStateWithLifecycle()
    val confirmBox by confirmBoxFlow.collectAsStateWithLifecycle()

    val decorations by vm.decorations.collectAsStateWithLifecycle()
    val info by vm.pictureInfo.collectAsStateWithLifecycle()

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
            decorations = decorations,
            aspect = info?.aspect ?: 1f,
            decorationsReady = info != null,
            onAddDecoration = vm::addDecoration,
            onClearDecorations = vm::clearDecorations,
            showKioskNote = kioskNote && !isKiosk,
            onDismissKioskNote = vm::dismissKioskNote,
            overlay = { if (locked) UnlockOverlay(unlockState, confirmBox) },
        )
    }
}
