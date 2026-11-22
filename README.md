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
