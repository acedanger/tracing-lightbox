package com.acedanger.traceart.photos

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val PREFIX = "TraceArt_"
private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
private val NAME = Regex("""^TraceArt_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}_([a-z0-9-]+)""")

/**
 * "TraceArt_2026-11-22_15-04-09_tiger.jpg": sorts by date in any gallery and remembers which
 * picture was traced, so My Drawings can show the outline next to the photo.
 */
fun photoFileName(pictureId: String?, time: LocalDateTime): String {
    val id = pictureId?.lowercase()?.filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.orEmpty()
    return PREFIX + STAMP.format(time) + (if (id.isEmpty()) "" else "_$id") + ".jpg"
}

/** The traced picture's id from a photo name, or null. Tolerates Android's " (1)" for duplicate names. */
fun pictureIdFromFileName(name: String?): String? = name?.let { NAME.find(it)?.groupValues?.get(1) }
