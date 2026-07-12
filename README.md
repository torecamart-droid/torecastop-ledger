# TorecaStop Ledger (Android)

An offline-first cash-sale ledger for market stalls and conventions. Scan a
card's SKU barcode, confirm quantity and price, save, and repeat — then export
the session as a zip (CSV + photos) at the end of the day.

Native Android, Kotlin, Jetpack Compose, Room, CameraX + ML Kit. Internal tool —
sideloaded, not published to the Play Store.

---

## Build plan (phased)

Each phase produces something that runs, so it can be reviewed before the next.

| Phase | Scope | Status |
|-------|-------|--------|
| 1 — Foundation & data layer | Gradle project, brand theme, Room database (Session + Sale), DAOs, repository (single-active-session + date-named sessions), app shell that launches | ✅ Done |
| 2 — Core UI | Active Session screen with running totals, sale entry form (SKU, qty, price, note), manual SKU entry | ✅ Done |
| 3 — Barcode scanning | CameraX + ML Kit scan screen wired into the entry form (reads the Code 128 labels the Label Generator already prints) | ✅ Done |
| 4 — Photos | Optional photo capture per sale, stored on device | ✅ Done |
| 5 — Edit & session management | Inline edit/delete of sale lines while active; close session + start new (one active at a time) | ✅ Done |
| 6 — Export | CSV build, zip bundle (CSV + `photos/`), share sheet | ✅ Done |

All six phases are implemented and the project builds a signed release APK
(see **Releasing to the team** below). Since the phase table was written the app
also gained: multi-item sales (one transaction, many item lines), photo
compression on capture, and the release pipeline (signing + R8 + unbundled
barcode model).

**v1.2** adds the trade feature and the UI round from the "UI Improvements &
Trade Feature Plan" brief:

- **Trades** — record two-sided card swaps: cards OUT of stock (scanned by SKU,
  optional per-card cost basis) vs cards IN from the customer (manual name +
  value — no SKU until intake), plus optional cash on top in either direction.
  A live balance card shows the value-added headline before committing.
  Decisions taken per the brief's recommendations: **T1** both metrics, true
  margin as headline with value-swing fallback · **T2** cost basis optional,
  never required · **T3** trades shown separately from sales in totals ·
  **T4** separate `trades.csv` in the export zip · **T5** incoming cost basis
  deferred to the intake workflow.
- **UI Tier 1** — "Sale saved — Undo" snackbar; scan feedback (vibration +
  beep + checkmark overlay); bottom-anchored **New Sale / New Trade** buttons
  (**U1**); bigger, high-contrast running totals; friendlier no-session /
  camera-permission / nothing-to-export states.
- **UI Tier 2** — pre-export session summary; session history with read-only
  detail and re-export; grouped sale/trade cards with a brief just-saved
  highlight; richer totals (sales count, item count, cash total, trade value
  added, trade cash).
- **UI Tier 3** — "Add & scan next" rapid multi-card loop; photo thumbnail with
  safe retake; local-time (Adelaide) display and export formatting confirmed.

## Project layout

```
torecastop-ledger/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml          # all dependency versions
├── gradle/wrapper/…                    # Gradle version pin
└── app/
    ├── build.gradle.kts                # module config, minSdk 29 / target 35
    └── src/main/
        ├── AndroidManifest.xml         # camera permission, IMAGE_CAPTURE query, FileProvider
        ├── java/com/torecastop/ledger/
        │   ├── LedgerApplication.kt    # shares one DB + repository app-wide
        │   ├── MainActivity.kt         # launches the Active Session screen
        │   ├── data/
        │   │   ├── Session.kt          # entity — one selling event
        │   │   ├── Sale.kt             # entity — one transaction (header)
        │   │   ├── SaleItem.kt         # entity — one item line within a sale
        │   │   ├── SaleWithItems.kt    # Room relation: a sale + its item lines
        │   │   ├── Trade.kt            # entity — one card swap (header + cash on top)
        │   │   ├── TradeItem.kt        # entity — one card line (OUT w/ SKU, IN w/ name)
        │   │   ├── TradeWithItems.kt   # Room relation + value-added maths (margin/swing)
        │   │   ├── SessionSummary.kt   # end-of-day numbers (pre-export review, history)
        │   │   ├── SessionDao.kt / SaleDao.kt / SaleItemDao.kt / TradeDao.kt / TradeItemDao.kt
        │   │   ├── LedgerDatabase.kt   # Room database (v3; additive 2→3 migration)
        │   │   ├── LedgerRepository.kt # business rules; atomic multi-item writes
        │   │   ├── PhotoStorage.kt     # per-sale/trade photos + capture-time compression
        │   │   └── LedgerExporter.kt   # builds the sales.csv + trades.csv + photos zip
        │   └── ui/
        │       ├── theme/              # brand palette + Material3 theme
        │       ├── scan/
        │       │   └── BarcodeScannerScreen.kt   # CameraX + ML Kit scanner + scan feedback
        │       └── session/
        │           ├── ActiveSessionViewModel.kt # session + totals + events/undo + all actions
        │           ├── ActiveSessionScreen.kt    # totals, merged ledger, bottom buttons, menu
        │           ├── SaleEntryScreen.kt        # full-screen sale entry (cart + scan-next loop)
        │           ├── TradeEntryScreen.kt       # full-screen trade entry/edit + live balance
        │           ├── SessionHistoryScreen.kt   # closed-session list + read-only detail
        │           ├── ExportSummaryDialog.kt    # pre-export review of the day's numbers
        │           ├── LedgerRows.kt             # shared sale/trade cards for the feeds
        │           ├── LedgerEntry.kt            # merged newest-first sales+trades feed
        │           ├── PhotoCaptureRow.kt        # shared add/retake/remove photo row
        │           ├── SaleEditDialog.kt         # edit/add/remove items + note; delete sale
        │           ├── DraftItem.kt              # in-progress sale item (cart/editor)
        │           ├── DraftTradeItem.kt         # in-progress trade line (entry form)
        │           └── Format.kt                 # shared AUD currency/time formatting
        └── res/                        # strings, colours, theme, launcher icon, file_paths
```

The data layer enforces the confirmed decisions:
- **one active session at a time**
- **sessions auto-named from the open date** (e.g. "01 Jul 2026")
- **timestamps stamped at save**
- **a sale is a transaction with one or more item lines** — a `Sale` header
  (note, photo, timestamp) plus N `SaleItem` lines (SKU, qty, price), written
  atomically; no auto-merging across sales
- **a trade mirrors that shape** — a `Trade` header (note, photo, timestamp,
  cash amount + direction) plus N `TradeItem` lines (direction OUT/IN, SKU or
  card name, qty, trade value, optional cost basis), written atomically.
  Value added is derived at read time: margin over cost when every OUT line
  has a cost basis, otherwise the value swing at market

The Active Session screen sits on top of that:
- On launch it resumes today's active session (or opens one) and shows big,
  sunlight-readable live totals — sales cash, items sold, and (once trades
  exist) trade count, value added and net trade cash, kept separate from sales.
- Two bottom-anchored buttons choose what you're recording: **New Sale** and
  **New Trade** (thumb reach, one-handed).
- A sale is a **cart of items**: enter a SKU — typed, or scanned from the
  printed Code 128 label — with quantity and price, then **Add item** (or
  **Add & scan next** to go straight back to the camera). One optional note and
  one optional photo cover the whole sale. **Save sale** commits every line at
  once (a single not-yet-added item is included automatically, so single-item
  sales stay one-tap fast). A successful scan vibrates, beeps and flashes a
  checkmark so it registers without looking.
- A trade has two asymmetric sides: **cards out** (your stock — scan or type
  the SKU, optional cost basis) and **cards in** (the customer's — name +
  value, no SKU until intake), plus optional **cash on top** either way. A live
  balance card shows OUT vs IN + cash and the value-added headline before you
  commit.
- Every save shows a **"saved — Undo"** snackbar, and the just-saved entry is
  briefly highlighted in the ledger.
- Tap any sale or trade to edit or delete it — while the session is active.
- The overflow menu **exports** the session (after a review of the day's
  numbers; `sales.csv` + `trades.csv` + `photos/` zipped and handed to the
  Android share sheet), opens **Session history** (re-open past sessions
  read-only and re-export their zips), or **closes** the session. After
  closing, the screen offers to start a new one — keeping exactly one active
  at a time.

Captured photos are **compressed on capture** (longest edge ≤ 1600 px, JPEG
quality 75, EXIF orientation applied) to keep storage and exports small. Photos
live under `filesDir/photos/`; the export zip lands in `cacheDir/exports/` — both
declared in `res/xml/file_paths.xml` and shared through the app's FileProvider.
`sales.csv` has one row per item with a `sale_id` column that groups the lines of
each transaction. `trades.csv` (present when the session has trades) has one row
per card line — direction, SKU/card name, values, optional cost basis — with the
trade's cash, value swing, margin and headline value added repeated per row.

## How to build & run

Setting this up on a fresh machine? See [SETUP.md](SETUP.md) for the clone +
signing-keystore steps.

This is a real Android project — it needs an Android build environment (it can't
be compiled in a chat). Two options:

**A. Android Studio (recommended for a one-off build)**
1. Install Android Studio (free).
2. `File → Open` → select the `torecastop-ledger` folder.
3. Let it run the first Gradle sync (this downloads the Android/Google libraries —
   needs internet the first time; accept any version-update prompts).
4. Plug in an Android phone with USB debugging on → press ▶ Run.
   The app installs and launches straight to the Active Session screen.

**B. Claude Code**
Open this folder in Claude Code to run the Gradle builds and produce the
installable APK.

## Releasing to the team

The distributable is a signed release APK (~5 MB) — one file anyone can be sent
and tap to install. No Play Store involved.

### Build a release
```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`.

Before each new release, bump `versionCode` (and `versionName`) in
`app/build.gradle.kts` so phones recognise it as an update.

The release build is:
- **Signed** with the project keystore, so each new version installs directly
  over the previous one — no uninstall, data kept.
- **Shrunk by R8 + resource shrinking** (`proguard-rules.pro` holds the only
  custom rule — readable stack traces).
- **Small (~5 MB)** because the barcode model is *unbundled*: it comes from
  Google Play services on the phone instead of shipping in the APK. The manifest
  asks Play services to fetch the model at install time, so the first scan
  doesn't wait on a download. (Needs a phone with Google Play services —
  true of every mainstream Android device. First install should happen with
  internet available.)

### Signing secrets — keep these safe
`torecastop.jks` (the keystore) and `keystore.properties` (its passwords) live
in the project root and are **gitignored — never commit them**. Back both up
somewhere safe (password manager + a second copy). If they're lost, the team
must uninstall/reinstall to take future updates (losing on-device session data);
if they leak, someone else can sign updates that install over the real app.

### Getting the APK onto phones
1. Send `app-release.apk` by any channel — Drive, email, USB, a group chat.
2. On the phone: open the file → allow "install unknown apps" for whatever app
   opened it (one-time) → tap **Install**.
3. Updates: send the new APK, open, install — it upgrades in place.

If passing files around gets old, **Firebase App Distribution** (free) or a
hosted link/QR (e.g. GitHub Releases) adds versioning and a proper installer
flow. An **App Bundle (`.aab`)** is Play-Store-only — for file sharing, always
distribute an APK.

### Debug builds
`./gradlew :app:assembleDebug` still works for development
(`app/build/outputs/apk/debug/app-debug.apk`), but it's signed with the
throwaway debug key: a phone can't upgrade between a debug and a release build
without uninstalling first. Don't hand debug builds to the team.

## Notes
- The Room database is **v3** (adds the trade tables). The **2→3 upgrade is a
  real migration** — purely additive, existing sessions/sales are kept. Only
  the ancient pre-multi-item **v1** still falls back to a destructive wipe on
  upgrade (the deliberate clean-reset choice from when v2 shipped).
- Custom fonts (Nunito / Inter / Space Mono) are not yet bundled; the app uses
  Material3 default typography so it builds with no binary assets. Drop the font
  files into `res/font` and wire them into `Theme.kt` when they're ready.
- Version numbers in `libs.versions.toml` are recent known-good releases;
  Android Studio may suggest newer ones on sync — accepting them is fine.
- `minSdk 29` (Android 10) is a safe floor for the team's phones and supports
  adaptive icons and modern camera APIs. `targetSdk 35` (Android 15) — bump as
  newer stable Android releases land.
