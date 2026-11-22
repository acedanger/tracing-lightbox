package com.acedanger.traceart.photos

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** US Letter / A4-ish portrait paper. */
const val PAPER_ASPECT = 0.77f

data class FrameRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

data class CropBox(val x: Int, val y: Int, val width: Int, val height: Int)

/** The biggest [aspect]-shaped frame that fits in the view after leaving the given margins, centered there. */
fun paperFrame(viewW: Float, viewH: Float, top: Float, bottom: Float, side: Float, aspect: Float = PAPER_ASPECT): FrameRect {
    val availW = max(1f, viewW - 2 * side)
    val availH = max(1f, viewH - top - bottom)
    val w = min(availW, availH * aspect)
    val h = w / aspect
    val cx = viewW / 2
    val cy = top + availH / 2
    return FrameRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}

/**
 * Where [frame] (in view pixels) lands in an upright photo of [imageW]x[imageH]. The preview
 * fills the view and is center-cropped, so the photo is mapped the same way.
 */
fun cropForFrame(frame: FrameRect, viewW: Float, viewH: Float, imageW: Int, imageH: Int): CropBox {
    val scale = max(viewW / imageW, viewH / imageH) // view px per image px (fill-center)
    val offX = (imageW * scale - viewW) / 2
    val offY = (imageH * scale - viewH) / 2
    fun ix(v: Float) = ((v + offX) / scale).roundToInt().coerceIn(0, imageW)
    fun iy(v: Float) = ((v + offY) / scale).roundToInt().coerceIn(0, imageH)
    val x0 = ix(frame.left)
    val y0 = iy(frame.top)
    val x1 = max(x0 + 1, ix(frame.right)).coerceAtMost(imageW)
    val y1 = max(y0 + 1, iy(frame.bottom)).coerceAtMost(imageH)
    return CropBox(min(x0, imageW - 1), min(y0, imageH - 1), x1 - min(x0, imageW - 1), y1 - min(y0, imageH - 1))
}
