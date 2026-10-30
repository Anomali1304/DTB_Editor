# DTB Studio

Offline Device Tree (`.dtb` / `.dtbo`) editor, inspector, decompiler, and compiler for Android.

**Package / Application ID:** `com.dtb.editor`

## 🚀 Fitur Utama
- **Import & Decompile**: Decompile file `.dtb` dan `.dtbo` menjadi `.dts` yang mudah dibaca.
- **Tree Explorer**: Navigasi node hierarki Device Tree (`/cpus`, `thermal-zones`, `regulators`, `clocks`, dll).
- **CPU Cluster Editor**: Visualisasi topologi CPU cluster core.
- **DTS Editor**: Editor kode Device Tree terintegrasi.
- **Compiler**: Compile kembali perubahan `.dts` menjadi `.dtb`.
- **Compare & Diff**: Bandingkan perubahan file asli dan modifikasi.
- **100% Offline**: Berjalan secara mandiri tanpa memerlukan Termux, root, ataupun koneksi internet.

---

## 🛠️ Build & CI/CD GitHub Actions

Project ini sudah dilengkapi dengan workflow otomatis di [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml).

### 1. Build Otomatis Saat Push
Cukup push commit ke branch `main` atau `master`:
```bash
git add .
git commit -m "Initial commit DTB Studio"
git push origin main
```
GitHub Actions akan secara otomatis:
- Menjalankan unit tests (`testDebugUnitTest`).
- Meng-compile Release APK (`DTBStudio-release.apk`) dan App Bundle (`DTBStudio-release.aab`).
- Menghasilkan SHA256 checksum untuk verifikasi integritas file.
- Mengunggah file APK dan AAB ke bagian **Artifacts** di tab **Actions** repository GitHub Anda.

### 2. Otomatis Buat GitHub Release dengan Tag
Ketika Anda membuat tag versi (misalnya `v1.0.0`):
```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions akan membuat Release baru di GitHub dan otomatis melampirkan file APK dan AAB siap download.

### 3. Signing Release Keystore (Opsional)
Secara default, workflow telah dilengkapi dengan auto-fallback signing key sehingga build **pasti sukses** tanpa konfigurasi tambahan.

Jika Anda ingin menggunakan upload key / production keystore milik Anda sendiri:
1. Encode keystore `.jks` ke Base64:
   ```bash
   base64 -w 0 my-release-key.jks
   ```
2. Buka repository GitHub -> **Settings** -> **Secrets and variables** -> **Actions**.
3. Tambahkan Repository Secrets berikut:
   - `RELEASE_KEYSTORE_BASE64`: Hasil teks base64 keystore
   - `RELEASE_STORE_PASSWORD`: Password keystore
   - `RELEASE_KEY_PASSWORD`: Password key

---

## 💻 Build Manual Secara Lokal
Untuk mem-build APK secara lokal:
```bash
# Build Debug APK
./gradlew assembleDebug

# Jalankan Unit Tests
./gradlew testDebugUnitTest

# Build Release APK
./gradlew assembleRelease
```
File APK akan berada di folder `app/build/outputs/apk/`.
