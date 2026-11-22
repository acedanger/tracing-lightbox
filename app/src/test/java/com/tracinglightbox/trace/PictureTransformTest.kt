package com.tracinglightbox.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureTransformTest {

    private val eps = 0.0001f

    @Test
    fun panIsStoredAsFraction() {
        val t = PictureTransform().applyGesture(100f, 50f, 1f, 0f, 1000f, 500f)
        assertEquals(0.1f, t.offsetXFrac, eps)
        assertEquals(0.1f, t.offsetYFrac, eps)
    }

    @Test
    fun offsetsAreFractionsOfContainer() {
        val t = PictureTransform(offsetXFrac = 0.1f, offsetYFrac = 0.2f)
        val (x1, y1) = t.offsetPx(1200f, 2000f)
        assertEquals(120f, x1, eps)
        assertEquals(400f, y1, eps)
        val (x2, y2) = t.offsetPx(2000f, 1200f)
        assertEquals(200f, x2, eps)
        assertEquals(240f, y2, eps)
    }

    @Test
    fun zoomMultipliesAndClamps() {
        assertEquals(2f, PictureTransform(scale = 1f).applyGesture(0f, 0f, 2f, 0f, 100f, 100f).scale, eps)
        assertEquals(8f, PictureTransform(scale = 6f).applyGesture(0f, 0f, 2f, 0f, 100f, 100f).scale, eps)
        assertEquals(0.25f, PictureTransform(scale = 0.3f).applyGesture(0f, 0f, 0.5f, 0f, 100f, 100f).scale, eps)
    }

    @Test
    fun rotationNormalizes() {
        assertEquals(-160f, PictureTransform(rotationDeg = 170f).applyGesture(0f, 0f, 1f, 30f, 100f, 100f).rotationDeg, eps)
        assertEquals(160f, PictureTransform(rotationDeg = -170f).applyGesture(0f, 0f, 1f, -30f, 100f, 100f).rotationDeg, eps)
        assertEquals(180f, PictureTransform(rotationDeg = 180f).applyGesture(0f, 0f, 1f, 0f, 100f, 100f).rotationDeg, eps)
    }

    @Test
    fun mirrorToggles() {
        val t = PictureTransform(offsetXFrac = 0.3f, scale = 2f)
        assertEquals(true, t.toggleMirror().mirrored)
        assertEquals(t, t.toggleMirror().toggleMirror())
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val t = PictureTransform(0.12f, -0.34f, 2.5f, -45.5f, true)
        assertEquals(t, decodePictureTransform(t.encode()))
    }

    @Test
    fun decodeRejectsGarbage() {
        assertNull(decodePictureTransform(null))
        assertNull(decodePictureTransform(""))
        assertNull(decodePictureTransform("1;2;3"))
        assertNull(decodePictureTransform("a;b;c;d;e"))
    }
}
