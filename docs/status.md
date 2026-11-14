# Cellaroo: build status and handoff (2026-11-14)

Read this first when resuming. It records what was built, what was verified, where the build differs from
`cellarlens-build-brief-v2.md`, and what still needs the user.

## State

- Repo: `hbnmusic/cellaroo`, branch `main`. Version **0.1.0** (versionCode 2).
- Release: https://github.com/hbnmusic/cellaroo/releases/tag/v0.1.0 with `cellaroo-0.1.0-preview.apk`
  (app id `app.cellaroo.preview`, signed with the key committed in `keystores/debug.keystore`).
- The `release` variant (`app.cellaroo`) builds but is **unsigned** until the four signing secrets are added.
- CI: `.github/workflows/build.yml` on every push to `main`. Last run green: 36 tests, 3 APKs.
- CI results: branch `ci-reports`, `runs/<commit sha>/summary.md` and `screens/*.png`.

## What exists

All of the brief's phases 0–6: scan with bounding-box review, shelf check (reconcile) with undo, paste parser with
merge preview, AI estimates (badged ESTIMATE, background worker), cellar list with search/filter/sort, wine detail and edit,
Open Tonight with cuisine chips, stats and charts, ZIP/JSON/CSV export and restore, settings with encrypted key storage,
shelves manager.

## How it was verified

| Check | Result |
|---|---|
| `:core` logic (readiness, verdicts, ranking, text parsing, label matching, shelf-check planning) | 15 unit tests pass (also run locally with kotlinc) |
| Gemini response parsing, request body, CSV | 6 unit tests pass |
| Room database through the repository (add, remove, undo, shelf check, cascade, backup round trip) | 5 tests pass on a real in-memory SQLite |
| View model flows end to end without network (scan save, re-scan joins wine, shelf check, undo, paste merge) | 2 tests pass |
| Every screen composes with a seeded database | 8 screenshot tests pass; PNGs reviewed by eye |
| Box-to-photo mapping | Checked visually on `samples/costco-cart-6.png` in the screenshots |
| Published APK | Downloaded and inspected: manifest, prompts, 16 KB-aligned arm64 libraries |

**Not verified, because nothing here can run them:**
- The app has never run on a phone. Camera capture, the permission dialog, Android Keystore encryption,
  the photo picker and the file pickers are untested.
- No request has been sent to Gemini (no key in CI, API blocked from the workspace). The prompts, the response schemas
  and the default model id `gemini-3.5-flash` are untested against the live API. The app falls back to
  `gemini-2.5-flash` on HTTP 404 and retries without the schema on HTTP 400.

## Differences from the brief

| Brief | Built | Why |
|---|---|---|
| DataStore + Tink for the API key | SharedPreferences + AES-256-GCM key in the Android Keystore | No extra libraries or native code; same protection model |
| OkHttp + Retrofit | OkHttp only | One endpoint; less to get wrong without a local compiler |
| Coil | Own small image loader (`LocalImage`) | Coil's hardware bitmaps did not render in screenshot tests; the app only shows local files |
| Roborazzi Gradle plugin, verify mode | Roborazzi library in record mode inside the normal test task | Smoke tests plus images for review; no golden files |
| Release created from a `v*` tag | Release created when `cellaroo.versionName` changes | Avoids depending on tag pushes from the workspace |
| Pairing tags by dish type | Tags are the user's cuisines + cheese & charcuterie + solo; each pairing carries a dish | User's cuisine list |
| Missing bottles default to Drunk | Drunk only when every seen bottle matched; otherwise Still here | An unknown label may be a misread of the missing one |
| User rating scale undecided | 1–5 stars | Quick to tap |
| Migration test harness | Not built; schema v1 JSON is exported to `ci-reports/latest/schemas` | Nothing to migrate from yet |

Defaults taken without asking (brief §2): purchase price per bottle in USD; market price flagged STALE after 12 months;
Auto Backup on for the database, off for the API key and photos; applicationId `app.cellaroo` (confirmed by user).

## Parked: needs the user

1. **Install and try it.** Download the preview APK from the release on the Pixel, enter the Gemini key in
   Stats → settings, tap Test key, then run the checklist in brief §11. Report what breaks.
2. **Signing secrets.** Add `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (values are in
   `cellaroo-signing-secrets.txt`, sent in chat). Then bump the version (0.1.1) so a signed `cellaroo-<v>.apk` is published.
   Do this before putting real data in: the preview and release apps do not share data (export/restore moves it).
3. **Repo housekeeping.** Make `main` the default branch and delete `access-probe` (the workspace cannot delete branches).
4. **`GEMINI_API_KEY_CI` secret** (optional, separate key). Then change `ci/prompt-eval.trigger` to run the prompts
   on the samples; results land in `ci-reports/prompt-eval/latest`.
5. **Ground truth for `samples/costco-cart-6.png`:** vintages of Luis R Crianza and Marqués de Riscal, the exact producer
   on the Muscadet, the grapes on ViBo.
6. **More samples:** a photo of a home shelf, the same shelf minus one bottle, and plain-text copies of a CellarTracker,
   Wine-Searcher and Vivino page into `samples/paste/`.
7. **Public vs private.** The repo, the sample photo, the screenshots in `ci-reports` and the release APK are public now.
   When the repo goes private, release downloads need a GitHub login on the phone.

## Resuming in a new chat

`add_repo` hbnmusic/cellaroo with push access, clone, read this file and the brief. The workspace cannot compile Android:
push to `main`, then read `ci-reports/runs/<sha>/summary.md`. `:core` can be compiled and tested locally with kotlinc
from JetBrains' GitHub releases.
