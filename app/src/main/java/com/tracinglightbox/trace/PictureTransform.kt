package com.tracinglightbox.trace

const val MIN_SCALE = 0.25f
const val MAX_SCALE = 8f

/**
 * Where the picture sits on screen. Offsets are fractions of the container size so a saved
 * position lands in the same relative place after rotation. `PictureTransform()` is "Fit".
 */
data class PictureTransform(
    val offsetXFrac: Float = 0f,
    val offsetYFrac: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val mirrored: Boolean = false,
)

fun PictureTransform.applyGesture(
    panXPx: Float,
    panYPx: Float,
    zoom: Float,
    rotationDeltaDeg: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
): PictureTransform = copy(
    offsetXFrac = offsetXFrac + if (containerWidthPx > 0f) panXPx / containerWidthPx else 0f,
    offsetYFrac = offsetYFrac + if (containerHeightPx > 0f) panYPx / containerHeightPx else 0f,
    scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE),
    rotationDeg = normalizeDegrees(rotationDeg + rotationDeltaDeg),
)

fun PictureTransform.toggleMirror(): PictureTransform = copy(mirrored = !mirrored)

fun PictureTransform.offsetPx(containerWidthPx: Float, containerHeightPx: Float): Pair<Float, Float> =
    offsetXFrac * containerWidthPx to offsetYFrac * containerHeightPx

fun PictureTransform.encode(): String =
    listOf(offsetXFrac, offsetYFrac, scale, rotationDeg).joinToString(";") + ";" + (if (mirrored) "1" else "0")

fun decodePictureTransform(s: String?): PictureTransform? {
    val parts = s?.split(";") ?: return null
    if (parts.size != 5) return null
    val nums = parts.take(4).map { it.toFloatOrNull() ?: return null }
    if (nums.any { !it.isFinite() }) return null
    val mirrored = when (parts[4]) {
        "1" -> true
        "0" -> false
        else -> return null
    }
    return PictureTransform(nums[0], nums[1], nums[2].coerceIn(MIN_SCALE, MAX_SCALE), normalizeDegrees(nums[3]), mirrored)
}

/** Maps any angle into (-180, 180]. */
private fun normalizeDegrees(deg: Float): Float {
    val r = ((deg % 360f) + 360f) % 360f
    return if (r > 180f) r - 360f else r
}
