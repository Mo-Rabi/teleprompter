# ArabicWithMo Floating Teleprompter (Android)

A small, native Android companion application for Mo's teleprompter. It uses Android's **Display over other apps** permission to show a draggable reading window over the native Huawei Camera app and other apps.

## Features
- 111 offline scripts sourced from Mo-Rabi/teleprompter on 2026-11-16, including Arabic diacritics.
- Search all scripts, newest-first.
- Launch a persistent floating foreground-service overlay; drag the header, resize by pulling the bottom-right corner.
- Play/pause auto-scroll, next/previous script, faster/slower, and independent font size.
- Overlay size, location, speed, text size, and last script saved to the device.
- Camera itself is **not** recorded or controlled by the app. Use your native camera for the best available quality.

## Building a debug APK
Requires Android SDK 35, JDK 17 and Gradle 8.10.2:
```bash
gradle -p android-floating assembleDebug
```
Install `android-floating/app/build/outputs/apk/debug/app-debug.apk` on your Android phone.
You can also get the debug APK from the [Build Floating Teleprompter APK workflow](../../actions/workflows/android-floating-apk.yml), under *Artifacts* of a successful run. This is an **unsigned-for-distribution / debug-signed** APK for personal testing, not a release-ready Play Store package.

## v2 troubleshooting and installation
**The new APK is built with an independent debug signing key.** Android may reject updating a v1 debug APK signed in an earlier GitHub Actions run. If installation says the package conflicts, uninstall the old **Mo Floating Prompter** app first, then install v2. This resets preferences saved by v1.

1. Grant **Display over other apps**, and on Huawei/EMUI check **Settings → Apps → Permissions → Dropzone** for this app.
2. Tap **SHOW FLOATING SCRIPT**, and check whether the draggable script is visible over the app itself. The new app reports WindowManager attachment/errors.
3. Confirm you can see it, then tap **GO HOME** and open Huawei Camera manually from your launcher (not through an embedded camera intent).
4. If it floats over Home but disappears only in Huawei Camera, EMUI/Camera may be hiding third-party overlays. The app cannot override platform-enforced overlay hiding. Also check that the app is allowed to run in background.

## Setup on Huawei
1. Install and open the APK (Android may ask to allow installations from that source).
2. Tap **Allow display over other apps** and grant permission in Android Settings.
3. Choose a script. The floating teleprompter opens.
4. Open the phone's native Camera app and switch to video recording; place the floating script near the selfie lens.
5. Drag the title/header to reposition; pull the corner to resize. Use ▶ and Ⅱ to scroll or pause.
6. Stop the overlay via its ✕ button or the persistent notification.

The overlay will not appear in the native camera's *recorded video*, as it is only a UI layer on top of the app. Some OEM camera apps hide overlays for security; this is controlled by Android/EMUI, not the teleprompter. Huawei's battery management may stop background services; allow background operation for Mo Floating Prompter if needed. Android 13+ may request notification permission.

## Architecture
- Java native Android (no third-party runtime dependencies).
- `ScriptCatalog.java` loads the full script list from `assets/scripts.json` for offline use.
- `FloatingService.java` uses `WindowManager.TYPE_APPLICATION_OVERLAY` and a foreground notification.
- `MainActivity.java` handles script selection and overlay permission.
- No external data collection; no camera or microphone permission is requested.
- App updates are needed to refresh the bundled scripts. The separate PWA stays unchanged.

## Limits
Android permissions and camera overlays can vary by phone and EMUI version; test on the actual Huawei P30 Pro. A sideloaded debug APK may trigger normal Android warnings. Debug builds are not suitable for public distribution or Play Store release.
