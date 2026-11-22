package com.acedanger.traceart.library

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
data class Picture(val id: String, val name: String, val file: String, val tags: List<String> = emptyList())

private const val PICTURES_DIR = "pictures"
private val SUPPORTED_EXTENSIONS = setOf("svg", "png", "jpg", "jpeg")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Parses `assets/pictures/manifest.json`: `{"pictures":[{"id","name","file"}, ...]}`.
 * Bad entries (missing fields, unsupported extension, duplicate id) are skipped; broken JSON
 * yields an empty list. Never throws.
 */
fun parseManifest(text: String): List<Picture> {
    val entries = runCatching {
        json.parseToJsonElement(text).jsonObject["pictures"]?.jsonArray
    }.getOrNull() ?: return emptyList()

    val seen = mutableSetOf<String>()
    return entries.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.string("id") ?: return@mapNotNull null
        val name = obj.string("name") ?: return@mapNotNull null
        val file = obj.string("file") ?: return@mapNotNull null
        val ext = file.substringAfterLast('.', "").lowercase()
        if (ext !in SUPPORTED_EXTENSIONS || !seen.add(id)) return@mapNotNull null
        Picture(id, name, file, obj.tags())
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

/** Optional "tags" array: strings only, trimmed, lowercased, de-duplicated; anything else is skipped. */
private fun JsonObject.tags(): List<String> =
    (this["tags"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

/** Every tag used by [pictures], once each, sorted by name. */
fun allTags(pictures: List<Picture>): List<String> = pictures.flatMap { it.tags }.distinct().sorted()

/** Pictures carrying [tag], in their original order; a null [tag] means no filter. */
fun filterByTag(pictures: List<Picture>, tag: String?): List<Picture> =
    if (tag == null) pictures else pictures.filter { tag in it.tags }

fun loadCatalog(context: Context): List<Picture> = runCatching {
    context.assets.open("$PICTURES_DIR/manifest.json").bufferedReader().use { it.readText() }
}.map(::parseManifest).getOrDefault(emptyList())

fun Picture.assetUri(): String = "file:///android_asset/$PICTURES_DIR/$file"
