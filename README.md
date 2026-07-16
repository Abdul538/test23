# Progress Hub (Kotlin / Jetpack Compose)

Native Android rewrite of the Flutter app — no Dart, no Flutter engine.
Single Activity + Jetpack Compose, plain SharedPreferences+JSON for storage (no Flutter shared_preferences plugin).

## What's included (kept simple on purpose)
- **Setup** screen (first run): start weight, goal weight, weeks.
- **Hari Ini (Today)**: current week's days, check off sessions, edit actual km.
- **Progres**: streak, totals, weight log + history.
- **Pengaturan**: edit program settings.

Ported 1:1 from `lib/program.dart` and `lib/app_state.dart` logic (phases, weekly
km targets, day roles, streaks, calorie estimate). Visual extras from the
Flutter version (particle background, glass-blur cards, custom route painter,
undo/redo, JSON backup/share) were intentionally dropped to keep this a small,
working baseline — easy to extend once you confirm the core works.

## Opening the project
1. Open this folder in **Android Studio** (Giraffe/Koala or newer).
2. Let it sync — Android Studio will generate the Gradle wrapper jar for you
   if it's missing (or run `gradle wrapper` once if you have Gradle installed
   locally).
3. Run on a device/emulator (minSdk 26).

## Package
`id.myapp.progresshubkt` — deliberately different from the original Flutter
app's `id.myapp.progresshub`, so both can be installed side by side on the
same device without an install conflict/signature mismatch.

## Updating in place (no uninstall needed)
`app/key.properties` + `app/progress-hub-kt-release.jks` are committed on
purpose (not secret — this isn't a Play Store production key). Both `debug`
and `release` build types sign with this same key, so:
- CI (GitHub Actions) always produces `app-release-apk`, signed identically
  every run.
- Installing a new build over an old one now works as a normal update — no
  more "App not installed" / uninstall-first prompt, which used to happen
  because each CI run and each machine's auto-generated debug key differed.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` before each
  release you want to keep distinguishable.

If you ever want to rotate this key (e.g. before a real Play Store release),
generate a new keystore and swap the values in `key.properties` — just know
any device with the app installed under the old key will need one final
uninstall to move to the new one.

## Hardware acceleration
`android:hardwareAccelerated="true"` is set explicitly on both the
`<application>` and `<activity>` in the manifest (Compose already renders
on the GPU by default on API 26+, but this makes it explicit rather than
relying on the default). `enableOnBackInvokedCallback="true"` is also set so
back-gesture handling uses the newer, smoother predictive-back path on
Android 13+ instead of the legacy dispatch, which can otherwise cause a
frame hitch on gesture nav.
