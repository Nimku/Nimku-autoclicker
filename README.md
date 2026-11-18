# Nimku AutoClicker

An Android auto clicker for automating taps, swipes and screen-based tasks. You can install it next to other auto clickers.

Nimku AutoClicker is based on the open-source [Smart AutoClicker / Klick'r](https://github.com/Nain57/Smart-AutoClicker) by Kevin Buzeau, and it is released under the same **GNU GPL v3** license.

## What it does

- **Simple mode (Dumb scenarios):** record taps, swipes and pauses at fixed positions, then repeat them.
- **Smart mode (Smart scenarios):** look at the screen and react to what it shows: images, colors, text (OCR) and timers. Then tap, swipe, type text, start apps, send notifications, or change counters and toggles.
- Quick Settings tile, service notification controls, and backup/restore of scenarios.

## How it differs from the original

| | Nimku AutoClicker |
|---|---|
| Application ID | `com.nimku.autoclicker` (debug: `com.nimku.autoclicker.debug`), so it installs next to the original app |
| Ads, in-app purchases, Firebase Crashlytics, Play review prompts | Removed. Only the open-source (F-Droid) build is kept |
| Play Store flavour and Play Asset Delivery OCR packs | Removed. OCR models are downloaded directly when needed |
| Build variants | Just `debug` and `release` (no product flavours) |
| Branding | New name and launcher icon |

## Remote control (ADB, Tasker, cloud phones)

Turn on **Settings → Remote control** to start and stop scenarios from outside the app. Each scenario must be started from the app at least once first, so the permissions are granted.

```bash
# Start a scenario by its name
adb shell am start -a com.nimku.autoclicker.START \
  -n com.nimku.autoclicker/com.buzbuz.smartautoclicker.feature.qstile.ui.RemoteControlActivity \
  --es name "My scenario"

# Stop the running scenario
adb shell am start -a com.nimku.autoclicker.STOP \
  -n com.nimku.autoclicker/com.buzbuz.smartautoclicker.feature.qstile.ui.RemoteControlActivity
```

Other extras: `--el id <number>` to pick a scenario by id, and `--ez smart true|false` when a simple and a smart scenario share a name. With no extras, the last scenario used with the quick settings tile starts. Tasker and other automation apps can send the same intents.

### Unattended setup (cloud phone, always-on device)

Run these once from a computer connected with ADB:

```bash
adb shell settings put secure enabled_accessibility_services com.nimku.autoclicker/com.buzbuz.smartautoclicker.SmartAutoClickerService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.nimku.autoclicker PROJECT_MEDIA allow      # skip the screen capture prompt (Android 13 and older)
adb shell dumpsys deviceidle whitelist +com.nimku.autoclicker      # don't kill the app in the background
```

## Download

Every push builds signed APKs with GitHub Actions:

1. Open the **Actions** tab, then the latest **Build APK** run.
2. Download the **NimkuAutoClicker-apks** artifact.
3. Install `NimkuAutoClicker-universal-release.apk`, which works on every device. You can also pick the file for your CPU: `arm64-v8a` for most modern phones.

When you push a tag such as `v1.0.0`, a GitHub Release with the APKs is also created.

## Build locally

Requirements: JDK 21, an Android SDK with an NDK installed (`ANDROID_NDK_HOME` set), and CMake 3.22.1.

```bash
./gradlew assembleDebug      # build/outputs/apk/debug
./gradlew assembleRelease    # build/outputs/apk/release
./gradlew assembleRelease -PabiFilters=arm64-v8a   # faster build, one ABI only
```

### Signing

- By default, builds are signed with the **shared** key `smartautoclicker/nimku-shared.jks`, which is committed to this repository. Each new APK installs as an update over the previous one. The key is public, so do not use it for a store release.
- To use a private key, put it at `smartautoclicker/nimku-release.jks` (this path is git-ignored) and pass `-PsigningStorePassword=… -PsigningKeyAlias=… -PsigningKeyPassword=…`. In CI, set the repository secrets `RELEASE_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.

## Project layout

- `smartautoclicker/`: the app module (activities, accessibility service, settings)
- `core/dumb`: simple click/swipe scenarios
- `core/smart/*`: image/text detection (OpenCV + NCNN, native C++), the processing engine and the Room database
- `core/common/*`: shared overlays, UI, permissions, display and settings
- `feature/*`: scenario editors, backup, notifications, quick settings tile and tutorials
- `build-logic/`: Gradle convention plugins

## License

GNU General Public License v3.0. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
