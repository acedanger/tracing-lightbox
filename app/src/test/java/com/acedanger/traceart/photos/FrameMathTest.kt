package com.acedanger.traceart.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameMathTest {

    @Test
    fun frameIsPaperShapedAndCenteredInTheFreeArea() {
        // 1000x2000 view, 100 free at the top, 300 at the bottom, 50 at the sides.
        val f = paperFrame(1000f, 2000f, top = 100f, bottom = 300f, side = 50f, aspect = 0.75f)
        assertEquals(0.75f, f.width / f.height, 0.001f)
        assertEquals(900f, f.width, 0.5f) // width-limited: 900 wide fits in 1600 of height
        assertEquals(500f, (f.left + f.right) / 2, 0.5f)
        assertEquals(100f + 800f, (f.top + f.bottom) / 2, 0.5f)
    }

    @Test
    fun heightLimitedFrameOnAWideScreen() {
        val f = paperFrame(2000f, 1000f, top = 100f, bottom = 100f, side = 50f, aspect = 0.75f)
        assertEquals(800f, f.height, 0.5f)
        assertEquals(600f, f.width, 0.5f)
        assertEquals(1000f, (f.left + f.right) / 2, 0.5f)
    }

    @Test
    fun cropScalesTheFrameFromViewToImage() {
        // View 1000x2000, frame in the middle half; image is the same shape at 2x.
        val frame = FrameRect(250f, 500f, 750f, 1500f)
        val crop = cropForFrame(frame, viewW = 1000f, viewH = 2000f, imageW = 2000, imageH = 4000)
        assertEquals(CropBox(500, 1000, 1000, 2000), crop)
    }

    @Test
    fun cropAssumesTheImageWasCenterCroppedLikeThePreview() {
        // Image is wider than the view (4:3 vs 1:2); the view shows the centered 1:2 strip.
        val frame = FrameRect(0f, 0f, 1000f, 2000f)
        val crop = cropForFrame(frame, viewW = 1000f, viewH = 2000f, imageW = 3000, imageH = 4000)
        assertEquals(CropBox(500, 0, 2000, 4000), crop)
    }

    @Test
    fun cropNeverLeavesTheImage() {
        val frame = FrameRect(-100f, -100f, 1200f, 2200f)
        val c = cropForFrame(frame, viewW = 1000f, viewH = 2000f, imageW = 1000, imageH = 2000)
        assertTrue(c.x >= 0 && c.y >= 0 && c.x + c.width <= 1000 && c.y + c.height <= 2000)
        assertTrue(c.width > 0 && c.height > 0)
    }
}
