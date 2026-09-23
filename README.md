# Personal Asset Passport — Kotlin Android

**Everything You Own. Protected. Organized.**

A native Android MVP built with **Kotlin + Jetpack Compose**, ready to open in **Android Studio Panda**. Room/SQLite keeps inventory offline. Optional Google sign-in uses Credential Manager + Firebase Authentication. The existing .NET 8/PostgreSQL backend remains available.

## Open in Android Studio Panda

1. Choose **File → Open** and select `D:\GITHUB\Personal-Asset-Passport\android`.
2. Use the bundled Gradle JDK (JBR 21). Let Gradle sync and install Android SDK 36 if requested.
3. Select the `app` configuration and an Android 8.0/API 26 or newer device/emulator.
4. Run. Choose **Continue on this device** before Firebase is configured.

No Flutter SDK is required for the native project. `mobile/` preserves the earlier Flutter implementation as a legacy reference; it is not the current Android application. Native and legacy local databases/backups are different formats; no automatic conversion is provided.

## Google Firebase login

Application ID: **`com.personalassetpassport.app`**.

Create a Firebase project, register this Android package, enable Google Authentication, add debug/release SHA fingerprints, and place the downloaded configuration at **`android/app/google-services.json`**. The file is ignored by Git. Re-sync and rebuild. Full instructions: [FIREBASE.md](docs/FIREBASE.md).

The Google Services Gradle plugin is applied only when the real config exists. Missing configuration produces a clear in-app message and does not block local mode. Firebase manages its session and token refresh. The app never writes OAuth tokens to its Room database or backups.

Login does not upload inventory. The local vault belongs to this installation and remains the same when Google accounts change. Account-scoped cloud storage/sync is a later phase.

## Implemented in Kotlin

- Short onboarding, Google login/cancellation/logout, persistent Firebase session, optional HTTPS server-session verification.
- Room asset CRUD with UUIDs, categories, purchase details, exact money values, photos, warranty and return dates.
- Dashboard, totals separated by currency, local search, category filters and sorting.
- Copies of JPG/PNG/PDF/UTF-8 text documents in app-private storage; open/share via FileProvider and system chooser.
- Custom reminders persisted locally and scheduled with WorkManager; notification permission handling and rescheduling from Settings.
- Transactional activity timeline and soft deletion.
- Password-encrypted local export/restore including attachments: AES-256-GCM, PBKDF2-HMAC-SHA256 (600,000 iterations), random salt/nonce, authenticated transactional restore. Maximum 32 MB attachments.
- Ten active assets, English and Indonesian Android resources, locale-aware date/currency formatting.
- .NET 8 API, PostgreSQL migration, Docker setup, API and release documentation, automated tests and CI configuration.

## Build and test

```powershell
cd android
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# On an attached clean English-language test device:
.\gradlew.bat :app:connectedDebugAndroidTest
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`.

```powershell
# From repository root:
dotnet test PersonalAssetPassport.sln
Copy-Item .env.example .env
# Fill database password, Firebase project and external server credential path.
docker compose up --build
```

The API listens on localhost port 8080 in Compose. Put a trusted TLS proxy in front of it for mobile access. See [deployment](docs/DEPLOYMENT.md) and [API](docs/API.md).

## Scope and release status

This is an incremental MVP, not the entire production product in the original brief. OCR/AI, cloud synchronization, family sharing, subscriptions, maintenance/repair, insurance and resale are not implemented. No dummy successful endpoint or paid upgrade is exposed. Manual reminders are implemented; automatic multi-offset warranty scheduling is future work.

The Android app stores inventory locally. It can verify `/api/me`; server asset CRUD is independently available but is not connected as a sync engine. Local files rely on the Android sandbox/device protection, not SQLCipher. Soft-deleted data and older copied files remain until app storage is cleared; exported backups must be deleted separately. No analytics SDK is initialized.

Real Google sign-in needs your Firebase project. Production release also needs device acceptance tests, account-deletion support, publisher-specific legal/support pages, release signing and Play internal-track verification. See [release](docs/RELEASE.md) and [verification](docs/TESTING.md).

## Structure

```text
android/app/src/main/java/com/personalassetpassport/app/
  auth/           Credential Manager and Firebase
  data/           Room entities, DAO, repository, validation
  backup/         Authenticated encrypted export and restore
  notifications/  WorkManager scheduling and delivery
  ui/             Compose screens and ViewModel
android/app/src/main/res/     EN/ID resources and Android config
android/app/src/test/         JVM, Room and Compose tests
android/app/src/androidTest/  Device acceptance flow
backend/                     .NET API and HTTP integration tests
database/migrations/         PostgreSQL schema
docs/                        Setup, architecture and release guides
mobile/                      Legacy Flutter reference, superseded
```

[Architecture](docs/ARCHITECTURE.md) · [Firebase](docs/FIREBASE.md) · [API](docs/API.md) · [Privacy draft](docs/PRIVACY.md)
