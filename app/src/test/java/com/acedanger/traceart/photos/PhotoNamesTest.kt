package com.acedanger.traceart.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class PhotoNamesTest {

    private val time = LocalDateTime.of(2026, 11, 22, 15, 4, 9)

    @Test
    fun namesCarryTheDateTimeAndTracedPicture() {
        assertEquals("TraceArt_2026-11-22_15-04-09_tiger-portrait.jpg", photoFileName("tiger-portrait", time))
    }

    @Test
    fun photoWithoutAPictureHasNoSuffix() {
        assertEquals("TraceArt_2026-11-22_15-04-09.jpg", photoFileName(null, time))
    }

    @Test
    fun oddCharactersInIdsAreDropped() {
        assertEquals("TraceArt_2026-11-22_15-04-09_catbox.jpg", photoFileName("Cat_Box/../", time))
        assertEquals("TraceArt_2026-11-22_15-04-09.jpg", photoFileName("___", time))
    }

    @Test
    fun readsThePictureIdBack() {
        assertEquals("tiger-portrait", pictureIdFromFileName("TraceArt_2026-11-22_15-04-09_tiger-portrait.jpg"))
        assertEquals("dragon-head", pictureIdFromFileName("TraceArt_2026-11-22_15-04-09_dragon-head (1).jpg"))
    }

    @Test
    fun namesWithoutAPictureGiveNull() {
        assertNull(pictureIdFromFileName("TraceArt_2026-11-22_15-04-09.jpg"))
        assertNull(pictureIdFromFileName("IMG_1234.jpg"))
        assertNull(pictureIdFromFileName(null))
    }
}
