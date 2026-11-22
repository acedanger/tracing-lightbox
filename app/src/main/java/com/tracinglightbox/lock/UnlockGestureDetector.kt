package com.tracinglightbox.lock

import kotlin.math.hypot

/** A plain rectangle in screen pixels (kept free of android.graphics so it is JVM-testable). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

data class UnlockConfig(
    val zoneSizePx: Float,
    val maxTouchMajorPx: Float,
    val confirmButton: Box,
    val holdMs: Long = 3000,
    val ringDelayMs: Long = 1000,
    val confirmWindowMs: Long = 5000,
    /** The Unlock tap must lift within this time... */
    val tapMaxMs: Long = 500,
    /** ...and move less than this far, so a resting hand or a pencil stroke never counts. */
    val tapSlopPx: Float = 40f,
)

/** DOWN covers ACTION_DOWN and ACTION_POINTER_DOWN; UP covers ACTION_UP and ACTION_POINTER_UP. */
enum class PointerAction { DOWN, MOVE, UP, CANCEL }

data class PointerSample(
    val action: PointerAction,
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val touchMajorPx: Float,
    val timeMs: Long,
)

sealed interface UnlockState {
    data object Idle : UnlockState
    /** [progress] runs 0..1 over the hold; [showRing] once the ring delay has passed. */
    data class Holding(val progress: Float, val showRing: Boolean) : UnlockState
    data class Confirming(val remainingMs: Long) : UnlockState
    data object Unlocked : UnlockState
}

/**
 * Detects the locked-screen unlock gesture: hold one finger in the top-left corner zone
 * for [UnlockConfig.holdMs], then tap the confirm button within [UnlockConfig.confirmWindowMs].
 * Every other touch is ignored. Palm-sized contacts never count.
 */
class UnlockGestureDetector(private val config: UnlockConfig) {

    var state: UnlockState = UnlockState.Idle
        private set

    private var holdPointerId = -1
    private var holdStartMs = 0L
    private var confirmEndMs = 0L
    private var tapPointerId = -1
    private var tapStartMs = 0L
    private var tapStartX = 0f
    private var tapStartY = 0f

    fun onPointer(sample: PointerSample) {
        advanceTime(sample.timeMs)
        when (state) {
            UnlockState.Idle -> onIdlePointer(sample)
            is UnlockState.Holding -> onHoldingPointer(sample)
            is UnlockState.Confirming -> onConfirmingPointer(sample)
            UnlockState.Unlocked -> Unit
        }
    }

    fun onTick(timeMs: Long) = advanceTime(timeMs)

    fun reset() {
        state = UnlockState.Idle
        holdPointerId = -1
        tapPointerId = -1
    }

    private fun inZone(x: Float, y: Float) = x < config.zoneSizePx && y < config.zoneSizePx

    private fun isPalm(s: PointerSample) = s.touchMajorPx > config.maxTouchMajorPx

    private fun onIdlePointer(s: PointerSample) {
        if (s.action == PointerAction.DOWN && inZone(s.x, s.y) && !isPalm(s)) {
            holdPointerId = s.pointerId
            holdStartMs = s.timeMs
            state = UnlockState.Holding(progress = 0f, showRing = false)
        }
    }

    private fun onHoldingPointer(s: PointerSample) {
        if (s.pointerId != holdPointerId) return
        val cancelled = when (s.action) {
            PointerAction.UP, PointerAction.CANCEL -> true
            PointerAction.MOVE, PointerAction.DOWN -> !inZone(s.x, s.y) || isPalm(s)
        }
        if (cancelled) reset()
    }

    /** Only a short, still, finger-sized tap on the button unlocks. */
    private fun onConfirmingPointer(s: PointerSample) {
        if (s.action == PointerAction.DOWN) {
            if (config.confirmButton.contains(s.x, s.y) && !isPalm(s)) {
                tapPointerId = s.pointerId
                tapStartMs = s.timeMs
                tapStartX = s.x
                tapStartY = s.y
            }
            return
        }
        if (s.pointerId != tapPointerId) return
        val stillATap = !isPalm(s) &&
            s.timeMs - tapStartMs <= config.tapMaxMs &&
            hypot(s.x - tapStartX, s.y - tapStartY) < config.tapSlopPx
        when {
            !stillATap || s.action == PointerAction.CANCEL -> tapPointerId = -1
            s.action == PointerAction.UP -> {
                tapPointerId = -1
                state = UnlockState.Unlocked
            }
        }
    }

    private fun advanceTime(t: Long) {
        when (state) {
            is UnlockState.Holding -> {
                val elapsed = t - holdStartMs
                if (elapsed >= config.holdMs) {
                    holdPointerId = -1
                    tapPointerId = -1
                    confirmEndMs = t + config.confirmWindowMs
                    state = UnlockState.Confirming(config.confirmWindowMs)
                } else {
                    state = UnlockState.Holding(
                        progress = elapsed.toFloat() / config.holdMs,
                        showRing = elapsed >= config.ringDelayMs,
                    )
                }
            }
            is UnlockState.Confirming -> {
                val remaining = confirmEndMs - t
                state = if (remaining <= 0) {
                    reset(); UnlockState.Idle
                } else {
                    UnlockState.Confirming(remaining)
                }
            }
            else -> Unit
        }
    }
}
