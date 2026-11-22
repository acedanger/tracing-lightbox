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
