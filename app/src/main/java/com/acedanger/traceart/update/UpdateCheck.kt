package com.acedanger.traceart.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

const val REPO_OWNER = "acedanger"
const val REPO_NAME = "tracing-lightbox"
const val OWNER_URL = "https://github.com/$REPO_OWNER"
const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
const val RELEASES_URL = "$REPO_URL/releases"
const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

/** The newest published GitHub release. [apkName]/[apkUrl] are the tap-to-install APK, if attached. */
data class ReleaseInfo(val tag: String, val pageUrl: String, val apkName: String?, val apkUrl: String?)

/** "v1.6" or "1.6.2" → [1, 6] / [1, 6, 2]; anything else → null. */
fun parseVersion(s: String): List<Int>? {
    val parts = s.trim().removePrefix("v").removePrefix("V").split('.')
    if (parts.isEmpty()) return null
    return parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
}

/** True only when both versions are readable and [latest] is strictly newer (1.6.0 == 1.6). */
fun isNewer(latest: String?, installed: String): Boolean {
    val a = latest?.let(::parseVersion) ?: return false
    val b = parseVersion(installed) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Checks at most once a day; never-checked or a clock that went backwards means check now. */
fun isCheckDue(lastCheckMs: Long?, nowMs: Long, intervalMs: Long = CHECK_INTERVAL_MS): Boolean =
    lastCheckMs == null || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs

private val json = Json { ignoreUnknownKeys = true }

/** Reads GitHub's releases/latest response. Drafts, pre-releases and error bodies give null. */
fun parseLatestRelease(body: String): ReleaseInfo? {
    val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    fun flag(k: String) = (obj[k] as? JsonPrimitive)?.booleanOrNull == true
    if (flag("draft") || flag("prerelease")) return null
    val tag = str(obj, "tag_name")?.takeIf { it.isNotBlank() } ?: return null
    val page = str(obj, "html_url") ?: RELEASES_URL
    val apk = (obj["assets"] as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .firstOrNull { a -> str(a, "name")?.let { it.endsWith(".apk") && !it.endsWith("-usb.apk") } == true }
    return ReleaseInfo(tag, page, apk?.let { str(it, "name") }, apk?.let { str(it, "browser_download_url") })
}
