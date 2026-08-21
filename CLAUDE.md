# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

TorecaStop Ledger — an offline-first, native Android (Kotlin + Jetpack Compose)
cash-sale ledger for market stalls/conventions. Scan a card's SKU barcode,
confirm quantity/price, save, repeat; export the day's session as a zip
(CSV + photos). Internal tool, sideloaded — not on the Play Store, no backend,
no tests directory exists yet.

**Current status: v1.3 is shipped** — merged to `main`, tagged as a public
GitHub Release with a signed `app-release.apk` attached, installed on the
owner's real phone. The in-app update checker is live (see below). Anything
past this point is v1.4+ and not yet started as of this writing — update this
line when that changes.

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
  the release output is simply unsigned. Never commit them, never print
  `keystore.properties`' contents (it holds real passwords) — if it's ever
  shown to you as changed-on-disk, treat that as sensitive and don't echo it.
  The original signing key was lost at one point and this project's key was
  **re-generated** — meaning any device still running a build signed with the
  *original* key needs one uninstall/reinstall to move onto this one. From
  here on, normal in-place updates work as long as this keystore is used.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` **and**
  `update-manifest.json` (same commit) before every new release — see
  **Publishing an update** in `README.md` for the full release checklist.
- On Claude Code web/remote sessions, `.claude/hooks/session-start.sh`
  auto-installs the Android SDK (compileSdk 35, build-tools 35.0.0) and warms
  the Gradle cache — it only runs when `CLAUDE_CODE_REMOTE=true`. Locally
  (Windows, no Android Studio on this machine): JDK 17 (Eclipse Temurin) and
  the Android SDK command-line tools were installed manually via `winget` /
  `sdkmanager` — JDK at
  `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`, SDK at
  `C:\Users\dandu\AppData\Local\Android\Sdk`. Export `JAVA_HOME`/`ANDROID_HOME`
  to those before running Gradle from a fresh shell.
- No unit/instrumented test suite exists in this repo currently.
- VS Code is installed on this machine (`C:\Users\dandu\AppData\Local\Programs\Microsoft VS Code`)
  with the Kotlin and GitLens extensions, for reviewing diffs before pushing.

## Architecture

**Layering:** `data/` (Room entities, DAOs, repository, export, photo storage)
→ `ui/session/*ViewModel` (StateFlow-based) → `ui/*Screen` (Compose). One
shared `LedgerDatabase` + `LedgerRepository` instance lives on
`LedgerApplication` (`LedgerApplication.kt`) and is handed to
`ActiveSessionViewModel` via a manual `ViewModelProvider.Factory` — there's no
DI framework. Two small extra packages: `intake/` (customer contact-intake QR
round-trip) and `update/` (in-app update check).

**`LedgerRepository` is the single point of business rules** — read it before
changing behavior, not just the DAOs:
- Exactly **one active session at a time**; sessions auto-name from the date
  they were opened (e.g. "01 Jul 2026"), with an optional free-text show/event
  `label` alongside it; timestamps are stamped at save time, not entry time.
- A **sale** is a transaction: one `Sale` header (note, timestamp, optional
  `cashReceived`) plus N `SaleItem` lines (SKU, qty, price, optional note),
  written atomically via `db.withTransaction`. No auto-merging across sales.
  Photos (any number, whole-sale or per-item) live in a separate `SalePhoto`
  table keyed by `saleId` with a nullable `saleItemId` — inserting them
  requires the generated `SaleItem` ids first, so `addSale`/`updateSale` use
  `saleItemDao.insertAll(...)` (which returns the generated ids) before
  attaching photos.
- A **trade** mirrors that shape: one `Trade` header (note, timestamp, cash
  amount + direction, optional `customerPhone`/`customerEmail`) plus N
  `TradeItem` lines. Each item has a `direction` (OUT = your stock, scanned by
  SKU; IN = the customer's, manual name, no SKU until intake) and a plain
  `saleCost` — **no margin/value-added calculation** (this existed through
  v1.2/early v1.3 as `valueSwing`/`margin`/`valueAdded` on `TradeWithItems`,
  and was deliberately scrapped in the v1.3 revision per direct user
  feedback — don't reintroduce it without being asked). `TradeItem` still
  carries a deprecated, always-null `acquisitionCost` field (mapped to the
  original `costBasis` column) — it was tried, then removed from the UI on
  later review; the column stays unused rather than forcing a schema change.
  `TradePhoto` mirrors `SalePhoto`.
- Trades are tracked and exported separately from sales, never merged into
  sales totals (decision T3).
- **Cash reconciliation**: optional `Session.startingFloat` (set at open) and
  `Session.countedCash` (set at close, alongside an optional
  `cashCountPhotoPath`), plus a `CashAdjustment` table for cash movements that
  aren't a sale/trade (paid-out / cash-in log). `SessionSummary.expectedCash`
  / `.cashVariance` derive the reconciliation math — both are computed, not
  stored.
- **Customer contact-intake QR** (`intake/CustomerIntakeQr.kt` +
  `QrCodeGenerator.kt`, v1.4): replaces the old, never-configured
  seller-intake design. Staff shows a QR (on-device generation via
  zxing-core, no network) linking to a small static form
  (`docs/intake.html`, GitHub Pages) that the *customer* fills in on their
  *own* phone; that page renders their answers back as a second QR, with no
  server involved at either end. Staff scans that back in via the same
  `BarcodeScannerScreen` used for SKUs (now also decoding QR). A client-side
  nonce — not a persisted trade id — correlates the two QRs, so this works
  before a trade is ever saved, in both new-trade and edit flows. A scanned
  response only overwrites `customerPhone`/`customerEmail` when non-blank,
  and a stale/unrecognized scan is rejected with a clear message rather than
  silently misattributing one customer's details to another trade.
- **In-app update check** (`update/UpdateChecker.kt`): network-optional poll
  of `update-manifest.json` on `main` at app start; shows a dismissible
  banner when the manifest's `versionCode` is ahead of the installed build.
  Fails silently offline/on any error — must never block recording a sale.

**Room database** (`LedgerDatabase.kt`) is at **v5**. Every upgrade from v2 on
is a real, additive `Migration` — 2→3 added the trade tables, 3→4 added
per-item notes / session label / cash-reconciliation / `cash_adjustments`,
4→5 added `sale_photos`/`trade_photos`, `Sale.cashReceived`, and
`Trade.customerPhone`/`customerEmail`. `tradeValue`→`saleCost` and
`costBasis`→`acquisitionCost` were renamed at the **Kotlin level only**
(`@ColumnInfo` mapping) — same columns, no migration needed for a pure
rename; keep that pattern in mind before reaching for a new migration when a
field is just being relabeled. Only the ancient pre-multi-item v1 falls back
to `fallbackToDestructiveMigration()` (a deliberate one-time clean-reset).

**UI** (`ui/session/`): `ActiveSessionViewModel` exposes reactive `StateFlow`s
(`session`, `sales`, `trades`, `itemCount`, `total`, `allSessions`,
`cashAdjustments`, `cashAdjustmentNet`, `updateAvailable`) built by
`flatMapLatest`-ing off the active session, plus a `Channel`-backed
`events: Flow<LedgerEvent>` for one-shot "Sale/Trade saved — Undo" snackbars
and a `highlightKey` that briefly flags the just-saved ledger row. Undo and
delete both go through the same photo-cleanup path (now plural —
`deletePhotoFiles(paths: List<String>)`, since a sale/trade can have any
number of photos) — if you add a new deletable entity with photos, wire it
through there too. `ActiveSessionScreen` merges sales + trades into one
newest-first feed via `LedgerEntry.kt`; `SaleEntryScreen`/`TradeEntryScreen`
are the full-screen entry forms (with an "Add & scan next" rapid-loop path);
`SessionHistoryScreen` is read-only re-export of closed sessions;
`SessionDialogs.kt` holds the smaller dialogs (session label, starting float,
cash-adjustment log, close-session reconciliation, seller-intake QR).

**Photo capture**: `PhotoCaptureRow.kt` is the original single-photo capture
row — now used only for the one-shot cash-count photo at session close.
Everywhere else (sale/trade, whole-transaction and per-item) uses
`MultiPhotoCaptureRow.kt`, which supports any number of photos.

**Barcode scanning** (`ui/scan/BarcodeScannerScreen.kt`): CameraX + ML Kit,
reading Code 128 (the SKU labels the separate Label Generator tool prints)
and QR (customer contact-intake responses, v1.4). `title`/
`permissionRationale` parameters let a second use of this same screen show
copy matching what's actually being scanned. A successful scan triggers
vibration + beep + a checkmark overlay (`VIBRATE` permission in the
manifest) — keep all three in sync if touching scan feedback.

**Export** (`LedgerExporter.kt`): builds `sales.csv`, `trades.csv` (present
only when the session has trades), `cash.csv` (present only when there's
reconciliation data), and a `photos/` folder, zipped into `cacheDir/exports/`
and shared via the app's `FileProvider` (`res/xml/file_paths.xml`,
`AndroidManifest.xml`). Photo filename columns are `;`-joined when a
sale/trade/item has more than one. No `value_swing`/`margin`/`value_added`/
`unit_acquisition_cost` columns — those were removed along with the
calculation. Photos are compressed at capture time (`PhotoStorage.kt`:
longest edge ≤ 1600px, JPEG quality 75, EXIF orientation applied) and live
under `filesDir/photos/`.

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
- No custom fonts are bundled yet (Nunito/Inter/Space Mono chosen but not
  wired in; currency totals use a tabular-figure feature on the default font
  in the meantime — see `Format.kt`'s `tabularFigures()`). Font wiring in
  `ui/theme/Theme.kt` is a known future step, not an oversight.
- Currency/time formatting is centralized in `ui/session/Format.kt` (AUD,
  Adelaide local time) — use it rather than formatting inline.
- Dark mode uses real brand colors (`InkBackground`/`InkSurface`/etc. in
  `Color.kt`), not Material3 defaults — keep both `LightColors`/`DarkColors`
  in `Theme.kt` in sync when adding new semantic colors.
- **Testing on-device without touching the owner's real app/data**: add a
  temporary `applicationIdSuffix = ".migrationtest"` under `buildTypes { debug { ... } }`
  in `app/build.gradle.kts`, build+install the debug variant — it installs as
  a separate package (`com.torecastop.ledger.migrationtest`) alongside the
  real app, safe to freely install/uninstall/wipe. **Revert the suffix**
  (`git checkout app/build.gradle.kts`) before committing — it's scratch-only,
  never meant to ship.
- `adb install` frequently **hangs on reporting back** over this particular
  USB connection even though the install itself succeeds within a couple of
  seconds — don't trust a timeout as a failure. Verify with
  `adb shell dumpsys package <id> | grep lastUpdateTime` (or `versionCode`)
  instead of retrying blindly.
- `gh` (GitHub CLI) is **not installed** on this machine — use the
  `mcp__Claude_Browser__*` tools to drive github.com directly for PRs/releases
  (open a compare URL, fill the form, merge). The browser session's GitHub
  login can silently drop between conversations — if a page redirects to
  "Sign in to GitHub," ask the user to log in themselves (never enter
  credentials).
- File uploads (e.g. attaching a release APK on GitHub) can't be automated
  through the browser tools — native OS file picker is outside their reach.
  Click the attach control, then ask the user to select the file themselves
  and confirm when done.
