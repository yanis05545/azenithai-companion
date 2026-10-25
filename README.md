# Azenith-AI Companion (source APK)

App Android untuk module **Azenith-AI Gaming Booster v2.9.0**.

- **UI** = WebUI module. App memuat `webroot/index.html` dari module yang terpasang
  (`/data/adb/modules/azenith-ai/webroot/`), jadi tampilan selalu sama dengan WebUI di Manager.
  Kalau module belum ada / gagal dibaca, dipakai salinan di `app/src/main/assets/webroot/`.
- **Fitur** = module. Semua tombol di UI memanggil `ksu.exec(...)`; di app itu dijembatani
  (`KsuBridge.kt` -> `RootShell.kt`) ke shell root, lalu menjalankan CLI module
  (`sh /data/adb/modules/azenith-ai/system/bin/azenith-ai ...`). App tidak berisi logika tuning.
- Package: `com.azenithtech.azenithai` (harus sama dengan `PKG` di `service.sh`, `action.sh`,
  `lib/app_install.sh` pada module). Tanpa izin apa pun, termasuk INTERNET.

```
UI (WebView) --ksu.exec--> KsuBridge --su--> azenith-ai CLI --> daemon module --> sysfs/prop
```

## Build

Butuh JDK 17 dan Android SDK 34.

**Android Studio:** File > Open folder ini > tunggu sync > Build > Build APK(s).

**Command line:**
```
gradle wrapper --gradle-version 8.7     # sekali saja, membuat ./gradlew
./gradlew assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
```
`assembleRelease` juga menghasilkan APK yang bisa dipasang (ditandatangani debug key, lihat `app/build.gradle.kts`).

## Masukkan APK ke module

```
tools/pack-module.sh Azenith-AI-Gaming-Booster-v2_9_0.zip app/build/outputs/apk/debug/app-debug.apk
```
Hasilnya `...-with-app.zip`. Atau manual: buat folder `app/` di dalam zip module, taruh APK dengan
nama persis `AzenithAI-Companion.apk`. Flash lewat Manager, reboot; module memasang app otomatis
(`lib/app_install.sh`, log: `/data/adb/azenith-ai/app_install.log`). Buka dari launcher atau tombol Action.

Kalau `webroot/index.html` di module diubah, UI app ikut berubah tanpa build ulang.
`tools/sync-webroot.sh <module.zip>` hanya untuk memperbarui salinan fallback.

## Catatan

- Saat pertama dibuka, Manager (Magisk/KernelSU/APatch) meminta izin root untuk app ini. Wajib diizinkan.
- WebView hanya boleh menampilkan halaman milik module; navigasi ke URL lain dialihkan ke browser,
  karena halaman di dalamnya bisa menjalankan perintah root.
- Ganti signing key = APK lama harus dicopot dulu; `app_install.sh` sudah menangani ini otomatis.

## Build lewat Termux + GitHub Actions (tanpa PC)

1. Taruh zip module di folder `module/`.
2. Push repo ini ke GitHub (branch `main`). Workflow `.github/workflows/build.yml` jalan otomatis.
3. Tab Actions > run terakhir > Artifacts: `module-with-app` (zip module sudah berisi APK) dan `AzenithAI-Companion-apk`.
