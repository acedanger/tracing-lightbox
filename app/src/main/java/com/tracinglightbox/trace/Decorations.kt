package com.tracinglightbox.trace

import com.tracinglightbox.lock.Box
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

const val MAX_DECORATIONS = 5

/** Decoration size as a fraction of the picture's shorter displayed side. */
const val DECORATION_SIZE_FRACTION = 0.16f

enum class DecorationKind(val file: String) {
    HEART("heart.svg"),
    STAR("star.svg"),
    RAINBOW("rainbow.svg"),
    CLOUD("cloud.svg"),
}

/**
 * A small extra drawing placed around the picture. [x] and [y] are its center in picture units:
 * fractions of the displayed picture's width/height measured from the picture's center, so the
 * decoration moves, zooms, rotates and mirrors together with the picture.
 */
data class Decoration(val kind: DecorationKind, val x: Float, val y: Float)

fun List<Decoration>.withDecoration(d: Decoration): List<Decoration> =
    if (size >= MAX_DECORATIONS) this else this + d

fun encodeDecorations(list: List<Decoration>): String =
    list.joinToString("|") { "${it.kind.name},${it.x},${it.y}" }

/** Bad entries are skipped; never throws. */
fun decodeDecorations(s: String?): List<Decoration> {
    if (s.isNullOrBlank()) return emptyList()
    return s.split("|").mapNotNull { entry ->
        val p = entry.split(",")
        if (p.size != 3) return@mapNotNull null
        val kind = DecorationKind.entries.firstOrNull { it.name == p[0] } ?: return@mapNotNull null
        val x = p[1].toFloatOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        val y = p[2].toFloatOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        Decoration(kind, x, y)
    }.take(MAX_DECORATIONS)
}

// ---- geometry: picture units <-> screen pixels ----

/** Container size and the picture's displayed size at scale 1 (ContentScale.Fit). */
data class Frame(val containerW: Float, val containerH: Float, val imageW: Float, val imageH: Float) {
    val decorationSizePx: Float get() = DECORATION_SIZE_FRACTION * minOf(imageW, imageH)
}

fun fitFrame(containerW: Float, containerH: Float, aspect: Float): Frame {
    val a = if (aspect > 0f && aspect.isFinite()) aspect else 1f
    return if (containerW / containerH > a) {
        Frame(containerW, containerH, containerH * a, containerH)
    } else {
        Frame(containerW, containerH, containerW, containerW / a)
    }
}

/** Same math as the picture's graphicsLayer: mirror and scale, then rotate, then translate. */
fun PictureTransform.toScreen(frame: Frame, ux: Float, uy: Float): Pair<Float, Float> {
    val vx = ux * frame.imageW * scale * (if (mirrored) -1f else 1f)
    val vy = uy * frame.imageH * scale
    val r = Math.toRadians(rotationDeg.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val (ox, oy) = offsetPx(frame.containerW, frame.containerH)
    return frame.containerW / 2 + ox + vx * c - vy * s to frame.containerH / 2 + oy + vx * s + vy * c
}

fun PictureTransform.fromScreen(frame: Frame, sx: Float, sy: Float): Pair<Float, Float> {
    val (ox, oy) = offsetPx(frame.containerW, frame.containerH)
    val dx = sx - frame.containerW / 2 - ox
    val dy = sy - frame.containerH / 2 - oy
    val r = Math.toRadians(rotationDeg.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val vx = dx * c + dy * s
    val vy = -dx * s + dy * c
    return vx / (frame.imageW * scale) * (if (mirrored) -1f else 1f) to vy / (frame.imageH * scale)
}

/** Axis-aligned screen box around the picture's drawn content ([content] in picture units). */
fun PictureTransform.contentOnScreen(frame: Frame, content: Box): Box {
    val corners = listOf(
        toScreen(frame, content.left, content.top),
        toScreen(frame, content.right, content.top),
        toScreen(frame, content.left, content.bottom),
        toScreen(frame, content.right, content.bottom),
    )
    return Box(corners.minOf { it.first }, corners.minOf { it.second }, corners.maxOf { it.first }, corners.maxOf { it.second })
}

/**
 * Bounds of the dark (drawn) pixels in picture units (-0.5..0.5 on each axis), or null for a
 * blank image. [pixels] are ARGB, row by row.
 */
fun contentBounds(pixels: IntArray, w: Int, h: Int): Box? {
    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    for (y in 0 until h) for (x in 0 until w) {
        val p = pixels[y * w + x]
        val a = p ushr 24
        val lum = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
        if (a > 128 && lum < 128) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX < 0) return null
    return Box(minX.toFloat() / w - 0.5f, minY.toFloat() / h - 0.5f, (maxX + 1).toFloat() / w - 0.5f, (maxY + 1).toFloat() / h - 0.5f)
}

// ---- placement ----

data class Circle(val x: Float, val y: Float, val r: Float)

private fun distToBox(x: Float, y: Float, b: Box): Float {
    val dx = maxOf(b.left - x, 0f, x - b.right)
    val dy = maxOf(b.top - y, 0f, y - b.bottom)
    return hypot(dx, dy)
}

/**
 * Picks a random screen point for a decoration of [radius] that stays fully on [screen] and keeps
 * clear of the picture's [content] box, the [occupied] decorations and the [forbidden] areas
 * (buttons, unlock corner). Spots close to the picture are tried first. Returns null if there is
 * no room.
 */
fun placeDecoration(
    screen: Box,
    content: Box,
    occupied: List<Circle>,
    forbidden: List<Box>,
    radius: Float,
    random: Random,
    tries: Int = 400,
): Pair<Float, Float>? {
    val gap = radius * 0.15f
    val minX = screen.left + radius
    val maxX = screen.right - radius
    val minY = screen.top + radius
    val maxY = screen.bottom - radius
    if (minX > maxX || minY > maxY) return null

    fun fits(x: Float, y: Float): Boolean =
        distToBox(x, y, content) >= radius + gap &&
            forbidden.all { distToBox(x, y, it) >= radius + gap } &&
            occupied.all { hypot(x - it.x, y - it.y) >= radius + it.r + gap }

    val near = radius * 3.5f
    repeat(tries) {
        val x = minX + random.nextFloat() * (maxX - minX)
        val y = minY + random.nextFloat() * (maxY - minY)
        if (distToBox(x, y, content) <= radius + near && fits(x, y)) return x to y
    }
    repeat(tries) {
        val x = minX + random.nextFloat() * (maxX - minX)
        val y = minY + random.nextFloat() * (maxY - minY)
        if (fits(x, y)) return x to y
    }
    return null
}
