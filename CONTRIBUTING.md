# Contributing to Olauncher

Olauncher aims to stay minimal — a distraction-free, ad-free launcher. Not every feature
request fits that goal; some niche features are kept hidden rather than exposed by default.

## Before opening a PR

- For anything beyond a small, obvious fix, please open an issue first describing what you'd
  like to do and why, so we can agree on scope before you invest time in code.
- Bigger feature ideas or scope questions are best raised as a GitHub Discussion (Ideas
  category) first.
- Keep PRs focused — one concern per PR.

## Building the project

- Android Studio, Kotlin, compileSdk 36 / minSdk 24 / targetSdk 36, JVM target 17.
- Please build and test locally before opening a PR:
  `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`.

## Downloading a debug APK

- Pushes, merges, and pull requests to `main` automatically run **Build debug APK** in GitHub Actions.
  To build manually, open **Actions → Build debug APK → Run workflow**.
  The manual option becomes available once the workflow is on the default branch.
- Open a successful run and download **ACES-Launcher-Debug-APK** from **Artifacts**.
  Extract the ZIP and copy `app-debug.apk` to your Android phone.
- On Android 7.0 (API 24) or newer, open the APK and allow installation from that
  source when prompted. The debug APK is automatically signed and installs separately
  from the release app. Debug signing keys can differ between runs; if Android rejects
  an update because of a signature mismatch, uninstall the previous debug app first
  (this deletes its settings), then install the new APK.

## Code style

- No ktlint/detekt config exists in this repo yet — match the style of the file you're editing.

## Licence

- GPLv3 — by contributing, you agree your changes are licensed under the same terms.
