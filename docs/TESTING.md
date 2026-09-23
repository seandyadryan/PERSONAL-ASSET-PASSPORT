# Verification — native Kotlin

Run from `android/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Attached clean English-language Android device/emulator:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Verification completed on 23 September 2026 with Android Studio Panda JBR 21, SDK 36 and NDK 28.2.13676358: `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` and `lintDebug` all passed. The JVM suite contains 11 passing tests across DomainTest, RepositoryTest, WelcomeTest and PassportFlowTest. No Android device or emulator was attached, so `connectedDebugAndroidTest`, real notification delivery, document picker behavior and Firebase account selection remain device checks.

DomainTest checks calendar deadlines, exact locale-aware amounts, validation, MIME checks and authenticated encryption. RepositoryTest uses Room under Robolectric and verifies persistence after database close/reopen, copied documents, reminders, timeline, soft deletion, limits, path validation and backup round trips/failure preservation. Compose tests exercise onboarding actions, busy controls and local onboarding/form validation/create against Room. Test APK includes an actual device onboarding/create flow.

Run `dotnet test PersonalAssetPassport.sln` from the root for the HTTP API tests. Their SQLite database and test-only identity verifier check the authentication/ownership boundary, not actual Firebase or PostgreSQL-specific behavior.

## Manual device acceptance

1. Fresh install, EN/ID, skip login, add photo and accurately entered amount/date.
2. Attach a receipt; delete the source file and confirm the app-owned copy opens/shares using a compatible viewer.
3. Warranty/return dates: yesterday/today/tomorrow, leap year and month transitions. Check per-currency totals.
4. Add a timed reminder, allow notifications and verify delivery. Repeat after permission denial, force-stop and reboot. WorkManager is inexact and force-stop can suppress work until app launch.
5. Airplane mode: search/edit, close/reopen and confirm persistence.
6. Export an encrypted backup, try wrong password/tampered content (inventory must remain), then restore correctly. Verify attachments and reschedule pending reminders. Kotlin PAP-KOTLIN archives are not compatible with legacy Flutter archives.
7. Verify the eleventh active asset is rejected; deleting one frees a slot.
8. Configure Firebase and test real Google picker/cancel/error/session/logout/account switching. Confirm the local vault remains the same and no asset upload occurs.
9. Enter your HTTPS API origin and verify server session. Check revoked/expired Firebase credentials server-side.
10. Test release through Play internal track, accessibility, large font sizes and low-memory devices.

Device, Play OAuth and notification delivery checks cannot be replaced with a successful JVM test result. Report unexecuted checks explicitly.
