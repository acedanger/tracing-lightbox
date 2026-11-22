# Tracing Lightbox

A private Android app that turns a tablet into a tracing lightbox. Pick an outline
picture, fit it to the paper, lock it, lay paper on the screen, and trace. While
locked, no touch can move the picture.

Built for a Samsung Galaxy Tab S6 Lite. No internet, no permissions, no accounts.

## Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug testDebugUnitTest
```

The APK is at `app\build\outputs\apk\debug\app-debug.apk`.

## Adding pictures

1. Put the file in `app/src/main/assets/pictures/`. SVG (preferred), PNG, or JPG.
2. Add a line to `app/src/main/assets/pictures/manifest.json`:
   `{ "id": "lion", "name": "Lion", "file": "lion.svg" }` (ids must be unique).
3. Rebuild and reinstall.

Picture rules, so it shows clearly through paper:

- Pure black lines on a pure white background. No grey, no shading, no color fills.
- Lines thick enough to see through paper (in a 1000-unit SVG, a stroke of about 14).
- Nothing tiny: a 6-year-old traces it with a pencil.
- Only free-to-use (public domain / CC0 / explicit permission) or original art. Many
  coloring-page sites forbid copying their pages, even into a private app.

## Install on a tablet

1. On the tablet: Settings → About tablet → Software information → tap **Build number** 7
   times to turn on Developer options. Then Settings → Developer options → turn on
   **USB debugging**.
2. Connect the tablet by USB and accept the "Allow USB debugging?" prompt.
3. From the repo folder:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -t -r app\build\outputs\apk\debug\app-debug.apk
```

(`-t` is required: debug builds are marked test-only so kiosk mode can be undone.)

Without the kiosk setup below, **Lock** still blocks every touch, keeps the screen on at
full brightness and pins the app, but Android asks to confirm pinning each time and the
"swipe up and hold" gesture can unpin it.

## Set up a tablet (kiosk mode)

Kiosk mode makes **Lock** a true lock: no confirmation, no Home/Recents/Back, no
notification shade. Samsung's Edge panel and S Pen Air Command should be blocked too
(confirm on the tablet). The only way out is the app's own unlock
(hold the top-left corner for 3 seconds, then tap **Unlock**).

Android only allows this on a tablet with **no accounts** on it.

**Before turning on kiosk mode, test the unlock on this tablet without it.** Lock a
picture, then hold the top-left corner with a fingertip for 3 seconds and tap **Unlock**.
The app ignores palm-sized contacts (wider than 20 mm), and that size comes from the
tablet's touch sensor. If the ring never appears with a normal fingertip, don't set up
kiosk mode yet. Report it so the threshold can be adjusted.

1. Either factory reset the tablet and skip adding any Google or Samsung account during
   setup, or remove every account in Settings → Accounts and backup → Manage accounts.
2. Turn on USB debugging and install the app (see above).
3. Make the app the device owner:

```powershell
& $adb shell dpm set-device-owner com.tracinglightbox/.admin.KioskAdminReceiver
```

   It should print `Success: Device owner set to package ...`.
4. You can now add accounts back if you want them.

### Undo kiosk mode

```powershell
& $adb shell dpm remove-active-admin com.tracinglightbox/.admin.KioskAdminReceiver
```

A factory reset also removes it.

### If the tablet is stuck locked

If the unlock gesture ever stops working in kiosk mode, connect the tablet by USB and run:

```powershell
& $adb shell am task lock stop
& $adb shell am force-stop com.tracinglightbox
```

The first command ends Android's lock; it may print "Activity manager is not in
lockTaskMode" even when it worked. The second closes the app, so it reopens unlocked. If no computer is at hand, force a restart by holding
**Power + Volume down** for about 10 seconds; the app opens unlocked after a restart
unless it is reopened and locked again.

## Using it

1. Pick a picture. Drag to move, pinch to zoom, twist with two fingers to rotate.
   **Fit** re-centers it, **Mirror** flips it.
2. Lay the paper on the screen, line it up, then press **Lock**.
3. Trace. Nothing on the screen reacts.
4. To unlock: press and hold the top-left corner for 3 seconds (a ring fills up), then
   tap the big **Unlock** button within 5 seconds.
