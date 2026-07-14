# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

TorecaStop Ledger — an offline-first, native Android (Kotlin + Jetpack Compose)
cash-sale ledger for market stalls/conventions. Scan a card's SKU barcode,
confirm quantity/price, save, repeat; export the day's session as a zip
(CSV + photos). Internal tool, sideloaded — not on the Play Store, no backend,
no tests directory exists yet.

## Build & run

Real Android project — needs an Android SDK; can't be compiled without one.

```
./gradlew :app:assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # signed release APK (needs keystore.properties)
gradlew.bat ...                   # Windows equivalent
```

- Command-line builds need JDK 17+ on `JAVA_HOME`.
- `keystore.properties` + `torecastop.jks` (project root, gitignored) are only
  needed for signed release builds; without them the project still builds and
  the release output is simply unsigned. Never commit them.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` before every new
  release so phones recognize it as an update.
- On Claude Code web/remote sessions, `.claude/hooks/session-start.sh`
  auto-installs the Android SDK (compileSdk 35, build-tools 35.0.0) and warms
  the Gradle cache — it only runs when `CLAUDE_CODE_REMOTE=true`; locally the
  SDK is assumed to already be present (Android Studio).
- No unit/instrumented test suite exists in this repo currently.

## Architecture

**Layering:** `data/` (Room entities, DAOs, repository, export, photo storage)
→ `ui/session/*ViewModel` (StateFlow-based) → `ui/*Screen` (Compose). One
shared `LedgerDatabase` + `LedgerRepository` instance lives on
`LedgerApplication` (`LedgerApplication.kt`) and is handed to
`ActiveSessionViewModel` via a manual `ViewModelProvider.Factory` — there's no
DI framework.

**`LedgerRepository` is the single point of business rules** — read it before
changing behavior, not just the DAOs:
- Exactly **one active session at a time**; sessions auto-name from the date
  they were opened (e.g. "01 Jul 2026"); timestamps are stamped at save time,
  not entry time.
- A **sale** is a transaction: one `Sale` header (note, photo, timestamp) plus
  N `SaleItem` lines (SKU, qty, price), written atomically via
  `db.withTransaction`. No auto-merging across sales.
- A **trade** mirrors that shape: one `Trade` header (note, photo, timestamp,
  cash amount + direction) plus N `TradeItem` lines. Each item has a
  `direction` (OUT = your stock, scanned by SKU, optional cost basis; IN = the
  customer's, manual name + value, no SKU until intake).
- Trade value-added math lives in `TradeWithItems` (a derived/computed
  property class, not stored): `valueSwing` (in + cash − out at trade values,
  always computable) vs `margin` (in + cash − cost basis, only when every OUT
  line has a cost basis) vs `valueAdded` (the headline: margin when available,
  else valueSwing). Don't duplicate this math elsewhere — extend it here.
- Trades are tracked and exported separately from sales, never merged into
  sales totals (decision T3).

**Room database** (`LedgerDatabase.kt`) is at **v3** (added `trades` +
`trade_items`). The 2→3 migration is a real, additive `Migration` object — no
data loss. Only the ancient pre-multi-item v1 falls back to
`fallbackToDestructiveMigration()` (a deliberate one-time clean-reset). When
changing entity schemas, add a new versioned `Migration`, don't rely on the
destructive fallback.

**UI** (`ui/session/`): `ActiveSessionViewModel` exposes reactive `StateFlow`s
(`session`, `sales`, `trades`, `itemCount`, `total`, `allSessions`) built by
`flatMapLatest`-ing off the active session, plus a `Channel`-backed
`events: Flow<LedgerEvent>` for one-shot "Sale/Trade saved — Undo" snackbars
and a `highlightKey` that briefly flags the just-saved ledger row. Undo and
delete both go through the same `deletePhotoFile` cleanup path — if you add a
new deletable entity with a photo, wire it through there too.
`ActiveSessionScreen` merges sales + trades into one newest-first feed via
`LedgerEntry.kt`; `SaleEntryScreen`/`TradeEntryScreen` are the full-screen
entry forms (with an "Add & scan next" rapid-loop path); `SessionHistoryScreen`
is read-only re-export of closed sessions.

**Barcode scanning** (`ui/scan/BarcodeScannerScreen.kt`): CameraX + ML Kit,
reading the Code 128 SKU labels the (separate) Label Generator tool prints. A
successful scan triggers vibration + beep + a checkmark overlay
(`VIBRATE` permission in the manifest) — keep all three in sync if touching
scan feedback.

**Export** (`LedgerExporter.kt`): builds `sales.csv` (one row per item line,
grouped by a `sale_id` column) and `trades.csv` (present only when the session
has trades; one row per card line, with cash/valueSwing/margin/valueAdded
repeated per row) plus a `photos/` folder, zipped into `cacheDir/exports/` and
shared via the app's `FileProvider` (`res/xml/file_paths.xml`,
`AndroidManifest.xml`). Photos are compressed at capture time
(`PhotoStorage.kt`: longest edge ≤ 1600px, JPEG quality 75, EXIF orientation
applied) and live under `filesDir/photos/`.

## Conventions worth knowing

- All dependency versions are pinned in `gradle/libs.versions.toml` (version
  catalog) — add new deps there, referenced via `libs.xxx` in
  `app/build.gradle.kts`, not as inline coordinate strings.
- `minSdk 29` / `targetSdk 35` — treat as a floor/ceiling for API usage.
- The barcode model is **unbundled**: shipped via Google Play services
  (`play-services-mlkit-barcode-scanning` + the `DEPENDENCIES` meta-data tag in
  the manifest) rather than bundled in the APK, to keep the release APK ~5MB.
  Don't switch to the bundled `com.google.mlkit:barcode-scanning` artifact
  without a reason — it defeats that size optimization.
- No custom fonts are bundled yet (Material3 defaults only); font wiring in
  `ui/theme/Theme.kt` is a known future step, not an oversight.
- Currency/time formatting is centralized in `ui/session/Format.kt` (AUD,
  Adelaide local time) — use it rather than formatting inline.
