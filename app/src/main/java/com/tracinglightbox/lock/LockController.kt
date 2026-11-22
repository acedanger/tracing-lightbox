package com.tracinglightbox.lock

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tracinglightbox.admin.KioskAdminReceiver

/** Applies and removes everything "locked" means at the window / OS level. Idempotent. */
class LockController(private val activity: ComponentActivity) {

    /** startLockTask() is requested once per lock, so a declined pinning prompt is not re-shown on every resume. */
    private var lockTaskRequested = false

    fun applyLocked() {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        if (!lockTaskRequested && !inLockTask()) {
            lockTaskRequested = true
            if (isKiosk) runCatching { allowKioskLockTask() }
            runCatching { activity.startLockTask() }
        }
    }

    /** True when the app is the device owner, so lock task is a full kiosk with no exits or prompts. */
    val isKiosk: Boolean
        get() = dpm.isDeviceOwnerApp(activity.packageName)

    /** Allowlist this app for lock task and turn off every system UI feature while locked. */
    private fun allowKioskLockTask() {
        dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
    }

    private val dpm: DevicePolicyManager
        get() = activity.getSystemService(DevicePolicyManager::class.java)

    private val admin: ComponentName
        get() = ComponentName(activity, KioskAdminReceiver::class.java)

    fun applyUnlocked() {
        val window = activity.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        lockTaskRequested = false
        if (inLockTask()) runCatching { activity.stopLockTask() }
    }

    private fun inLockTask(): Boolean =
        activity.getSystemService(ActivityManager::class.java).lockTaskModeState !=
            ActivityManager.LOCK_TASK_MODE_NONE
}
