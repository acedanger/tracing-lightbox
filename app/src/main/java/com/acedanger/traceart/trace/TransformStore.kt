package com.acedanger.traceart.trace

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "tracing")

private val LAST_PICTURE = stringPreferencesKey("last_picture")
private val KIOSK_NOTE_DISMISSED = booleanPreferencesKey("kiosk_note_dismissed")
private fun transformKey(pictureId: String) = stringPreferencesKey("t_$pictureId")
private fun decorationsKey(pictureId: String) = stringPreferencesKey("d_$pictureId")

/** Remembers each picture's position and the last picture used. */
class TransformStore(context: Context) {
    private val store = context.applicationContext.dataStore

    suspend fun load(pictureId: String): PictureTransform? =
        decodePictureTransform(store.data.first()[transformKey(pictureId)])

    suspend fun save(pictureId: String, t: PictureTransform) {
        store.edit { it[transformKey(pictureId)] = t.encode() }
    }

    suspend fun loadDecorations(pictureId: String): List<Decoration> =
        decodeDecorations(store.data.first()[decorationsKey(pictureId)])

    suspend fun saveDecorations(pictureId: String, list: List<Decoration>) {
        store.edit { it[decorationsKey(pictureId)] = encodeDecorations(list) }
    }

    suspend fun lastPictureId(): String? = store.data.first()[LAST_PICTURE]

    suspend fun setLastPictureId(id: String) {
        store.edit { it[LAST_PICTURE] = id }
    }

    suspend fun kioskNoteDismissed(): Boolean = store.data.first()[KIOSK_NOTE_DISMISSED] ?: false

    suspend fun dismissKioskNote() {
        store.edit { it[KIOSK_NOTE_DISMISSED] = true }
    }
}
