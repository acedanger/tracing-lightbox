# Tracing Lightbox — Design

Date: 2026-11-22
Status: Ready for approval

## Intent and the why

A private Android app that turns a phone or tablet into a tracing lightbox for the
owner's 6-year-old daughter. The app shows a black-on-white outline picture; a sheet of
paper is laid directly on the screen, and she traces the lines showing through with a
pencil.

The owner already tried a web app for this. It failed because the pencil and the paper
pressing on the screen registered as touches, and the image kept moving. **The whole
point of this app is that once the picture is locked, nothing a child, a pencil, a palm,
or a sheet of paper does to the screen can move it, change it, or leave the app.** A
native app was chosen specifically because it can control touch handling, screen
sleep, brightness, and app exit far better than a web page.

## Users

- **The daughter (6):** picks a picture, traces, and can lock and unlock it herself.
  The controls must be simple enough for her to use alone.
- **The parent (owner):** may help pick or fit the picture. Installs the app on their
  own devices. Unlocking is **not** a parent-only action.

## Goals

1. Show a bundled outline picture full-screen, as dark lines on a bright white
   background, so it shines clearly through ordinary paper.
2. Before locking, let the user position the picture: drag to move, pinch to zoom,
   two-finger rotate, plus a "reset to fit" button and a "mirror" toggle.
3. **Lock mode** that, once on:
   - ignores every touch on the screen (single, multi-touch, palm, pencil pressure
     through paper, long presses, swipes), except for the parent unlock gesture;
   - keeps the screen on (no dimming, no sleep);
   - forces screen brightness to maximum for the app window;
   - hides the status and navigation bars (immersive mode);
   - locks the current screen orientation, so tilting the device doesn't re-lay out
     the picture;
   - uses Android **screen pinning** (`startLockTask()`) so Back, Home, and Recents
     don't take her out of the app.
4. An **unlock gesture** that anyone, including the daughter, can do on purpose, but
   that tracing won't trigger by accident: press and hold a specific corner (top-left)
   for 3 seconds, with a visible progress ring showing up after about 1 second so
   she can see it working. Releasing early cancels. It is not meant to keep her out.
   *(Our call, to guard against a palm or slow tracing resting in the corner:)* very
   large contacts (palms, side of hand) don't count toward the hold, and finishing the
   hold doesn't unlock straight away. It shows a big **Unlock** button in the middle of
   the screen for 5 seconds, and one tap on it unlocks. If nobody taps it, it
   disappears and the picture stays locked.
5. A built-in **picture library**: a grid of large thumbnails, easy for a child to tap.
   Pictures ship inside the app.
6. For development: one original, child-friendly **cartoon tiger** outline, drawn for
   this project (no third-party art).
7. Remember the last-used picture and its position/zoom/rotation, so reopening the
   app gets back to the same setup.
8. Works fully offline. Built for the Galaxy Tab S6 Lite tablet.

## Non-goals

- No camera / camera-overlay "AR" mode. (Considered and explicitly set aside: the
  target is paper-on-the-screen.)
- No importing pictures from the device, the web, or the camera at runtime. New
  pictures are added by the developer and the app is rebuilt.
- No Play Store listing, accounts, analytics, ads, in-app purchases, or network access.
- No iOS or web version.
- No in-app drawing or coloring on the screen.
- No photo-to-outline conversion (pictures are already outlines).

## Anti-goals (failure even if it "works")

- The picture moves, zooms, rotates, or changes in any way while locked.
- The screen dims or sleeps during a tracing session.
- The picture unlocks, or the app is left, by accident while tracing (unlocking
  on purpose is fine).
- The app asks for internet or any other permission it doesn't need.
- Lines too faint to see through paper.

## Constraints

- Android only; sideloaded via USB / APK by the owner onto their own devices.
- **Target device: Samsung Galaxy Tab S6 Lite** (10.4", 2000×1200, landscape or
  portrait), dedicated to the daughter; there may be more than one. Depending on the
  model year it runs Android 12–14 / One UI. Phones are no longer a target, though
  nothing should stop the app from running on one.
- Samsung extras to account for during testing: the Edge panel swipe, the S Pen
  Air Command button, and One UI's own gesture settings. Kiosk lock task should
  suppress these; confirm on the device.
- Dev machine: Windows 11, with Android Studio and the Android SDK installed. Java is
  not on PATH; use Android Studio's bundled JDK (JBR) for command-line Gradle builds.
  `adb` is in the SDK's `platform-tools`, not on PATH.
- Source control: a new local git repo, pushed to a **private** GitHub repository under
  the owner's account using the GitHub CLI (`gh`, already signed in).
- Pictures added later must be free to use (public domain / CC0 / explicit permission)
  or drawn for the project. Many coloring-page sites forbid redistribution, even in a
  private app.

## Approaches considered

| Approach | Trade-off | Verdict |
|---|---|---|
| **Native Android, Kotlin + Jetpack Compose** | Direct control of touch dispatch, keep-screen-on, window brightness, immersive mode, orientation lock, and screen pinning. Small APK, standard Android Studio build. Android-only. | **Chosen.** The lock is the heart of the app, and native does it best. |
| Flutter | Could later target iPad, but lock / pinning / brightness still need Android-specific code; adds a second toolchain for no benefit. | Rejected. |
| Web app / PWA | No install, but can't reliably block touches at the OS level, block Home/Back, force brightness, or stop sleep. The owner already tried this and the image moved. | Rejected. |

The owner chose native Android.

## Decided design

### Screens

```
Library                        Trace (unlocked)                 Trace (locked)
+----------------------+       +----------------------+         +----------------------+
|  Pick a picture      |       | [<] [Fit] [Mirror]   |         |                      |
| +------+ +------+    |       |                      |         |      (just the       |
| | tiger| |  ... |    |  -->  |     outline image    |  -->    |       picture,       |
| +------+ +------+    |       |   drag/pinch/rotate  |         |      full screen,    |
|                      |       |                      |         |      max bright)     |
|                      |       |          [ LOCK ]    |         |                      |
+----------------------+       +----------------------+         +----------------------+
```

- **Library:** grid of large thumbnail cards; tapping opens Trace with that picture.
- **Trace, unlocked:** picture fills the screen on white. Toolbar: Back to library,
  Fit (reset transform), Mirror (horizontal flip), and a large **Lock** button.
  Gestures move / zoom / rotate the picture.
- **Trace, locked:** all UI hidden, just the picture on white. Nothing marks the
  unlock corner (*our call*: it would show through the paper and get traced). The
  progress ring and the Unlock button appear only during the unlock gesture. Only the
  unlock gesture does anything. On unlocking: exit pinning, restore system bars, brightness,
  and orientation, and return to Trace (unlocked) with the toolbar visible.

### How the lock works (the critical part)

- Touch blocking happens at the **Activity level**: when locked, override
  `dispatchTouchEvent` so no event reaches the Compose UI. The Activity watches the raw
  events only to detect the unlock gesture. This is deliberately below Compose, so no
  stray gesture detector anywhere in the UI can react.
- Unlock detection: one pointer that goes down inside the top-left corner zone (about
  72dp square), stays inside it, and is held for 3 seconds. If a second pointer goes
  down, the pointer leaves the zone, or it lifts early, the gesture is cancelled. It
  should still work if other fingers or a palm are resting elsewhere on the screen.
  (Builder may refine.)
- Window flags while locked: `FLAG_KEEP_SCREEN_ON`; window `screenBrightness = 1.0f`;
  immersive sticky system bars hidden; orientation pinned to the current one.
- **Kiosk lock via device owner (primary).** The tablets are dedicated to her, so the
  app is provisioned as **device owner** (a `DeviceAdminReceiver`, set with
  `adb shell dpm set-device-owner …` on a tablet with no accounts on it). The app
  allowlists itself for lock task and calls `startLockTask()` on Lock, with lock-task
  features set so the status bar, notifications, Home, Recents and global actions are
  all off. That gives no confirmation dialog and no system exit gesture. The only
  way out is the app's own unlock. The power button still turns the screen off; that's
  acceptable.
- **Fallback: plain screen pinning.** If the app isn't device owner (for example, a
  fresh install before provisioning), `startLockTask()` falls back to ordinary
  pinning, with Android's confirmation and exit gesture. Touch blocking, keep-awake
  and brightness still work. The app shows a small one-time note in the unlocked
  screen saying it's not set up as a kiosk yet.
- The README gives step-by-step provisioning for the tablet: factory reset (or remove
  every Google/Samsung account), enable USB debugging, install the APK, run the
  `dpm set-device-owner` command, then add accounts back if wanted. It also covers how
  to remove device owner later.
- Lock state should survive configuration changes and the app being briefly
  backgrounded (for example, a notification shade the child somehow pulls down).

### Picture library

- Pictures live in the app bundle (for example `assets/pictures/`), with a small
  manifest (id, display name, file) so adding a picture is "drop in a file, add a line,
  rebuild".
- Supported formats: SVG (crisp at any zoom, preferred for drawn art) and PNG/JPG (for
  sourced coloring pages).
- Display: always rendered as dark lines on pure white, full brightness. Mirror is a
  display transform.
- *(Our call:)* there's no image cleanup inside the app. Every bundled picture is
  prepared before it's added: pure black lines, thick enough to see through paper,
  on a pure white background. When pictures are sourced later, the developer converts
  them to this standard. The README documents the rules for preparing pictures.

### The development tiger

- An original cartoon tiger outline, drawn as SVG for this project: big friendly head,
  simple body, bold stripes, thick uniform black strokes on white, no fills or shading,
  no tiny details (sized for a 6-year-old tracing with a pencil).

### Persistence

- Remember last picture id plus transform (offset, scale, rotation, mirror) per picture,
  in local preferences (for example DataStore).

### Platform targets

- Kotlin, Jetpack Compose, single Activity.
- minSdk 28 (Android 9, which covers every Tab S6 Lite and gives the lock-task
  features API), targetSdk / compileSdk current stable. *(Our call.)*
- No `INTERNET` permission, no other permissions.

### Repository

- New local repo (proposed name `tracing-lightbox`), Gradle Kotlin DSL, a `.gitignore`
  suitable for Android, a README with build and sideload steps (including how to turn
  on App pinning).
- Private GitHub repo created and pushed with `gh repo create --private`.

## Left to the builder

- Exact library choices (image loading, SVG rendering, gesture math), module layout,
  and architecture inside the app.
- Visual styling of the Library and toolbar (keep it big, simple, and friendly).
- Exact corner-zone size and progress-ring look for the unlock gesture.
- Test strategy: unit tests for the unlock-gesture state machine and the transform
  math are expected. The lock behaviour itself needs on-device verification by the
  owner (pencil and paper on a real screen).
