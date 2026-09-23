# Native Android project

Open **this directory** in Android Studio Panda. The app is Kotlin + Jetpack Compose, with Room, WorkManager, Credential Manager and Firebase Auth. No Flutter SDK is needed.

Use the Gradle JDK bundled with Android Studio (JBR 21); compilation targets JVM 17. Install SDK 36, sync Gradle, select `app`, and Run on an API 26+ device/emulator. Without Firebase config, choose local mode.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

See [project README](../README.md), [Firebase setup](../docs/FIREBASE.md) and [release guide](../docs/RELEASE.md).
