package com.acedanger.traceart.photos

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Album the photos go into; it shows up as "Trace Art" in the Samsung Gallery. */
const val ALBUM_PATH = "Pictures/Trace Art/"

/** One photo of a finished drawing. [pictureId] is the outline that was traced, when known. */
data class DrawingPhoto(val uri: Uri, val takenMs: Long, val pictureId: String?)

/**
 * Photos live in the shared Pictures/Trace Art album (so they're in the tablet's Gallery too),
 * and My Drawings lists only the ones this app saved there. Android lets an app see and delete
 * its own photos without any storage permission.
 */
class PhotoStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** What a new photo should be saved as. */
    fun newPhotoValues(pictureId: String?): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, photoFileName(pictureId, LocalDateTime.now()))
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, ALBUM_PATH)
    }

    val collectionUri: Uri get() = collection

    /** Saves an upright, already-cropped photo to the Trace Art album. Returns its uri, or null. */
    suspend fun saveJpeg(bitmap: Bitmap, pictureId: String?): Uri? = withContext(Dispatchers.IO) {
        val values = newPhotoValues(pictureId).apply { put(MediaStore.MediaColumns.IS_PENDING, 1) }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } == true
        }.getOrDefault(false)
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return@withContext null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        uri
    }

    /** Newest first. */
    suspend fun list(): List<DrawingPhoto> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val out = mutableListOf<DrawingPhoto>()
        runCatching {
            resolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(ALBUM_PATH),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (c.moveToNext()) {
                    out += DrawingPhoto(
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        takenMs = c.getLong(dateCol) * 1000,
                        pictureId = pictureIdFromFileName(c.getString(nameCol)),
                    )
                }
            }
        }
        out
    }

    /** Removes a photo from My Drawings and the tablet's Gallery. */
    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }
}
