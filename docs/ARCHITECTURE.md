# Native Kotlin architecture

The current mobile client is `android/`, built with Kotlin, Jetpack Compose, ViewModel/StateFlow, Room and WorkManager. Open it directly in Android Studio Panda. The earlier Flutter source remains in `mobile/` as a legacy reference and is not a dependency.

## Data flow

Compose → ViewModel → repository → Room/SQLite and app-private attachment files. All mutations run in coroutines. Room flows update UI; transactions keep assets and timeline events consistent. A repository mutex coordinates writes with backup operations. Google identity is an independent optional service. The local vault belongs to the installation, not the currently signed-in account.

## Native schema

Room schema version 1 is exported under `android/app/schemas`:

- Asset: UUID, product fields, purchase price in integer hundredths, ISO calendar dates, photo filename, created/updated/deleted timestamps.
- Document: UUID, asset FK, display name, generated internal filename, detected MIME, byte size, creation time. Binary data lives outside Room.
- AssetEvent: UUID, asset FK, event kind and creation time.
- Reminder: UUID, asset FK, title, due instant, delivery flag and creation time.

Foreign keys are indexed and cascade on hard deletion. Normal deletion is soft, logs an event and removes reminders. Backups can retain soft-deleted assets and old files. The exported version-1 schema is the migration baseline; no destructive migration fallback is enabled.

Dates use LocalDate for warranty/return boundaries, UTC epoch milliseconds for audit/reminder instants. Currency formatting follows locale. Totals are separated by currency and represent purchase value, not current valuation. Category/currency enums are validated at the repository boundary.

## Authentication

Credential Manager obtains a Google ID token; Firebase Auth exchanges it for a Firebase session and manages refresh/persistence. No token is stored in Room or backup. The optional HTTPS `/api/me` check obtains a fresh Firebase token through SDK APIs. Assets are never automatically sent during login.

## Documents, reminders and backup

System document picker avoids broad storage permissions. Imports are bounded to 10 MB, MIME-sniffed and copied under generated names. Photos are downsampled and recompressed. FileProvider grants temporary URI read access only through explicit open/share actions.

WorkManager persists scheduling across process death/reboot. It is inexact and battery/device settings can delay delivery. Permission denial leaves the reminder visible in the app; Settings can reschedule it. A worker rechecks asset/reminder existence before showing a notification, and uses private lock-screen visibility.

Backups use AES-256-GCM authenticated encryption with random salt/nonce and PBKDF2-HMAC-SHA256 (600,000 iterations). Heavy processing runs on Dispatchers.IO. Restore authenticates and validates the payload, writes fresh attachment filenames, then replaces database records in one transaction. Invalid password/content leaves the current inventory intact. Native backup format PAP-KOTLIN version 1 is not compatible with earlier Flutter archives.

## Server and future phases

The .NET 8/PostgreSQL API retains verified UID ownership, pagination, free limit, soft deletion and concurrency versions. Household/member tables reserve future roles, but sharing is not enabled. Purchase/warranty/return fields are embedded in the initial asset aggregate. API data and native local data are separate until an explicit opt-in sync engine exists.

Future phases add account-scoped vault adoption, outbox/idempotency/conflicts, signed object-storage URLs, OCR/AI provider interfaces, maintenance/repair, insurance/resale, purchase verification and configurable pricing. These are not represented by fake implementations or unused empty interfaces.

## Remaining risks

Real OAuth package/SHA configuration and notifications need device testing. Files rely on Android device encryption/sandbox, not app-level SQLCipher encryption. Backup passwords cannot be recovered. No cloud upload/analytics occurs by default. Account deletion, actual publisher policies, support and Play validation are release prerequisites. No startup/search performance benchmark or full security audit has been certified.
