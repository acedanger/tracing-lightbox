# Tracing Lightbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A private Android tracing-lightbox app for a Galaxy Tab S6 Lite. It shows a bundled outline picture that can be positioned, then locked so that no touch can move it, with kiosk lock when the app is device owner.

**Architecture:** One Activity hosting Jetpack Compose (two screens: Library and Trace). All lock-time touch handling happens in `MainActivity.dispatchTouchEvent`, which feeds raw events to a pure-Kotlin `UnlockGestureDetector` and never passes them to Compose. Pure logic (unlock state machine, transform math and encoding, manifest parsing) is JVM-unit-tested. Android side effects (window flags, lock task, device admin) live in `LockController` and are verified on the device.

**Tech Stack:** Kotlin, Jetpack Compose (BOM), activity-compose, lifecycle-viewmodel-compose, Coil 3 (`coil-compose` + `coil-svg`), DataStore Preferences, kotlinx-serialization-json, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-22-tracing-lightbox-design.md`

## Global Constraints

- Package / applicationId: `com.tracinglightbox`. Repo and project name: `tracing-lightbox`.
- minSdk 28; compileSdk 36.1 (the only installed platform is `android-36.1`; use AGP's `compileSdk { version = release(36) { minorApiLevel = 1 } }` DSL); targetSdk 36.
- Build with Gradle 9.7.1 (already cached at `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.7.1-bin`), and run it with Android Studio's JBR: `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr`. adb is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- AGP: the newest stable release compatible with Gradle 9.7.1 (9.x). Use AGP's built-in Kotlin support plus the `org.jetbrains.kotlin.plugin.compose` and `plugin.serialization` plugins at the matching Kotlin version. Declare all versions in `gradle/libs.versions.toml`.
- **No permissions in the manifest. No `INTERNET`.** (Coil must not pull in network: use `coil-compose` + `coil-svg` only, never `coil-network-*`.)
- Unlock: corner zone = top-left square, 72dp; hold = 3000 ms; ring appears after 1000 ms; confirm window = 5000 ms; a contact with touchMajor > 20 mm counts as a palm and is ignored.
- Picture display: dark lines on pure white (`#FFFFFF`) background; while locked, window brightness = 1.0f and the screen stays on.
- Nothing is drawn on the locked screen except the picture, plus the ring and Unlock button while the unlock gesture is in progress.
- Transform: scale clamped to [0.25, 8.0]; offsets stored as fractions of the container size.
- Bundled pictures: pure black lines on pure white, free to use or original.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Many contacts at once while locked** (palm, fingers and paper all down, moving around): the picture and app state must not change, and no unlock progress may start from contacts outside the corner. → Task 2 `pointersOutsideZoneNeverStartHold`, `extraPointersDoNotDisturbHold`; Task 6 device check.
2. **Slow tracing that wanders into the corner and stays 3 s:** this may reach Confirming, but with no tap the picture stays locked. → Task 2 `confirmingTimesOutToIdle`.
3. **App recreated or briefly backgrounded while locked** (rotation attempt, shade, power button off/on): it comes back still locked, with the flags re-applied. → Task 6 `AppViewModel` saved state, plus device check.
4. **Saved position reopened in the other orientation:** the picture lands in the same relative place, not off-screen. → Task 3 `offsetsAreFractionsOfContainer`.
5. **Bad manifest entry** (missing field, unknown extension, duplicate id, broken JSON): that entry is skipped and the app doesn't crash. → Task 4 tests.

---

### Task 1: Project scaffold, local repo, private GitHub repo

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/tracinglightbox/MainActivity.kt`, `app/src/main/res/values/strings.xml` (`app_name` = "Tracing Lightbox"), launcher icon (a simple adaptive icon is fine)
- Create: `.gitignore` (standard Android: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `captures/`, `.kotlin/`), `README.md` (title, one-paragraph description, build command; later tasks add sections)
- Move in: `docs/superpowers/specs/2026-11-22-tracing-lightbox-design.md` and this plan

**Interfaces:**
- Produces: a building app module `:app`; `MainActivity : ComponentActivity` showing a placeholder Compose `Text`.

- [ ] **Step 1:** Create the repo folder at the location the user chose, then `git init -b main`.
- [ ] **Step 2:** Generate the wrapper: run the cached `gradle-9.7.1\bin\gradle.bat wrapper --gradle-version 9.7.1` in the repo root with `JAVA_HOME` set to the JBR.
- [ ] **Step 3:** Write the Gradle files and manifest to the Global Constraints (single Activity, `android:screenOrientation` unset, theme `Theme.Material.Light.NoActionBar`, no permissions). Add the test dependency `junit:junit:4.13.2`.
- [ ] **Step 4:** Verify. Run `.\gradlew.bat assembleDebug testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists. Run `aapt2 dump permissions` (or inspect the merged manifest) and expect no `uses-permission` lines.
- [ ] **Step 5:** Commit: `chore: scaffold Android project`.
- [ ] **Step 6:** Run `gh repo create tracing-lightbox --private --source . --remote origin --push`. Verify with `gh repo view tracing-lightbox --json visibility` and expect `"visibility":"PRIVATE"`.

---

### Task 2: Unlock gesture state machine (pure Kotlin)

**Files:**
- Create: `app/src/main/java/com/tracinglightbox/lock/UnlockGestureDetector.kt`
- Test: `app/src/test/java/com/tracinglightbox/lock/UnlockGestureDetectorTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) { fun contains(x: Float, y: Float): Boolean }
data class UnlockConfig(
    val zoneSizePx: Float, val maxTouchMajorPx: Float, val confirmButton: Box,
    val holdMs: Long = 3000, val ringDelayMs: Long = 1000, val confirmWindowMs: Long = 5000)
enum class PointerAction { DOWN, MOVE, UP, CANCEL }   // DOWN covers ACTION_DOWN and ACTION_POINTER_DOWN
data class PointerSample(val action: PointerAction, val pointerId: Int, val x: Float, val y: Float, val touchMajorPx: Float, val timeMs: Long)
sealed interface UnlockState {
    data object Idle : UnlockState
    data class Holding(val progress: Float, val showRing: Boolean) : UnlockState   // progress 0..1
    data class Confirming(val remainingMs: Long) : UnlockState
    data object Unlocked : UnlockState
}
class UnlockGestureDetector(config: UnlockConfig) {
    val state: UnlockState
    fun onPointer(sample: PointerSample)
    fun onTick(timeMs: Long)
    fun reset()
}
```

The rules (the algorithm the types don't determine):
- **Idle:** a DOWN inside the zone (`x < zoneSizePx && y < zoneSizePx`) with `touchMajorPx <= maxTouchMajorPx` starts Holding and tracks that pointer and its start time. Every other event is ignored.
- **Holding:** the tracked pointer MOVEs out of the zone, its touchMajor exceeds the max, or it goes UP/CANCEL → Idle. Events from other pointers are ignored. On any event or tick with `t - start >= holdMs` → Confirming, ending at `t + confirmWindowMs`. `progress = (t - start) / holdMs`, and `showRing = t - start >= ringDelayMs`.
- **Confirming:** a DOWN inside `confirmButton` tracks that tap pointer. Its UP inside `confirmButton` → Unlocked. Reaching the end time (by event or tick) → Idle.
- **Unlocked:** terminal until `reset()`.

- [ ] **Step 1: Write the failing tests.** Use zone 100, max touchMajor 50, confirmButton `Box(400f, 400f, 600f, 500f)`, and helper `down/move/up(id, x, y, t, major = 10f)`:
  - `holdInCornerForThreeSecondsShowsConfirm`: down(0, 10, 10, 0); tick(2999) → `Holding`; tick(3000) → `Confirming(5000)`.
  - `ringAppearsAfterOneSecond`: down at 0; tick(999) → `showRing == false`; tick(1000) → `showRing == true`, progress ≈ 0.333.
  - `releaseEarlyCancels`: down 0, up(0, …, 2000) → `Idle`; tick(3500) → still `Idle`.
  - `leavingZoneCancels`: down 0; move(0, 150, 10, 500) → `Idle`.
  - `palmContactIgnored`: down(0, 10, 10, 0, major = 80f); tick(4000) → `Idle`.
  - `palmGrowingDuringHoldCancels`: down with major 10, then move with major 80 → `Idle`.
  - `pointersOutsideZoneNeverStartHold`: downs at (500, 500), (50, 300), (300, 50); tick(10_000) → `Idle`.
  - `extraPointersDoNotDisturbHold`: hold pointer 0 in the zone; pointer 1 goes down, moves and goes up at (500, 500) during the hold; tick(3000) → `Confirming`.
  - `tapOnConfirmUnlocks`: reach Confirming; down(1, 500, 450, 3100), up(1, 500, 450, 3200) → `Unlocked`.
  - `tapOutsideConfirmDoesNothing`: reach Confirming; tap at (100, 900) → still `Confirming`.
  - `confirmingTimesOutToIdle`: reach Confirming at 3000; tick(8000) → `Idle`.
  - `resetReturnsToIdle`: from `Unlocked`, call `reset()` → `Idle`.
- [ ] **Step 2:** Run `.\gradlew.bat testDebugUnitTest --tests "*UnlockGestureDetectorTest"`. Expected: compilation FAIL, because `UnlockGestureDetector` doesn't exist yet.
- [ ] **Step 3:** Implement `UnlockGestureDetector` to the rules above.
- [ ] **Step 4:** Run the same command. Expected: 12 tests PASS.
- [ ] **Step 5:** Commit: `feat: unlock gesture state machine`.

---

### Task 3: Picture transform math and encoding (pure Kotlin)

**Files:**
- Create: `app/src/main/java/com/tracinglightbox/trace/PictureTransform.kt`
- Test: `app/src/test/java/com/tracinglightbox/trace/PictureTransformTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class PictureTransform(
    val offsetXFrac: Float = 0f, val offsetYFrac: Float = 0f,
    val scale: Float = 1f, val rotationDeg: Float = 0f, val mirrored: Boolean = false)
fun PictureTransform.applyGesture(panXPx: Float, panYPx: Float, zoom: Float, rotationDeltaDeg: Float,
                                  containerWidthPx: Float, containerHeightPx: Float): PictureTransform
fun PictureTransform.toggleMirror(): PictureTransform
fun PictureTransform.offsetPx(containerWidthPx: Float, containerHeightPx: Float): Pair<Float, Float>
fun PictureTransform.encode(): String            // "x;y;scale;rot;0|1"
fun decodePictureTransform(s: String?): PictureTransform?   // null on null or malformed input
```
"Fit" is `PictureTransform()`. The rotation is normalized to (-180, 180].

- [ ] **Step 1: Write the failing tests:**
  - `panIsStoredAsFraction`: identity.applyGesture(100f, 50f, 1f, 0f, 1000f, 500f) → offsetXFrac 0.1, offsetYFrac 0.1.
  - `offsetsAreFractionsOfContainer`: a transform with offset fractions (0.1, 0.2) gives `offsetPx(1200f, 2000f) == (120f, 400f)` and `offsetPx(2000f, 1200f) == (200f, 240f)`.
  - `zoomMultipliesAndClamps`: scale 1 × zoom 2 → 2. Scale 6 × zoom 2 → 8. Scale 0.3 × zoom 0.5 → 0.25.
  - `rotationNormalizes`: 170 + 30 → -160. -170 - 30 → 160. 180 stays 180.
  - `mirrorToggles`: `toggleMirror()` twice → the original.
  - `encodeDecodeRoundTrip`: a transform with all fields non-default survives `decode(encode())`.
  - `decodeRejectsGarbage`: `null`, `""`, `"1;2;3"` and `"a;b;c;d;e"` → null.
- [ ] **Step 2:** Run `.\gradlew.bat testDebugUnitTest --tests "*PictureTransformTest"`. Expected: compilation FAIL.
- [ ] **Step 3:** Implement the functions in `PictureTransform.kt`.
- [ ] **Step 4:** Run the same command. Expected: 7 tests PASS.
- [ ] **Step 5:** Commit: `feat: picture transform math and encoding`.

---

### Task 4: Picture catalog, manifest, and the tiger

**Files:**
- Create: `app/src/main/java/com/tracinglightbox/library/PictureCatalog.kt`
- Create: `app/src/main/assets/pictures/manifest.json`, `app/src/main/assets/pictures/tiger.svg`
- Test: `app/src/test/java/com/tracinglightbox/library/PictureCatalogTest.kt`
- Modify: `README.md` (add a "Adding pictures" section: drop the file in `assets/pictures/`, add a manifest line, rebuild; the picture rules from the spec: pure black lines, thick enough to see through paper, pure white background, free-to-use or original only)

**Interfaces:**
- Produces:
```kotlin
@Serializable data class Picture(val id: String, val name: String, val file: String)
fun parseManifest(json: String): List<Picture>
fun loadCatalog(context: Context): List<Picture>   // reads assets/pictures/manifest.json
fun Picture.assetUri(): String                       // "file:///android_asset/pictures/<file>"
```
The manifest format is `{"pictures":[{"id":"tiger","name":"Tiger","file":"tiger.svg"}]}`. The allowed extensions are `.svg`, `.png`, `.jpg` and `.jpeg`, matched case-insensitively.

- [ ] **Step 1: Write the failing tests:**
  - `parsesValidManifest`: two entries → two `Picture`s, in order.
  - `skipsEntryMissingField`: an entry without `file` is dropped and the others are kept.
  - `skipsUnsupportedExtension`: `"x.gif"` is dropped, and `"X.PNG"` is kept.
  - `duplicateIdKeepsFirst`: two entries with id `"a"` → one result, the first.
  - `brokenJsonGivesEmptyList`: `"{not json"` → `emptyList()`.
- [ ] **Step 2:** Run `.\gradlew.bat testDebugUnitTest --tests "*PictureCatalogTest"`. Expected: compilation FAIL.
- [ ] **Step 3:** Implement with kotlinx-serialization (`Json { ignoreUnknownKeys = true }`). Decode entries as `JsonObject`s so that one bad entry doesn't fail the whole file.
- [ ] **Step 4:** Run the same command. Expected: 5 tests PASS.
- [ ] **Step 5: Draw `tiger.svg`.** This is an original drawing: `viewBox="0 0 1000 1000"`, a full white `<rect>` background, and every shape `fill="none" stroke="#000" stroke-width="14" stroke-linecap="round" stroke-linejoin="round"`. Contents: a big round friendly head with two rounded ears (inner-ear curves), large round eyes with pupils, a rounded nose, a smiling "w" mouth, three whiskers per side, three forehead stripes and two cheek stripes per side, a simple sitting body with front paws (toe lines) and a curled tail with three stripes. No fills, no shading, nothing smaller than about 25 units. Add `{"id":"tiger","name":"Tiger","file":"tiger.svg"}` to the manifest.
- [ ] **Step 6:** Verify: `.\gradlew.bat assembleDebug` succeeds. Render the SVG to PNG in a browser and check by eye that it reads as a friendly tiger with bold, traceable lines.
- [ ] **Step 7:** Commit: `feat: picture catalog and tiger outline`.

---

### Task 5: Library and Trace screens (unlocked), with persistence

**Files:**
- Create: `app/src/main/java/com/tracinglightbox/AppViewModel.kt`
- Create: `app/src/main/java/com/tracinglightbox/trace/TransformStore.kt`
- Create: `app/src/main/java/com/tracinglightbox/ui/LibraryScreen.kt`, `app/src/main/java/com/tracinglightbox/ui/TraceScreen.kt`, `app/src/main/java/com/tracinglightbox/ui/PictureView.kt`
- Modify: `MainActivity.kt` (host `App()`; choose the screen from ViewModel state, with no navigation library; system Back on Trace (unlocked) returns to Library)

**Interfaces:**
- Consumes: `PictureTransform` and its functions (Task 3); `Picture`, `loadCatalog`, `assetUri` (Task 4).
- Produces:
```kotlin
class TransformStore(context: Context) {   // DataStore Preferences "tracing"
    suspend fun load(pictureId: String): PictureTransform?   // key "t_<id>", via decodePictureTransform
    suspend fun save(pictureId: String, t: PictureTransform)
    suspend fun lastPictureId(): String?                     // key "last_picture"
    suspend fun setLastPictureId(id: String)
}
class AppViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    val pictures: List<Picture>
    val currentPicture: StateFlow<Picture?>     // null = Library screen
    val transform: StateFlow<PictureTransform>
    val locked: StateFlow<Boolean>              // backed by SavedStateHandle key "locked" (used in Task 6)
    fun openPicture(p: Picture); fun closePicture()
    fun onGesture(panX: Float, panY: Float, zoom: Float, rot: Float, w: Float, h: Float)
    fun fit(); fun toggleMirror()
    fun setLocked(value: Boolean)
}
@Composable fun PictureView(picture: Picture, transform: PictureTransform, modifier: Modifier)
```
On start, if `lastPictureId()` exists in the catalog, open it. Save the transform (debounced about 300 ms) and the last picture id whenever they change.

- [ ] **Step 1:** Implement `TransformStore` and `AppViewModel`.
- [ ] **Step 2:** Implement `PictureView`: a white full-size `Box`, then a Coil `AsyncImage` (`SvgDecoder.Factory()` registered in an app `ImageLoader`), `ContentScale.Fit`, and a `graphicsLayer` applying `offsetPx`, `scale`, `rotationZ`, and `scaleX = -scale` when mirrored. Request rasterization at 2× the layout size so zooming stays sharp.
- [ ] **Step 3:** Implement `LibraryScreen`: an adaptive grid (minimum cell 220dp) of large rounded cards, each with a thumbnail and the picture name. Tap opens the picture.
- [ ] **Step 4:** Implement `TraceScreen` (unlocked): `PictureView` with `detectTransformGestures` → `onGesture`, plus a top bar with Back, Fit and Mirror and a large **Lock** button bottom-right (minimum 72dp tall). Lock calls `setLocked(true)`, which Task 6 wires up.
- [ ] **Step 5:** Verify: `.\gradlew.bat assembleDebug testDebugUnitTest` succeeds. Install on the tablet or an emulator (`adb install -r app\build\outputs\apk\debug\app-debug.apk`). Check: the library shows Tiger; tapping it opens the picture on white; drag, pinch and rotate work; Fit resets; Mirror flips; after force-stopping and relaunching, the same picture opens in the same place; rotating the tablet keeps the picture in the same relative position.
- [ ] **Step 6:** Commit: `feat: library and trace screens with saved position`.

---

### Task 6: Lock mode (touch blocking, screen, unlock overlay)

**Files:**
- Create: `app/src/main/java/com/tracinglightbox/lock/LockController.kt`
- Create: `app/src/main/java/com/tracinglightbox/ui/UnlockOverlay.kt`
- Modify: `MainActivity.kt`, `TraceScreen.kt`

**Interfaces:**
- Consumes: `UnlockGestureDetector`, `UnlockConfig`, `PointerSample`, `UnlockState`, `Box` (Task 2); `AppViewModel.locked` / `setLocked` (Task 5).
- Produces:
```kotlin
class LockController(private val activity: ComponentActivity) {
    fun applyLocked()     // keep-screen-on flag, brightness 1.0f, hide system bars (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE), SCREEN_ORIENTATION_LOCKED, startLockTask()
    fun applyUnlocked()   // clear all of the above, restore brightness to BRIGHTNESS_OVERRIDE_NONE, SCREEN_ORIENTATION_UNSPECIFIED, stopLockTask()
}
@Composable fun UnlockOverlay(state: UnlockState, confirmButton: Box, onDrawn: () -> Unit = {})
```

- [ ] **Step 1:** In `MainActivity`, override `dispatchTouchEvent(ev)`. When `locked` is false, call `super`. When it's true, convert every pointer in `ev` to a `PointerSample` (DOWN for `ACTION_DOWN`/`ACTION_POINTER_DOWN` at the action index; MOVE for every pointer on `ACTION_MOVE`; UP for `ACTION_UP`/`ACTION_POINTER_UP`; CANCEL for every pointer on `ACTION_CANCEL`), feed them to the detector, and **return `true` without calling `super`.** Build `UnlockConfig` from the window size: zone 72dp in px; `maxTouchMajorPx` = 20 mm at `displayMetrics.xdpi`; confirm button 320×120dp, centered.
- [ ] **Step 2:** While the detector is Holding or Confirming, tick it every 50 ms from a `Handler` on the main looper, and stop ticking in Idle. When it reaches Unlocked: `setLocked(false)`, then `detector.reset()`.
- [ ] **Step 3:** Collect `locked` in `onCreate`/`onResume` and call `applyLocked()` or `applyUnlocked()`. That way recreation and returning from the background re-apply the lock. `startLockTask()` is wrapped in try/catch so a refusal can't crash the app; touch blocking still applies.
- [ ] **Step 4:** Implement `UnlockOverlay`, drawn only while the detector isn't Idle: a progress ring (96dp, stroke 10dp, dark grey) in the top-left zone when `showRing`, and a large "Unlock" button visual with the remaining seconds while Confirming. It's purely visual; the taps are handled in `dispatchTouchEvent`. `TraceScreen` hides its toolbar and Lock button while locked.
- [ ] **Step 5:** Verify the build: `.\gradlew.bat assembleDebug testDebugUnitTest` succeeds.
- [ ] **Step 6: Verify on the tablet** (screen pinning fallback for now). Lock the tiger, then: drag, pinch and palm-press all over → nothing moves; screen stays on past the timeout; brightness at max; status and navigation bars hidden; rotating the tablet doesn't rotate the app; slow tracing into the corner for 3 s brings up Unlock, and without a tap it goes away after 5 s; holding the corner for 3 s and tapping Unlock unlocks, and the toolbar, brightness and orientation come back; power off and on → still locked.
- [ ] **Step 7:** Commit: `feat: lock mode with corner-hold unlock`.

---

### Task 7: Device-owner kiosk lock and README provisioning

**Files:**
- Create: `app/src/main/java/com/tracinglightbox/admin/KioskAdminReceiver.kt` (`DeviceAdminReceiver`, empty body)
- Create: `app/src/main/res/xml/device_admin.xml` (`<device-admin><uses-policies/></device-admin>`)
- Modify: `AndroidManifest.xml` (register the receiver with `BIND_DEVICE_ADMIN`, meta-data `android.app.device_admin`, intent filter `DEVICE_ADMIN_ENABLED`)
- Create: `app/src/debug/AndroidManifest.xml` (sets `android:testOnly="true"` on `<application>` so device owner can be removed again over adb; installs then need `adb install -t`)
- Modify: `LockController.kt`, `TraceScreen.kt`, `README.md`

**Interfaces:**
- Consumes: `LockController` (Task 6).
- Produces: `LockController.isKiosk: Boolean` (`DevicePolicyManager.isDeviceOwnerApp(packageName)`).

- [ ] **Step 1:** In `applyLocked()`, when `isKiosk` is true and before `startLockTask()`: `setLockTaskPackages(admin, arrayOf(packageName))`, then `setLockTaskFeatures(admin, LOCK_TASK_FEATURE_NONE)`. With that there's no status bar, notifications, Home, Recents or global actions, and no confirmation dialog.
- [ ] **Step 2:** In `TraceScreen` (unlocked), when `!isKiosk`, show a small dismissible note once (remembered in DataStore key `kiosk_note_dismissed`): "Not set up as a kiosk yet — see README to stop exits from pinning."
- [ ] **Step 3:** Add a README section, "Set up a tablet (kiosk mode)": (1) factory reset, or remove every Google and Samsung account in Settings → Accounts; (2) enable Developer options and USB debugging; (3) `adb install -t -r app-debug.apk`; (4) `adb shell dpm set-device-owner com.tracinglightbox/.admin.KioskAdminReceiver`; (5) accounts can be added back afterward. Also add "Undo kiosk mode": `adb shell dpm remove-active-admin com.tracinglightbox/.admin.KioskAdminReceiver` (works because the debug build is `testOnly`), or a factory reset. And a "Build and install" section.
- [ ] **Step 4:** Verify the build: `.\gradlew.bat assembleDebug testDebugUnitTest` succeeds, and the manifest still has no `uses-permission`.
- [ ] **Step 5: Verify on the tablet** (needs the user, since the tablet has to be account-free): provision, then lock. No confirmation dialog; Back+Recents hold, the swipe-up-and-hold gesture, the Edge panel swipe and S Pen Air Command all do nothing; the notification shade won't open; the corner-hold plus Unlock is the only way out. Then repeat Task 6 Step 6.
- [ ] **Step 6:** Commit: `feat: device-owner kiosk lock and setup guide`, then `git push`.
