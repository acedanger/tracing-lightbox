package com.acedanger.traceart.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Context.updateStore by preferencesDataStore(name = "updates")

private val AUTO_CHECK = booleanPreferencesKey("auto_check")
private val LAST_CHECK = longPreferencesKey("last_check_ms")
private val TAG = stringPreferencesKey("latest_tag")
private val PAGE = stringPreferencesKey("latest_page")
private val APK_NAME = stringPreferencesKey("latest_apk_name")
private val APK_URL = stringPreferencesKey("latest_apk_url")

/** What the app last learned about GitHub releases, so the badge shows even when offline. */
data class SavedUpdateInfo(val autoCheck: Boolean, val lastCheckMs: Long?, val latest: ReleaseInfo?)

class UpdateStore(context: Context) {
    private val store = context.applicationContext.updateStore

    suspend fun load(): SavedUpdateInfo {
        val p = store.data.first()
        val tag = p[TAG]
        val latest = tag?.let { ReleaseInfo(it, p[PAGE] ?: RELEASES_URL, p[APK_NAME], p[APK_URL]) }
        return SavedUpdateInfo(p[AUTO_CHECK] ?: true, p[LAST_CHECK], latest)
    }

    suspend fun setAutoCheck(on: Boolean) {
        store.edit { it[AUTO_CHECK] = on }
    }

    suspend fun saveCheck(nowMs: Long, latest: ReleaseInfo) {
        store.edit {
            it[LAST_CHECK] = nowMs
            it[TAG] = latest.tag
            it[PAGE] = latest.pageUrl
            if (latest.apkName != null) it[APK_NAME] = latest.apkName else it.remove(APK_NAME)
            if (latest.apkUrl != null) it[APK_URL] = latest.apkUrl else it.remove(APK_URL)
        }
    }
}

/** Asks GitHub for the newest release. Returns null when offline or GitHub answers oddly. */
suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "TraceArt-Android")
            if (conn.responseCode != 200) return@runCatching null
            parseLatestRelease(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
