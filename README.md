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

**v1.3** — implemented (Room schema now **v4**; additive 3→4 migration keeps all
data). This release adds:

- **Per-item notes**, for inventory / serial-number tracking. Today, notes
  live only at the transaction level (one per sale, one per trade) — too
  coarse to record a serial number or condition note against a specific card
  when a sale or trade has multiple lines. Add a nullable `note` column to
  both `SaleItem` and `TradeItem` (schema v3→v4, purely additive
  `ALTER TABLE ... ADD COLUMN`, same shape as the v2→v3 trade-tables
  migration — existing data untouched). UI: a "Note" field alongside
  SKU/qty/price on the sale-entry row, and alongside SKU/qty/value/cost on
  the trade **cards-out** row — that's the store's own stock, where a serial
  number actually needs tracking (cards-in rows get the field too, for
  consistency, even though it matters less there until a card is intake'd
  with a real SKU). Surface the note on draft rows (`TradeDraftRow` / the
  sale cart row), saved history rows (`LedgerRows.kt`), and the edit flow.
  Export: add an `item_note` column to `sales.csv` and `trades.csv`.
- **In-app update check.** This app has no Play Store auto-update — updates
  are hand-delivered per **Getting the APK onto phones** above, so the team
  can easily be running stale builds. Since there's no Play Store, Google's
  Play Core In-App Update API isn't available (Play-only); instead, poll a
  release feed on app start (network-optional — silently skip when offline,
  keeping the app offline-first): the GitHub Releases API
  (`/repos/torecamart-droid/torecastop-ledger/releases/latest`) is the
  natural source since releases already land there, **provided the repo (or
  at least its releases) can be made public or reached with a token** — if it
  must stay fully private, fall back to a small hand-maintained public JSON
  manifest (e.g. a GitHub Gist or Firebase Hosting) with `versionCode`,
  `versionName`, and a download URL. Compare the fetched `versionCode` against
  `BuildConfig.VERSION_CODE`; if newer, show a dismissible banner ("v1.3
  available — Update") that opens the release page/APK URL in the browser,
  letting the existing manual install-unknown-apps flow finish the job —
  no `REQUEST_INSTALL_PACKAGES` / silent-install complexity needed for v1.3.
  Needs the `android.permission.INTERNET` permission added to the manifest
  (not currently present, since the app has otherwise never needed network).
- **Cash reconciliation at session close.** Add nullable `startingFloat` and
  `countedCash` columns to `Session` — same v3→v4 migration batch as the
  per-item notes above, so this doesn't need its own schema bump. Opening a
  session gets an optional, skippable "Starting float" prompt (cash
  physically in the drawer); closing one gets an optional "Count the drawer"
  prompt, and the app shows the variance between counted cash and the
  expected figure (float + cash sales + net trade cash) before confirming the
  close. Skipping either prompt just means no reconciliation line for that
  session — this doesn't force the workflow on anyone not using it yet.
  Surface float/counted/variance in `SessionDetailScreen`, the pre-export
  summary dialog, and the export itself. Two companions that make the
  reconciliation actually trustworthy rather than just arithmetic:
  - **Cash adjustments / "paid out" log** — a small entity (amount + reason,
    no items), for cash movements that aren't a sale or trade (making change
    for a neighbouring stall, a supply run). Feeds the expected-cash formula
    so a legitimate cash movement doesn't read as a reconciliation error.
  - **Cash-count photo**, reusing the existing `PhotoCaptureRow` (already
    used for sale/trade photos) to optionally snap the counted drawer at
    close — a cheap audit trail alongside the numbers, no new infrastructure.
- **High-value sale confirm-guard.** No schema change — pure UI. Above a
  configurable dollar threshold, saving a sale/trade line shows a "Confirm
  $X?" step before it commits. A single card in this business can be a large
  dollar figure, so a fat-fingered price or quantity (e.g. $500 vs $50) is an
  expensive typo worth catching, without adding friction to normal $5–$50
  sales below the threshold.
- **Show/event name on a session.** Sessions currently auto-name from the
  date only ("13 Jul 2026") — fine for a single-location shop, but the team
  travels to different shows/conventions, so the date alone won't mean
  anything looking back months later. Add an optional free-text `label` on
  `Session` (same v3→v4 migration batch again), settable when opening a
  session, shown alongside the date in the title bar, session history, and
  export filename.

  UI polish, no schema involved — all pure visual/interaction work:
  - **Dark mode & color semantics.** `DarkColors` in `Theme.kt` only
    overrides primary/secondary/tertiary, leaving background/surface/
    onSurface as Material3's generic defaults — dark mode (the default, via
    `isSystemInDarkTheme()`) currently loses the brand's Cloud/Mist/Ink
    identity that light mode has. Design a real dark surface derived from
    `Ink` instead of accepting the default. While touching the color scheme,
    also pin an explicit brand `error` color: right now a trade's negative
    value-added falls back to Material's default error color, which can read
    too similarly to `primary` (Coral, itself a warm red) used for plain sale
    totals — an explicit, distinct error color keeps "this trade lost value"
    visually unambiguous from "here's a normal total."
  - **Currency in a tabular/monospace font.** `Theme.kt` ships plain
    `Typography()` — the fonts its own comment names (Nunito/Inter/Space
    Mono) were never wired in (also flagged under **Notes** below). Rather
    than a general font pass, the concrete first win is wiring a
    tabular-figure font (e.g. Space Mono) specifically into currency totals,
    so digits line up like a till display instead of the proportional
    default.
  - **Cap variable-length content in the ledger feed.** `NoteLine` and the
    multi-item loop in `SaleRow` (`LedgerRows.kt`) both render with no
    `maxLines`/ellipsis — a long note (once per-item notes ship) or a
    many-item sale can blow out one card's height mid-scroll. Cap both to
    2-3 lines with a "+N more"/tap-to-expand, so every card in the feed
    stays a predictable size.
  - **Consistent empty states.** `NoActiveSession` (icon + title + body) and
    `EmptyLedger` (plain two-line text) currently read as one finished state
    and one placeholder. Give `EmptyLedger` the same icon+title+body
    treatment for a consistent system.
  - **Quantity direct-entry.** `QuantityStepper` (`ActiveSessionScreen.kt`)
    is tap-only +/−, so a bulk sale (e.g. 24 commons) takes 23 taps. Make the
    number itself tappable to type a value directly, keeping +/− for the
    fast 1-3 case.
  - **Visually distinguish trade cards from sale cards.** Both use plain
    `CardDefaults.cardColors()` today — the only tell is a small icon+label
    inside the card. A subtle left-edge stripe or tonal tint on trade cards
    would make a long mixed feed scannable for "just the trades" without
    reading each card individually.
- Bump `versionCode`/`versionName` to 1.3 per the release convention.

**Planned v1.4** — external integrations & multi-till workflow (bigger scope
than v1.3 — network-dependent and/or cross-device, so kept separate):

- **Shopify SKU lookup, to double-check faulty scans.** Read-only — not
  inventory sync. The team already runs Shopify plus a separate inventory
  system for stock; this app should stay a ledger, not a second source of
  truth for stock levels. The goal is narrower: catch a misread barcode or a
  fat-fingered manual SKU at entry time by confirming it matches something
  real. Background-sync a local cache of SKU/barcode → product title from the
  Shopify Admin API (GraphQL `productVariants`/`inventoryItem` lookup by SKU)
  whenever the phone's online; the actual scan-time check reads only that
  local cache, so it stays instant and works with no signal — non-negotiable
  given the app's offline-first design. An unrecognized SKU shows a soft
  inline warning ("Not found in Shopify — check the scan") but never blocks
  the save; a recognized one can show the matched product title next to the
  SKU field as a positive double-check. Needs a Shopify Admin API access
  token stored on-device, plus the same `android.permission.INTERNET` the
  update-checker above needs (one manifest change covers both v1.4 features).
- **Multi-till session consolidation.** The team runs the app on two phones
  during the one session/show — each phone has its own independent local
  database, so each opens its own same-day session (e.g. two unrelated
  "13 Jul 2026" sessions, one per phone; session names/ids are only unique
  within a single phone's DB). Two-step plan:
  1. *Cheap, do first:* a one-time per-phone "Till name" setting (e.g. "Till
     A" / "Till B"), stored locally and stamped as a new `till` column on
     every row of `sales.csv`/`trades.csv`. Unblocks combining both phones'
     exports into one sheet for the existing external-inventory/Shopify
     workflow, with zero ambiguity about which till a row came from. Also
     show the till name in the app itself (title bar, alongside the session
     date/label) — right now both phones' screens would just say "13 Jul
     2026" with nothing distinguishing them if you're coordinating live
     between tills ("check Till A's total").
  2. *Bigger, scope once step 1 is in use:* an in-app "Import another till's
     export" action that reads a second phone's exported zip and merges its
     rows into a combined, read-only "Consolidated session" view, re-exported
     as one zip — instead of a manual spreadsheet merge every time. This
     merge stays read-only/reporting-only and is never written back into the
     live Room DB as if locally created, so it can't disturb the
     single-active-session rule each phone already enforces.
  Cash reconciliation (above) stays **per-till** rather than merged — each
  phone is one physical cash drawer, so consolidation should show both tills'
  floats/counts side by side, not combine them into one number.

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
        │   │   ├── LedgerDatabase.kt   # Room database (v4; additive 2→3, 3→4 migrations)
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

### Publishing an update (in-app update check)
The app checks for a newer build on launch by reading `update-manifest.json`
from this repo's `main` branch (raw URL, no auth — requires the repo to be
**public**). `UpdateChecker.MANIFEST_URL` points at it. When the manifest's
`versionCode` is higher than the installed build, a dismissible "Update
available" banner opens the `url` in the browser.

Each release, in the **same commit** that bumps `versionCode`/`versionName`:
1. Update `update-manifest.json` to the new `versionCode`, `versionName`, and
   the download `url` (a GitHub Release page, or any hosted APK link).
2. Publish the APK where `url` points (e.g. attach it to a GitHub Release).

The check is network-optional and fails safe: offline, a private repo, a 404,
or malformed JSON all resolve to "no update" and never block recording a sale.
To disable the feature, set `MANIFEST_URL` back to `""`.

### Debug builds
`./gradlew :app:assembleDebug` still works for development
(`app/build/outputs/apk/debug/app-debug.apk`), but it's signed with the
throwaway debug key: a phone can't upgrade between a debug and a release build
without uninstalling first. Don't hand debug builds to the team.

## Notes
- The Room database is **v4**. Every upgrade from v2 on is a **real, additive
  migration** — 2→3 added the trade tables, 3→4 added per-item notes, the
  session label + cash-reconciliation fields, and the `cash_adjustments` table;
  existing sessions/sales/trades are kept. Only the ancient pre-multi-item **v1**
  still falls back to a destructive wipe on upgrade (the deliberate clean-reset
  choice from when v2 shipped).
- Custom fonts (Nunito / Inter / Space Mono) are not yet bundled; the app uses
  Material3 default typography so it builds with no binary assets. Drop the font
  files into `res/font` and wire them into `Theme.kt` when they're ready.
- Version numbers in `libs.versions.toml` are recent known-good releases;
  Android Studio may suggest newer ones on sync — accepting them is fine.
- `minSdk 29` (Android 10) is a safe floor for the team's phones and supports
  adaptive icons and modern camera APIs. `targetSdk 35` (Android 15) — bump as
  newer stable Android releases land.
