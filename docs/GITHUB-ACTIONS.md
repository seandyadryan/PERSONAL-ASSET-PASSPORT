# GitHub Actions

Repository: https://github.com/seandyadryan/PERSONAL-ASSET-PASSPORT

The `Build and Test` workflow uses GitHub-hosted Ubuntu 24.04 runners. No runner service or registration token is needed on your computer. It runs for pushes, pull requests and the manual **Actions → Build and Test → Run workflow** button.

Two jobs run independently:

- Backend tests with .NET 8, with TRX results uploaded.
- Android with JDK 21, SDK 36 and NDK 28.2.13676358: debug APK, device-test APK compilation, JVM tests and lint. The debug APK and test reports are uploaded as artifacts for 14 days.

Download the installable APK from **Actions → completed successful run → Artifacts → personal-asset-passport-debug**. This is a debug build, not a Play release. The workflow does not publish to Google Play or deploy the backend.

Firebase configuration is not required for the initial local-mode build. `google-services.json`, signing keys and service accounts stay excluded from Git. Real Google login needs Firebase configuration and the SHA fingerprints of the certificate used by that build; the local debug certificate differs from the runner's debug certificate. A stable CI signing/Firebase configuration can be added when that project exists.

The workflow compiles the device tests but does not run an emulator. Device notification, picker and Google OAuth acceptance checks are still required before release.
