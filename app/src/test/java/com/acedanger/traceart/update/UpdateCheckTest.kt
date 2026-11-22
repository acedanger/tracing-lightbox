package com.acedanger.traceart.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun parsesVersionsWithOrWithoutV() {
        assertEquals(listOf(1, 6), parseVersion("v1.6"))
        assertEquals(listOf(1, 10, 2), parseVersion(" 1.10.2 "))
        assertNull(parseVersion("latest"))
        assertNull(parseVersion(""))
        assertNull(parseVersion("v1..2"))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(isNewer("v1.10", "1.9"))
        assertTrue(isNewer("v1.7", "1.6"))
        assertTrue(isNewer("v1.6.1", "1.6"))
        assertFalse(isNewer("v1.6", "1.6"))
        assertFalse(isNewer("v1.6.0", "1.6"))
        assertFalse(isNewer("v1.5", "1.6"))
    }

    @Test
    fun unreadableVersionsAreNeverNewer() {
        assertFalse(isNewer("nightly", "1.6"))
        assertFalse(isNewer("v1.7", "dev"))
        assertFalse(isNewer(null, "1.6"))
    }

    @Test
    fun checkIsDueAfterADay() {
        val day = 24 * 60 * 60 * 1000L
        assertTrue(isCheckDue(lastCheckMs = null, nowMs = 5))
        assertFalse(isCheckDue(lastCheckMs = 1_000, nowMs = 1_000 + day - 1))
        assertTrue(isCheckDue(lastCheckMs = 1_000, nowMs = 1_000 + day))
    }

    @Test
    fun clockGoingBackwardsMakesTheCheckDue() {
        assertTrue(isCheckDue(lastCheckMs = 10_000, nowMs = 5_000))
    }

    @Test
    fun parsesLatestReleaseAndPicksTheTapToInstallApk() {
        val json = """
            {"tag_name":"v1.6","html_url":"https://github.com/acedanger/tracing-lightbox/releases/tag/v1.6",
             "draft":false,"prerelease":false,"extra":1,
             "assets":[
               {"name":"trace-art-v1.6-usb.apk","browser_download_url":"https://x/usb.apk"},
               {"name":"trace-art-v1.6.apk","browser_download_url":"https://x/main.apk"}
             ]}
        """.trimIndent()
        val r = parseLatestRelease(json)!!
        assertEquals("v1.6", r.tag)
        assertEquals("https://github.com/acedanger/tracing-lightbox/releases/tag/v1.6", r.pageUrl)
        assertEquals("trace-art-v1.6.apk", r.apkName)
        assertEquals("https://x/main.apk", r.apkUrl)
    }

    @Test
    fun releaseWithoutApkStillParses() {
        val r = parseLatestRelease("""{"tag_name":"v2.0","html_url":"https://p"}""")!!
        assertEquals("v2.0", r.tag)
        assertNull(r.apkName)
        assertNull(r.apkUrl)
    }

    @Test
    fun badReleaseJsonGivesNull() {
        assertNull(parseLatestRelease("not json"))
        assertNull(parseLatestRelease("""{"message":"Not Found"}"""))
        assertNull(parseLatestRelease("""{"tag_name":"v1.7","html_url":"https://p","draft":true}"""))
    }
}
