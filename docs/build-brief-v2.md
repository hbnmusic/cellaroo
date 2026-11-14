# Cellaroo: Build Brief v2 (pre-build decisions)

Prepared 2026-11-14 for the build session (Claude Fable 5.2).
This brief **supersedes conflicting parts of `cellarlens-spec-v1-original.md`** (the original spec, saved alongside). Anything in v1 that this brief does not change still applies (UI chip lists, sort options, KPI cards, chart types, etc.).

Tags: `[inference]` = my reasoning or estimate, not a verified fact. Everything else was stated by the user or checked against a source listed in §14.

---

## 0. How the build session should use this doc

1. Read §1 (locked decisions) and §2 (open items). Ask the user to confirm the §2 defaults before Phase 1 if they are present; if not, proceed on the defaults and say so.
2. Collect the inputs in §13 before the phases that need them.
3. Follow the phases in §10. Each phase ends with a green CI run on GitHub and a push, so a usage limit or a new chat loses no work.
4. If this is a new chat: attach the repo with `add_repo` (push access), clone it, read this brief from the project.

---

## 1. Locked decisions (user, 2026-11-14)

| # | Topic | Decision | Consequence for the build |
|---|---|---|---|
| D1 | Build path | **GitHub Actions only**, private repo | The Claude workspace cannot compile Android: Google Maven, Maven Central, Gradle distribution and plugin portal all return 403 at the egress proxy; GitHub is reachable. Every compile, test and APK signing happens on GitHub-hosted runners. |
| D2 | Photo scans | **Add + reconcile a location** | Structured locations, per-bottle inventory, and a reconcile flow (§7). |
| D3 | Storage | **Bottles upright on shelves/counter** | Labels face out, so reconcile by photo is feasible. |
| D4 | Market price & critic scores | **Paste/manual only.** No auto-fetch, no web search, no grounding. | Drop WineAPI.io, `WINEAPI_KEY`, "Auto-Fetch Stats", grounded vintage synthesis. Price and score fields are only ever filled by paste or manual entry. |
| D5 | AI provider | **Gemini API with the user's AI Studio key, free tier only.** No Claude API. | The user's Claude Pro plan does not include API usage (billed separately), so Claude is out. One provider to integrate. The Gemini endpoint is blocked from the Claude workspace, so live prompt tests run in CI (§9.5) or on the phone. |
| D6 | Collection size | **Under 50 bottles** today | Free quota is not a constraint; plain lists, no paging. |
| D7 | Import | **None, start fresh** | Only importer is the app's own backup restore. |
| D8 | Recurring cost | $0 `[inference from D1, D4, D5]` | Gemini free tier + GitHub Free private repo (2,000 Actions minutes/month). |
| D9 | App name | **Cellaroo** (replaces CellarLens) | Display name, package, file names. |
| D10 | Repo | **`hbnmusic/cellaroo`** (created by user 2026-11-14; was **public** at check time, see §15) | Build session pushes here. |
| D11 | AI estimates (was O1) | **Yes**: Gemini estimates drinking windows, solo-vs-paired and pairings, badged `ESTIMATE`; never prices or critic scores | Enables recommendations for bottles without pasted data. |

---

## 2. Open items, with recommended defaults

Confirm or change these before Phase 1. Items marked ⚠ are hard to change after the first install.

| # | Item | Recommended default | Why it matters |
|---|---|---|---|
| O1 | ~~AI estimates~~ | **Locked as D11 (yes).** | — |
| O2 ⚠ | **applicationId** | `app.cellaroo` (debug build: `app.cellaroo.debug`) | Changing it later makes Android treat it as a different app; local data does not carry over. |
| O3 | Repo | `hbnmusic/cellaroo`, **private** | You create the empty repo (I could not confirm I can create one from here). Private = 2,000 free Actions minutes/month; public = unlimited minutes but public code. |
| O4 ⚠ | Release signing key | I generate the keystore in the workspace (JDK is present), send you the file + passwords for your password manager, and it goes into GitHub as repo secrets. Not committed to the repo (v1 said to commit it). | Losing this key means you cannot install updates over the existing app; you would have to uninstall, which deletes the on-device database. |
| O5 | Phone model + Android version | *needed from you* | Camera behaviour and minSdk sanity check; on-device QA. |
| O6 | Purchase price convention | Per bottle, USD, what you actually paid (tax included, shipping excluded), plus purchase date and where bought (optional) | Needed for cost basis and gain/loss. |
| O7 | Market price staleness | Show "stale" after 12 months since the pasted price date | Paste-only prices age silently otherwise. |
| O8 | Pairing chips | v1's five (Roasted meats / air-fryer mains, Rich braises & stews, Hot pot / Asian flavors, Cheese & charcuterie, Solo sipping) **plus** Vietnamese / SE Asian, Seafood & shellfish, Pizza & pasta, Spicy food | Chips filter on a controlled tag list (§5.4), so the list must be fixed before the AI prompt is written. Tell me the cuisines you actually cook. |
| O9 | Locations | *needed from you*: names of your shelves/areas and roughly how many bottles each holds | Seeds the Locations table and the reconcile flow. |
| O10 | CI prompt evaluation | Yes, with a **separate** AI Studio key stored as a GitHub secret (`GEMINI_API_KEY_CI`), revocable any time | Only way to test real Gemini responses on your sample photos before the APK is on your phone. |
| O11 | Free-tier data use | Acknowledge: on the free tier Google may use prompts and photos to improve its products. This applies to **every** call (scans, paste parsing, estimates), not only lookups. | Your choice of free tier; noted so it is a conscious one. |
| O12 | Gemini models | Default `gemini-3.5-flash` for vision and text; fallback `gemini-2.5-flash`. Model IDs are a Settings value, with a picker filled from the ListModels endpoint. | Google retires models (Gemini 2.0 Flash was shut down June 1, 2026); a hard-coded ID would break the app. |
| O13 | Your AI Studio project tier | Confirm in AI Studio that the project behind your key is on the **Free** tier (no billing account linked). | `[inference]` A project with billing enabled is on a paid tier and would be charged; a free-tier project gets rate-limited (HTTP 429) instead. |
| O14 | Android Auto Backup (Google Drive) | On for the Room database; off for API keys (they are bound to this device's Keystore and would not decrypt after restore) and for photos (size) | Second safety net besides manual ZIP export. |

---

## 3. Changes to the v1 spec

| v1 spec item | v2 change | Reason |
|---|---|---|
| §1.2 `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` / `WINEAPI_KEY` | **Gemini key only.** Remove Anthropic and WineAPI fields. | D4, D5. I could not find WineAPI.io as a working service; Wine-Searcher's API is a paid trade product; Vivino has no public API. |
| §1.2 EncryptedSharedPreferences | **DataStore + Tink AEAD**, keyset wrapped by an Android Keystore key | `androidx.security:security-crypto` was deprecated at 1.1.0-alpha07 (April 2025). |
| §1.3 / §5 "`./gradlew` compiles cleanly" verified in-session | Verified **in GitHub Actions** | D1. |
| §5.5 keystore committed at `app/cellarlens-release.keystore` | Keystore in GitHub secrets; a fixed **debug** keystore is committed | O4. Debug keystores are generated fresh on each CI runner `[inference]`, so without a committed one every debug APK has a different signature and cannot install over the last one. |
| §2 Camera: `camera-compose` | Keep; use `CameraXViewfinder` from CameraX 1.5.x (stable) | camera-compose reached stable in 2025. |
| §3 W1 `duplicate_count_in_photo` | **Remove.** One detection per physical bottle, each with its own box; the app groups identical labels. | Reconcile needs per-bottle counts and boxes. |
| §3 W1 bounding box `[ymin, xmin, ymax, xmax]` 0–1000 | Keep (this is Gemini's native box format). Validate on device: 4 ints, 0–1000, min < max; drop or flag invalid boxes. | Gemini is trained for this format. |
| §3 W2 Path A (Auto-Fetch, WineAPI, grounded synthesis, regional vintage tables) | **Removed.** Replaced by O1 "Estimate profile (AI)" if confirmed. | D4. |
| §3 W2 "Batch Enrich All Unscored Bottles" (WorkManager) | Becomes **"Estimate all bottles without a window"** (only if O1 = yes), rate-limited WorkManager job | D4, O1. |
| §3 W2 `data_source_badge` (ONLINE_REPO / WEB_SYNTHESIS / PASTED_TEXT / MANUAL) | **Per-field provenance**: `SCAN`, `PASTE`, `AI_ESTIMATE`, `MANUAL` (+ date) | Several sources can contribute to one wine; one badge per wine cannot show which field came from where. |
| §3 W2 readiness `HOLD / READY_TO_DRINK / AT_PEAK / PAST_PEAK` | Add `UNKNOWN`; add `peakEndYear`; add a `windowClosing` flag (§5.1) | v1 has no peak end, so AT_PEAK had no defined end; bottles without a window had no state. |
| §3 W2 critic scores as one avg + free-text sources | **CriticScore table**, one row per source, raw text + normalized 0–100 | Pasted text mixes 100-pt, 20-pt (Jancis Robinson), 5-star (Vivino) and range scores. |
| §3 W2 regex ranges (years 1950–2035, scores 80–100, `$XX–$XXXX`) | Widened (§8) | Drinking windows routinely run past 2035; prices have commas, per-case quotes, other currencies. |
| Project description: "price purchased at" | **Added** purchase price/date/source per bottle (O6) | Missing from v1. |
| Project description: "drink alone or paired" | **Added** `servingMode` (SOLO / FOOD / EITHER) + one-line reason, serving temp, decant time | Missing from v1. |
| §4 `WineBottleEntity` with `quantity` | **Split** into Wine (label-level) + Bottle (physical unit) + Location (§4) | Per-bottle purchase prices, per-shelf placement, reconcile. |
| §4 `tastingNotes` | Split into `referenceNotes` (pasted critic/retailer text) and `myNotes` | Your notes should not be overwritten by a paste. |
| §2 minify | **Release minify off** for v1 `[inference]` | R8 keep-rule mistakes with Retrofit/kotlinx.serialization fail only at runtime on the phone, which CI cannot catch. Size does not matter for sideloading. |
| §1 targetSdk/compileSdk 35 | targetSdk 35; **compileSdk 36 if current AndroidX releases require it** `[inference: verify in Phase 0]` | Recent AndroidX versions may demand compileSdk 36. |
| §5.4 unit tests | Kept, plus reconcile matching, score normalization, window parsing edge cases, and screenshot tests (§11) | No emulator is available (no KVM) in CI or the workspace. |

---

## 4. Data model v2 (Room, schema v1, `exportSchema = true`)

**WineEntity** (one row per label + vintage + bottle size)
- `id` PK; `producer`; `wineName`; `appellationRegion`; `country`; `vintage: Int?` (null = NV); `wineType` enum (RED, WHITE, ROSE, SPARKLING, DESSERT, FORTIFIED, **OTHER**); `grapesJson`; `abvPercent?`; `bottleSizeMl` (default 750)
- `normalizedKey` (lowercase, accent-folded producer + name + vintage/NV + size), indexed, used for matching
- Window: `drinkFromYear?`, `peakStartYear?`, `peakEndYear?`, `drinkUntilYear?`, `windowSource` + `windowUpdatedAt`
- Serving: `servingMode?` (SOLO / FOOD / EITHER), `servingReason?`, `servingTempC?`, `decantMinutes?`, `servingSource`
- Pairing: `pairingTagsJson` (controlled vocabulary, §5.4), `pairingNotes?`, `pairingSource`
- Notes: `referenceNotes?`, `myNotes?`, `vintageSummary?`
- Market price: `marketPriceUsd?`, `marketPriceAsOf?`, `marketPriceSourceText?` (always PASTE or MANUAL, per D4)
- `labelImagePath?`; `createdAt`; `updatedAt`

**BottleEntity** (one row per physical bottle)
- `id` PK; `wineId` FK → Wine (cascade restrict); `locationId?` FK → Location (set null)
- `status` enum: IN_CELLAR, CONSUMED, GIFTED, SOLD, LOST
- `purchasePriceUsd?`, `purchaseDate?`, `purchaseSource?`
- `cropImagePath?`; `addedVia` (SCAN, PASTE, MANUAL, RECONCILE); `addedAt`; `removedAt?`
- Indexes: `wineId`, `locationId`, `status`
- "Quantity" shown in the UI = count of IN_CELLAR bottles for a wine.

**LocationEntity**: `id`, `name`, `sortOrder`, `description?`, `lastReconciledAt?`

**CriticScoreEntity**: `id`, `wineId` FK, `source` (WA, VINOUS, JS, WS, DECANTER, JR, JEB_DUNNUCK, WINE_ENTHUSIAST, VIVINO, CELLARTRACKER, OTHER), `sourceLabel`, `rawText`, `score100: Float`, `rangeLow100?`, `rangeHigh100?`, `addedAt`

**ConsumptionLogEntity**: `id`, `bottleId` FK, `wineSnapshotTitle`, `consumedDate`, `occasion?`, `pairedWith?`, `userRating?` (0–100 or 5-star, decide in Phase 1), `personalNotes?`

**ScanEntity**: `id`, `kind` (ADD, RECONCILE), `locationId?`, `imagePath`, `modelId`, `rawResponseJson`, `createdAt` (kept for debugging prompts)

**PasteLogEntity**: `id`, `wineId?`, `rawText`, `parsedJson`, `appliedFieldsJson`, `createdAt`

**ChangeSetEntity** (undo support for reconcile and batch saves): `id`, `kind`, `payloadJson`, `createdAt`, `undone: Boolean`

Derived values are computed, not stored: readiness, verdict, quantity, cost basis, value, gain.

---

## 5. Recommendation logic (deterministic, on device)

All proposed defaults; tune after real use. `y` = current year.

### 5.1 Readiness
- `UNKNOWN`: no `drinkFromYear` and no `drinkUntilYear`.
- `HOLD`: `y < drinkFromYear`.
- `AT_PEAK`: `peakStartYear ≤ y ≤ (peakEndYear ?: drinkUntilYear)`.
- `READY_TO_DRINK`: inside the window but not at peak (before peak start, or after peak end and still ≤ `drinkUntilYear`).
- `PAST_PEAK`: `y > drinkUntilYear`.
- `windowClosing` flag: `drinkUntilYear − y ≤ 1` while READY or AT_PEAK.
- Partial windows: only `drinkUntil` → READY until then; only `drinkFrom` → HOLD, then READY with no end.

### 5.2 Verdict line (per wine, `n` = bottles in cellar)
- HOLD → "Hold until {drinkFrom}".
- READY and `n ≥ 3` → "Ready. Open one now and keep the rest toward {peakStart}" (if a peak is known).
- READY and `n < 3` → "Drink any time through {drinkUntil}" or "Best from {peakStart}".
- AT_PEAK → "At peak. Drink by {peakEnd ?: drinkUntil}".
- `windowClosing` → "Drink this year".
- PAST_PEAK → "Past its window. Open soon; it may be declining".
- UNKNOWN → "Add a drinking window (paste, or estimate if O1 is on)".

### 5.3 Open Tonight ranking
Candidates: READY, AT_PEAK (PAST_PEAK shown in a separate "Use soon" section).
`score = 40·urgency + 25·peak + 20·quality + 15·pairing`
- `urgency = clamp(1 − yearsLeft/10, 0, 1)`, `yearsLeft = (drinkUntil ?: y+10) − y`
- `peak = 1.0` AT_PEAK, `0.5` READY
- `quality` = best available of my rating, mean critic score (0–100) or 88 if none, mapped 85–100 → 0–1
- `pairing` (only when a chip is selected) = 1 if the chip's tag is in `pairingTags`; for "Solo sipping", 1 if `servingMode` is SOLO, 0.5 if EITHER; otherwise 0

### 5.4 Pairing tag vocabulary (controlled; chips map to tags)
`ROAST_RED_MEAT, BRAISE_STEW, HOT_POT, VIETNAMESE_SE_ASIAN, SPICY, SEAFOOD, POULTRY_PORK, PIZZA_PASTA, CHEESE_CHARCUTERIE, VEGETARIAN, DESSERT, SOLO`
Final list depends on O8. The AI prompt and the paste normalizer may only emit these tags.

### 5.5 Serving-mode fallback (when no estimate or manual value) `[inference]`
SPARKLING → EITHER; DESSERT/FORTIFIED → SOLO; WHITE/ROSE → EITHER; RED within 5 years of vintage → FOOD; older RED → EITHER. Shown as a heuristic, not as data.

---

## 6. AI calls (Gemini, free tier)

| Call | Model (default) | Input | Output |
|---|---|---|---|
| Scan (add or reconcile) | `gemini-3.5-flash` | One JPEG (long edge 2048 px, quality 85) + prompt | `ScanResult { bottles: [ { index, box_2d[4], producer?, wine_name?, appellation_region?, country?, vintage?, vintage_visible: bool, wine_type?, primary_grapes[], abv_percent?, bottle_size_ml?, label_legible: bool, confidence: 0–1 } ] }` |
| Paste normalizer | `gemini-3.5-flash` | Pasted text + regex pre-extraction | `PasteResult { identity?, scores: [ {source, raw, score100, range?} ], window?, market_price? {amount, currency, per_bottle, as_of?}, reference_notes?, pairing_tags[], serving? }` |
| Profile estimate (O1) | `gemini-3.5-flash` | Wine identity only | `Estimate { window {from, peak_start, peak_end, until}, serving_mode, serving_reason, serving_temp_c, decant_minutes, pairing_tags[], pairing_notes, style_summary }`. The prompt forbids prices and critic scores. |

Rules:
- REST `generateContent` with `x-goog-api-key` header, `responseMimeType: application/json` + JSON schema; temperature low. Verify the exact field names against current Gemini docs in Phase 2 (fetchable via WebFetch even though the API itself is blocked from the workspace). Gemini docs also show a newer "interactions" endpoint; use `generateContent` unless Phase 2 finds a reason not to.
- One detection per physical bottle; left to right, then top to bottom; null (not a guess) for anything unreadable, especially vintage.
- **Reconcile scans do not send the expected bottle list to the model**: matching happens on device, so the model cannot "see" bottles because it was told they should be there.
- Validate every response on device (schema, box ranges, enum values case-insensitively). Store the raw response in ScanEntity.
- Apply EXIF rotation **before** sending the image and before mapping boxes; map boxes to the displayed image accounting for `ContentScale.Fit` letterboxing.
- Errors: 429 → back off and show "Free quota reached; resets at midnight Pacific (3 AM Eastern)"; network failure → keep the photo and offer "Retry scan" (offline capture, process later).
- Settings: API key (masked), "Test key" (ListModels), model pickers, O1 toggle, free-tier data-use notice (O11).

---

## 7. Location reconcile flow

1. Scan tab → **Check a location** → pick a location.
2. Take one or more photos; guidance: one shelf row per photo, no overlap between photos.
3. App runs the scan call per photo and pools detections.
4. Matching per detection: exact `normalizedKey`, else fuzzy token-set similarity ≥ 0.85 on producer + name **and** equal vintage. Detections with unreadable vintage or `confidence < 0.6` go to a "Needs review" group.
5. Per wine: `expected` = IN_CELLAR bottles at this location; `seen` = matched detections.
6. Review screen with photo + boxes, grouped:
   - **Matched**: `min(expected, seen)`; no action.
   - **Missing** (`expected > seen`): per bottle choose Drunk (logs consumption, optional rating), Moved to…, Still here (hidden), Gifted/Sold.
   - **Extra** (`seen > expected`): if the wine has bottles elsewhere → default "Moved here from {location}"; else "New bottle" (add card with purchase price).
   - **Unknown wine**: new-wine card prefilled from the detection.
   - **Needs review**: user picks a match or dismisses.
7. Nothing changes until **Apply**; one Room transaction; saved as a ChangeSet with **Undo**.
8. Updates `lastReconciledAt` on the location.

---

## 8. Paste parser ("Paste Stats from Web" + Quick Paste)

Deterministic pass first (works offline), then the Gemini normalizer, then a diff preview (current vs parsed, per field, with checkboxes) → **Confirm & Merge**.

Regex coverage:
- **Vintage**: 1900–current year; detect "NV" / "Non-Vintage".
- **Drinking window**: "Drink 2026-2038", en/em dashes, "Drinking window: 2026 – 2038", "Best 2028 through 2045", "Best from 2027 through 2045", "now–2030" / "now through 2035" (now = current year), "Hold until 2027"; window years up to 2100.
- **Scores**: "95 pts/points", "(93-95)" barrel ranges, "95+", source prefixes (WA/RP, Vinous/AG, JS, WS, JD, WE, Decanter), Jancis Robinson "17.5/20" → convert, Vivino "4.2" stars only when the text says ratings/stars, CellarTracker community "CT 92.3".
- **Prices**: "$1,250", "$45.99", "USD 45", "€38", "£30" (stored with currency, flagged non-USD), "per bottle", "/750ml", "case of 6 … $540" → $90/bottle, Wine-Searcher "Avg Price (ex-tax)".
- Test fixtures come from your real pasted samples (§13), anonymized if needed.

---

## 9. Build & CI pipeline (GitHub Actions)

### 9.1 Workspace limits (checked 2026-11-14)
- Blocked (403 at egress proxy): `dl.google.com`, `maven.google.com`, `services.gradle.org`, `plugins.gradle.org`, `repo1.maven.org`, `repo.maven.apache.org`, `generativelanguage.googleapis.com`.
- Reachable: `github.com`, `api.github.com`, GitHub release downloads, PyPI, npm.
- Present: JDK 21, Gradle 8.14.3 (can generate the wrapper locally `[inference]`), 2 CPUs, 7 GB RAM. No Android SDK, no `/dev/kvm` (no emulator).
- So: no local compile or test of anything that depends on Android or Maven libraries.

### 9.2 Workflows
- `build.yml` (push, PR, manual): checkout → Temurin JDK → `gradle/actions/setup-gradle` (cache) → decode keystore from secrets → `./gradlew testDebugUnitTest verifyRoborazziDebug assembleDebug assembleRelease` → upload APKs, test reports and screenshots as artifacts → **write a summary to an orphan `ci-reports` branch**.
- `release.yml` (tag `v*`): build signed release → GitHub Release with `cellaroo-vX.Y.Z.apk` (download on the phone while signed in to GitHub, since the repo is private).
- `prompt-eval.yml` (manual, O10): runs a JVM task that sends `samples/*.jpg` and `samples/paste/*.txt` to Gemini with `GEMINI_API_KEY_CI`, writes the JSON outputs and a scored comparison to `ci-reports`.

### 9.3 Reading CI results from the workspace
- `ci-reports/<run-id>/summary.md`: pass/fail per task, first ~200 lines of compiler errors, failed tests with messages, APK paths and sizes, screenshot PNGs. The build session reads it with `git fetch` (GitHub is reachable), which avoids depending on Actions log downloads that redirect to other hosts `[inference: unverified whether those hosts are blocked]`.
- Workflow needs `permissions: contents: write`.

### 9.4 Signing
- Release keystore: PKCS12, RSA 4096, ~30-year validity, generated with `keytool` in the workspace; secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. You keep the file and passwords (O4).
- Committed `app/debug.keystore` so debug APKs from different runs can update each other.
- Release and debug use different applicationIds, so both can be installed side by side.

### 9.5 Minutes and loop time `[inference]`
- Estimate 6–12 minutes per full CI run with Gradle caching; ~40 runs during the build ≈ 240–480 of the 2,000 free private-repo minutes.
- Each fix-and-rebuild cycle takes one CI run, so the build session should batch changes per push and keep pure logic (parsers, readiness, matching) in a `:core` Kotlin module with no Android dependencies. Optional speed-up: install `kotlinc` from JetBrains' GitHub releases to syntax-check `:core` locally.

### 9.6 Versions
Pick current stable versions in Phase 0 (AGP, Kotlin + Compose compiler plugin, Compose BOM, Room + KSP, CameraX 1.5.x, WorkManager, Coil 3, kotlinx.serialization, OkHttp/Retrofit, DataStore, Tink, Roborazzi + Robolectric). The first CI run validates resolution.

---

## 10. Build phases (exit criterion for each: green `build.yml` + push)

| Phase | Scope | Needs from user |
|---|---|---|
| 0 | Repo, Gradle wrapper, empty Compose app with 4-tab shell and theme, `build.yml`, `ci-reports`, signing, first release tag → APK installs on your phone | Empty repo (O3), keystore secrets (O4), phone (O5) |
| 1 | `:core` (readiness, verdicts, ranking, score/price/window parsing, matching) + Room schema, DAOs, repository + unit tests | §2 confirmations |
| 2 | Settings + DataStore/Tink key storage, Gemini client, paste flow (regex + normalizer + diff preview), `prompt-eval.yml` | AI Studio key(s), paste samples |
| 3 | CameraX viewfinder (single/lineup guide, flash), photo picker, image pipeline, scan → bounding-box review → batch save | Sample photos |
| 4 | Locations manager + reconcile flow + undo | Location list (O9), reconcile test photos |
| 5 | Cellar list (search, filters, sorts), wine detail, − stepper with undo + consumption log, Open Tonight | Chip list (O8) |
| 6 | Stats KPIs (incl. cost basis, value coverage, gain) + Canvas charts; ZIP/JSON/CSV export, restore; Auto Backup rules; O1 estimate worker | — |
| 7 | Screenshot test pass, polish, `v0.1.0` release, on-device QA checklist (§11) with you | Your QA run |

---

## 11. Verification

**Unit tests (`:core` and `:app` JVM)**
- Scan JSON parsing; box validation (range, ordering, count); case-insensitive enums; nulls.
- Paste extraction on your real CellarTracker / Wine-Searcher / Vivino / critic / retailer samples.
- Readiness and verdicts across all states and partial windows; `windowClosing`; year boundaries.
- Score normalization (100-pt, ranges, 20-pt, 5-star); price parsing (commas, per-case, currencies).
- Reconcile matching (exact, fuzzy, vintage mismatch, duplicates, missing/extra counts).
- Room migrations test harness (schema v1 baseline).

**Screenshot tests (Roborazzi on Robolectric, JVM)**: each tab, scan review with boxes, reconcile review, paste diff, empty states, dark theme. PNGs go to `ci-reports` so the build session can look at them.

**Not testable in CI**: camera hardware, permissions dialogs, real Gemini calls (except `prompt-eval`), share-sheet/SAF pickers.

**On-device QA checklist (you, Phase 7)**: install from release; enter key + Test key; single-label scan; 6+ bottle lineup scan; edit/delete a detection; save; reconcile a shelf after removing one bottle; paste a CellarTracker block and merge; − stepper + undo; Open Tonight with a chip; export ZIP; uninstall a debug copy and restore; install an update over the previous release and confirm data survives.

---

## 12. Risks

| Risk | Mitigation |
|---|---|
| Slow fix loop (CI only) | `:core` module, batched pushes, `ci-reports` summaries |
| Gemini free-tier limits or model retirement | Model IDs in Settings + ListModels picker; 429 handling; ScanEntity keeps raw responses for re-tests |
| Box accuracy on glare/foil labels | Review screen with per-box edit/delete; `label_legible`/`confidence` flags; prompt-eval on your photos |
| Lost release key | O4 backup; ZIP export before any reinstall |
| Free-tier data use by Google | O11 notice in Settings |
| Long build session hits plan usage limits `[inference]` | Phase-by-phase pushes; this brief in the project; resume in a new chat via `add_repo` |
| EXIF rotation / letterbox mapping bugs | Explicit handling (§6) + screenshot test with a rotated fixture |

---

## 13. Inputs to gather before (or at the start of) the build

1. Create an empty **private** GitHub repo (`hbnmusic/cellaroo`).
2. Confirm your AI Studio project is on the **Free** tier (O13); have the key ready; optionally create a second key for CI (O10).
3. **Sample photos** (phone camera, normal lighting): 3 single-label close-ups, 2 full-shelf lineups, the same shelf with one bottle removed, and 1 difficult case (glare, foil, angled).
4. **Paste samples** as plain text: a CellarTracker wine page, a Wine-Searcher page, a Vivino page, a critic review, a retailer email.
5. Phone model and Android version (O5).
6. Location names and rough bottle counts (O9).
7. Pairing chips / cuisines you cook (O8).
8. Answers on O1, O2, O6, O7, O14 if you want something other than the defaults.

---

## 14. Sources

- Claude API vision limits and coordinates: https://platform.claude.com/docs/en/build-with-claude/vision
- Claude API pricing (web search $10/1,000): https://platform.claude.com/docs/en/about-claude/pricing
- Paid Claude plans do not include API usage: https://support.claude.com/en/articles/9876003
- Gemini pricing / free tier / data use / Gemini 2.0 shutdown: https://ai.google.dev/gemini-api/docs/pricing
- Gemini rate limits (RPD reset at midnight Pacific; limits shown in AI Studio): https://ai.google.dev/gemini-api/docs/rate-limits
- Gemini bounding boxes `[ymin, xmin, ymax, xmax]` 0–1000: https://ai.google.dev/gemini-api/docs/image-understanding
- Jetpack Security Crypto deprecation: https://developer.android.com/reference/kotlin/androidx/security/crypto/package-summary
- CameraX 1.5 and camera-compose: https://android-developers.googleblog.com/2025/11/introducing-camerax-15-powerful-video.html
- GitHub plans (2,000 Actions minutes on Free): https://docs.github.com/en/get-started/learning-about-github/githubs-plans
- Wine-Searcher API (paid, trade): https://www.wine-searcher.com/trade/api

---

## 15. Readiness check (2026-11-14, before Phase 0)

**Repo access from the Claude workspace (`hbnmusic/cellaroo`)**
- Clone: works (repo was empty).
- Push: works, **including files under `.github/workflows/`** (tested with a `workflow_dispatch`-only probe file).
- Branch deletion: **blocked** by the session's git proxy. A leftover branch `access-probe` (one probe workflow file, manual trigger only, does nothing) exists and, because it was the first push, became the repo's default branch. After Phase 0 pushes `main`, the user sets `main` as default and deletes `access-probe` in GitHub.
- GitHub API: repo info, releases and Actions **run listing** are readable. The Actions **secrets** API path is blocked by the proxy, so the **user adds secrets** in GitHub → Settings → Secrets and variables → Actions.
- Consequence: CI results come back through the `ci-reports` branch (git) plus the runs API for status.
- Visibility at check time: **public**. Public = unlimited Actions minutes, but code, `samples/` photos, paste samples and `ci-reports` screenshots would be visible to anyone. Secrets stay hidden either way. Decision pending (O15).

**Sample inputs received**
- `samples/costco-cart-6.png` (user photo, 2026-11-14): 6 bottles in two rows in a shopping cart. Expected detections, to be confirmed by the user as ground truth:
  1. Viu Manent, La Capilla Single Vineyard Cabernet Sauvignon 2022, Colchagua, Chile
  2. Marchesi di Barolo, Gavi (vintage not visible)
  3. Belvedere des Sept Bois de Fer (?), Muscadet Sèvre et Maine sur Lie (vintage not visible)
  4. Viu Manent, ViBo Viñedo Centenario (Cabernet Sauvignon / Malbec?), Colchagua, Chile; carries a "93" sticker with no visible source
  5. Luis R (Bodegas y Viñedos), Rioja Crianza, vintage 2021 `[inference: small print]`
  6. Marqués de Riscal, Rioja (Reserva?), vintage 2021 `[inference: small print]`
- Prompt notes from this photo: retailer/award stickers ("93") must be captured as `sticker_score` with `source: UNKNOWN`, never as a critic score; vintage must be null when not printed on the front label.

**Still needed before or during Phase 0**

| # | Item | Blocks |
|---|---|---|
| O2 ⚠ | Confirm applicationId `app.cellaroo` | Phase 0 |
| O15 | Keep `hbnmusic/cellaroo` public, or make it private | Phase 0 (before samples are committed) |
| O4 | After Claude generates the keystore: user saves it and adds 4 secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) | First signed release (debug builds can run before this) |
| O5 | Phone model + Android version | Phase 0 install test |
| O10/O13 | Second Gemini key as secret `GEMINI_API_KEY_CI`; confirm AI Studio project is Free tier | Phase 2 |
| O8, O9 | Pairing chips / cuisines; shelf names | Phases 4–5 |
| §13.3–4 | More photos (single labels, a home shelf, same shelf minus one bottle) and paste samples | Phases 2–4 |
