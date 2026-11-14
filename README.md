# Cellaroo

A sideloaded Android app for a personal wine collection. It reads bottle labels from photos, tracks what you paid
and what bottles are worth, and recommends what to drink, what to keep, and whether a wine is better alone or with food.

Everything is stored on the phone. The only network calls go to Google's Gemini API, with a key you enter in the app.

## What it does

- **Scan**: photograph one bottle or a lineup. Gemini returns one entry per bottle with a bounding box; you review, edit and save.
- **Shelf check**: photograph a shelf and the app compares it with what it expects there, then proposes what was drunk, moved or added. One tap to apply, one tap to undo.
- **Paste stats**: paste text from CellarTracker, Wine-Searcher, Vivino, a review or a retailer email. A pattern matcher and Gemini extract scores, drinking window, price, notes and pairings; you pick what to merge.
- **Estimates**: for wines with no pasted data, Gemini estimates a drinking window, serving style and pairings. These are labelled ESTIMATE and never overwrite pasted or typed values. Prices and critic scores are never estimated.
- **Open tonight**: ranks bottles that are in their window, with cuisine filters.
- **Stats & backup**: totals, charts, ZIP/JSON/CSV export and restore.

## Layout

| Path | Contents |
|---|---|
| `core/` | Pure Kotlin logic: readiness and verdicts, ranking, text parsing, label matching, shelf-check planning. Unit-tested. |
| `app/` | Android app: Room database, Gemini client, image pipeline, Compose UI. |
| `app/src/main/assets/prompts/` | The prompts and response schemas sent to Gemini. |
| `ci/` | Scripts used by the GitHub workflows. |
| `samples/` | Photos and pasted texts for `prompt-eval`. |

## Building

Builds run in GitHub Actions (`.github/workflows/build.yml`) on every push to `main`:
unit tests, database tests, screenshot smoke tests, then debug, preview and release APKs.
A summary of each run and the screenshots are written to the `ci-reports` branch.

Locally: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` with JDK 17 and the Android SDK.

### Variants

| Variant | App id | Signing |
|---|---|---|
| `release` | `app.cellaroo` | Your private key, from repo secrets. Unsigned if the secrets are missing. |
| `preview` | `app.cellaroo.preview` | Key committed in `keystores/debug.keystore`. Same code as release. |
| `debug` | `app.cellaroo.debug` | Same committed key. |

### Release signing secrets

Settings → Secrets and variables → Actions:
`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

### Publishing

Change `cellaroo.versionName` (and raise `cellaroo.versionCode`) in `gradle.properties`. The next green build on `main`
creates the GitHub release `v<versionName>` with the APKs attached.

## Prompt evaluation

`.github/workflows/prompt-eval.yml` sends everything in `samples/` to Gemini using the app's prompts and writes the answers,
with boxes drawn on the photos, to `ci-reports/prompt-eval/latest`. It needs the `GEMINI_API_KEY_CI` secret.
