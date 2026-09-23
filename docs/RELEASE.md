# Kotlin Android release preparation

Open `android/` in Android Studio Panda. The project uses AGP 8.11.1, Gradle 8.14, Kotlin/Compose compiler 2.2.20, compile/target SDK 36 and min SDK 26. Android 8.0 is the minimum chosen for native java.time and crypto support. The bundled IDE JBR 21 runs Gradle; JVM source/bytecode target is 17.

Application ID: **com.personalassetpassport.app**. Create a dedicated upload keystore outside the repository. Copy `android/key.properties.example` to the ignored `android/key.properties` and fill your real signing values. The release configuration never falls back to the debug signing key.

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:bundleRelease
```

A release must be signed with your upload key before submission. With no key.properties, Gradle can create unsigned outputs; they are not a Play release. Add upload and Play App Signing certificate fingerprints to Firebase. Verify a Play internal-track installation.

Before production:

- Configure the actual Firebase project and verify Google success/cancel/network failure, persistence, logout and revoked sessions on a real device.
- Run connectedDebugAndroidTest on a clean English-language emulator; complete notification, document-picker, reboot, offline restart and backup checks in TESTING.md.
- Test TalkBack, large fonts, small screens, low storage and battery restrictions. No performance benchmark is certified.
- Add account deletion support and verified publisher/support/deletion URLs before offering account creation in production. Current app supports logout, not account deletion.
- Finalize the privacy/terms drafts with your legal entity and processing details. Complete Play Data Safety for the SDKs actually shipped.
- Prepare store screenshots, a 1024×500 feature graphic and 512×512 store icon. The launcher includes a vector passport mark; test renders are not final store screenshots.
- Keep cloud sync, OCR/AI, subscriptions, insurance and resale absent from the listing until implemented.
- Recheck [Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878) at submission, audit dependency updates, and validate PostgreSQL operations before hosting the API publicly.

Draft name: Personal Asset Passport

Draft short description: Organize your assets, receipts, warranties and reminders in one place.

Draft full description: Keep important details about the things you own together. Create a passport for electronics, appliances, furniture and other valuables. Store purchase details, photos, receipts and documents. Track warranty and return dates and add reminders. Search by name, brand, serial number or store. Your inventory works offline, with optional Google sign-in. Export a password-encrypted backup including documents. This initial release supports ten active assets. Cloud synchronization, OCR and paid upgrades are not yet available.

No support email, legal identity, real signing credential or Firebase project has been invented.
