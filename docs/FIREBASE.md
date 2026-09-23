# Firebase Google login — Kotlin / Android Studio Panda

Firebase project belum dibuat pada implementasi ini. Aplikasi dapat digunakan secara lokal tanpa konfigurasi Firebase. Jangan memasukkan service-account private key ke aplikasi Android atau repository.

1. Buka [Firebase Console](https://console.firebase.google.com/) dan buat project. Analytics tidak diperlukan untuk MVP.
2. Tambahkan Android app dengan package **com.personalassetpassport.app**. Ini package native Kotlin; package Flutter lama berbeda dan tidak digunakan lagi.
3. Aktifkan **Authentication → Sign-in method → Google**, pilih support email, dan lengkapi Google Auth Platform consent configuration. Tambahkan tester bila consent masih dalam mode testing.
4. Dari terminal di folder `android`, jalankan `./gradlew.bat :app:signingReport`. Tambahkan SHA-1 dan SHA-256 debug ke Firebase. Bila debug keystore belum ada, build APK debug dulu dan ulangi signingReport.
5. Unduh ulang **google-services.json** setelah Google aktif, lalu simpan di **android/app/google-services.json**. Konfigurasi harus berisi OAuth client bertipe Web (client_type 3). Plugin menghasilkan resource `default_web_client_id`; Credential Manager membutuhkan Web client ID, bukan Android client ID.
6. Buka folder `android` di Android Studio Panda, klik **Sync Project with Gradle Files**, lalu Run pada perangkat/emulator dengan Google Play Services.
7. Uji tombol Google → account picker → pilih akun → dashboard → Settings menampilkan email → restart → sesi tetap ada → logout. Pembatalan account picker tidak dianggap sebagai login berhasil.
8. Untuk rilis, tambahkan fingerprint upload key serta **Play App Signing** sesuai sertifikat aplikasi yang dipasang. Uji melalui Play internal track.

Tidak ada API key/credential palsu di source. Google Services plugin hanya diterapkan jika file config ada, sehingga build local-only tetap berfungsi. Firebase API key adalah identitas project, bukan pengganti otorisasi. Terapkan API restrictions yang kompatibel.

### Fingerprint APK debug yang dibuat di komputer ini

Diverifikasi dengan `apksigner` pada 23 September 2026:

```text
SHA-1:   5A:FD:F9:5F:ED:85:33:1A:73:8D:A0:84:A8:A0:5F:19:86:34:4D:3E
SHA-256: 57:E3:8A:E9:5D:A2:C9:34:FF:02:8A:7A:C6:24:D1:13:D0:AF:14:00:C4:52:01:EA:D1:AC:05:18:F6:1D:98:F2
```

Gunakan nilai ini untuk APK debug yang disertakan. Build dari komputer/keystore lain atau Play App Signing membutuhkan fingerprint sertifikatnya sendiri. Fingerprint bukan private key.

## Backend opsional

`Firebase__ProjectId` harus sama dengan project Android. Gunakan Application Default Credentials/workload identity pada server. Untuk development, simpan service-account JSON di luar repository dan set `GOOGLE_APPLICATION_CREDENTIALS`; jangan kirim private key melalui chat.

Backend memakai Firebase Admin `VerifyIdTokenAsync(token, checkRevoked: true)`. Identitas berasal dari UID token terverifikasi, bukan input user ID. Backend tidak menerbitkan refresh token tambahan; Firebase SDK mengelola sesi.

Di Settings aplikasi, pengguna yang sudah login dapat memasukkan URL HTTPS backend dan memilih **Verify server session**. Token hanya dikirim ke origin yang dipilih dan redirect HTTP dinonaktifkan. Ini memverifikasi `/api/me`; tidak mengunggah aset atau mengaktifkan sinkronisasi.

## Troubleshooting

- Konfigurasi belum ada: periksa lokasi file, Sync, dan rebuild.
- Developer/configuration error: periksa package, SHA sertifikat aplikasi terpasang, Web client ID dan project.
- Tidak muncul akun: periksa Google Play Services, akun Google pada perangkat, internet dan OAuth tester/consent.
- API gagal setelah login: periksa HTTPS, Project ID server, ADC dan izin Firebase Auth untuk pemeriksaan revocation.

Referensi resmi: [Firebase Google auth](https://firebase.google.com/docs/auth/android/google-signin), [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg), [server verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens).
