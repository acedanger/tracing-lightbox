package com.tracinglightbox.trace

import com.tracinglightbox.lock.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class DecorationsTest {

    private val eps = 0.01f

    // ---- storage ----

    @Test
    fun encodeDecodeRoundTrip() {
        val list = listOf(
            Decoration(DecorationKind.HEART, 0.61f, -0.2f),
            Decoration(DecorationKind.RAINBOW, -0.7f, 0.45f),
        )
        assertEquals(list, decodeDecorations(encodeDecorations(list)))
    }

    @Test
    fun decodeSkipsBadEntries() {
        assertEquals(
            listOf(Decoration(DecorationKind.STAR, 0.5f, 0.5f)),
            decodeDecorations("STAR,0.5,0.5|UNICORN,1,1|HEART,x,1|CLOUD,1"),
        )
        assertEquals(emptyList<Decoration>(), decodeDecorations(null))
        assertEquals(emptyList<Decoration>(), decodeDecorations(""))
    }

    @Test
    fun atMostFiveDecorations() {
        var list = emptyList<Decoration>()
        repeat(7) { list = list.withDecoration(Decoration(DecorationKind.STAR, it.toFloat(), 0f)) }
        assertEquals(MAX_DECORATIONS, list.size)
        assertEquals(5, MAX_DECORATIONS)
    }

    // ---- geometry ----

    @Test
    fun fitFrameKeepsAspect() {
        val f = fitFrame(1200f, 2000f, aspect = 1f)
        assertEquals(1200f, f.imageW, eps)
        assertEquals(1200f, f.imageH, eps)
        val g = fitFrame(2000f, 1200f, aspect = 2f)
        assertEquals(2000f, g.imageW, eps)
        assertEquals(1000f, g.imageH, eps)
    }

    @Test
    fun pictureCenterMapsToContainerCenterPlusOffset() {
        val frame = fitFrame(1000f, 2000f, 1f)
        val t = PictureTransform(offsetXFrac = 0.1f, offsetYFrac = -0.05f)
        val (x, y) = t.toScreen(frame, 0f, 0f)
        assertEquals(600f, x, eps)
        assertEquals(900f, y, eps)
    }

    @Test
    fun toScreenAndBackRoundTripWithRotationMirrorAndZoom() {
        val frame = fitFrame(1200f, 1900f, 1f)
        val t = PictureTransform(0.12f, -0.08f, 1.7f, 33f, mirrored = true)
        val (sx, sy) = t.toScreen(frame, 0.31f, -0.42f)
        val (ux, uy) = t.fromScreen(frame, sx, sy)
        assertEquals(0.31f, ux, 0.0005f)
        assertEquals(-0.42f, uy, 0.0005f)
    }

    @Test
    fun mirrorFlipsHorizontally() {
        val frame = fitFrame(1000f, 1000f, 1f)
        val (x, _) = PictureTransform(mirrored = true).toScreen(frame, 0.25f, 0f)
        assertEquals(250f, x, eps)
    }

    @Test
    fun contentBoundsFindsDarkPixels() {
        val w = 10
        val h = 10
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        px[2 * w + 3] = 0xFF000000.toInt() // x=3, y=2
        px[7 * w + 6] = 0xFF101010.toInt() // x=6, y=7
        val b = contentBounds(px, w, h)!!
        assertEquals(-0.2f, b.left, eps)   // 3/10 - 0.5
        assertEquals(-0.3f, b.top, eps)    // 2/10 - 0.5
        assertEquals(0.2f, b.right, eps)   // 7/10 - 0.5
        assertEquals(0.3f, b.bottom, eps)  // 8/10 - 0.5
    }

    @Test
    fun contentBoundsOfBlankImageIsNull() {
        assertNull(contentBounds(IntArray(16) { 0xFFFFFFFF.toInt() }, 4, 4))
    }

    // ---- placement ----

    private val screen = Box(0f, 0f, 1200f, 1900f)
    private val content = Box(250f, 500f, 950f, 1300f)
    private val radius = 80f

    private fun distToBox(x: Float, y: Float, b: Box): Float {
        val dx = maxOf(b.left - x, 0f, x - b.right)
        val dy = maxOf(b.top - y, 0f, y - b.bottom)
        return hypot(dx, dy)
    }

    @Test
    fun neverOverlapsPictureAndStaysOnScreen() {
        repeat(200) { seed ->
            val p = placeDecoration(screen, content, emptyList(), emptyList(), radius, Random(seed))
            assertNotNull(p)
            val (x, y) = p!!
            assertTrue("overlaps picture: $p", distToBox(x, y, content) >= radius)
            assertTrue("off screen: $p", x - radius >= 0f && x + radius <= 1200f && y - radius >= 0f && y + radius <= 1900f)
        }
    }

    @Test
    fun avoidsOtherDecorationsAndButtons() {
        val others = listOf(Circle(150f, 300f, 80f), Circle(1050f, 1500f, 80f))
        val buttons = listOf(Box(0f, 0f, 700f, 200f), Box(0f, 1700f, 1200f, 1900f))
        repeat(200) { seed ->
            val (x, y) = placeDecoration(screen, content, others, buttons, radius, Random(seed))!!
            others.forEach { assertTrue(hypot(x - it.x, y - it.y) >= radius + it.r) }
            buttons.forEach { assertTrue(distToBox(x, y, it) >= radius) }
        }
    }

    @Test
    fun noRoomGivesNull() {
        val huge = Box(-10f, -10f, 1210f, 1910f)
        assertNull(placeDecoration(screen, huge, emptyList(), emptyList(), radius, Random(1)))
    }
}
