# Setup — working on this project on another machine

The whole project lives in Git, so getting it onto a new computer (PC or Mac) is
mostly a clone. The only things that **don't** come through Git are secrets and
machine-specific files — this doc covers those.

## 1. Prerequisites

- **Android Studio** (free) — bundles a JDK and the Android SDK, which is all the
  build needs. Install it and let it finish its first-run SDK download.
- Git. (Optional: the GitHub CLI `gh` if you like `gh repo clone`.)

## 2. Clone

```
git clone https://github.com/torecamart-droid/torecastop-ledger.git
cd torecastop-ledger
```

Open the folder in Android Studio (`File → Open`) or in Claude Code.

## 3. Drop in the signing secrets — only if you cut RELEASES here

`torecastop.jks` (the keystore) and `keystore.properties` (its passwords) are
**gitignored on purpose** and are not in the clone. You need them only to build
**release** APKs that can update the copies already on the team's phones.

Copy both files into the **project root** (next to `settings.gradle.kts`) by a
safe channel — password manager, USB stick, encrypted transfer. **Never** email
or commit them.

`keystore.properties` uses a relative `storeFile=torecastop.jks`, so once both
files sit in the project root it works on any OS unchanged:

```
storeFile=torecastop.jks
storePassword=…
keyAlias=torecastop
keyPassword=…
```

> Losing the keystore means the team must uninstall/reinstall to take future
> updates (and lose on-device session data). Keep a backup in at least two places.

If you only **develop/debug** on this machine and always release from the
original Mac, you can skip this step — debug builds sign themselves.

Without these files the project still builds; the release output is simply
unsigned.

## 4. `local.properties` — do nothing

It's gitignored and points at one machine's Android SDK. Android Studio
regenerates it automatically on first open. Don't copy it from another machine.

## 5. Build

The Gradle wrapper is committed, so no separate Gradle install is needed. First
build needs internet (it downloads the Android/Google libraries once).

| | macOS / Linux | Windows |
|---|---|---|
| Debug APK | `./gradlew :app:assembleDebug` | `gradlew.bat :app:assembleDebug` |
| Signed release APK | `./gradlew :app:assembleRelease` | `gradlew.bat :app:assembleRelease` |

Or just press **▶ Run** in Android Studio with a device/emulator selected.

- Debug output: `app/build/outputs/apk/debug/app-debug.apk`
- Release output: `app/build/outputs/apk/release/app-release.apk`

Command-line builds need a JDK 17+ on `JAVA_HOME`. If you don't have one,
point it at the JDK bundled with Android Studio, or just build from inside the
IDE (which uses its own). See `README.md` → **Releasing to the team** for the
full release/versioning process.

## What is NOT in the repo (by design)

- `torecastop.jks`, `keystore.properties` — signing secrets (see step 3)
- `local.properties` — machine-specific SDK path (auto-generated)
- `.claude/settings.local.json` — local Claude Code settings
- `/build`, `/app/build`, `.gradle` — build output
- Any recorded sales — those live only on each phone; export them from the app
