#!/usr/bin/env bash
# Writes a compact summary of this CI run to the ci-reports branch so it can be read with a plain git fetch.
set +e
SHA="${GITHUB_SHA}"
OUT="_reports/runs/${SHA}"
mkdir -p "$OUT"
touch build.log
{
  echo "# CI run ${GITHUB_RUN_ID} @ ${SHA:0:8}"
  echo "outcome: ${BUILD_OUTCOME}"
  echo
  echo "## Errors"
  grep -nE "^e: |error:|FAILED$|Could not (resolve|find|get)|\[ksp\]|Execution failed|AAPT|Manifest merger failed" build.log | cut -c1-600 | head -160
  echo
  echo "## What went wrong"
  grep -n -A30 "What went wrong" build.log | cut -c1-500 | head -140
  echo
  echo "## Tests"
  python3 ci/tests_summary.py
  echo
  echo "## APKs"
  find app/build/outputs/apk -name "*.apk" -printf "%s bytes  %p\n" 2>/dev/null
  echo
  echo "## Signing"
  if [ -n "$CELLAROO_KEYSTORE_FILE" ]; then echo "release key: present"; else echo "release key: absent (release APK is unsigned; preview APK is signed with the committed key)"; fi
  echo
  echo "## Log tail"
  tail -30 build.log | cut -c1-400
} > "$OUT/summary.md" 2>&1

if [ -d app/build/outputs/roborazzi ]; then
  mkdir -p "$OUT/screens"
  cp app/build/outputs/roborazzi/*.png "$OUT/screens/" 2>/dev/null
fi
if [ -d app/schemas ]; then cp -r app/schemas "$OUT/schemas"; fi

cd _reports || exit 0
rm -rf latest && cp -r "runs/${SHA}" latest
# Keep the branch small: only the 5 newest runs.
ls -1t runs | tail -n +6 | while read -r d; do rm -rf "runs/$d"; done
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add -A
git commit -q -m "report ${SHA:0:8} (${BUILD_OUTCOME})"
git push -q origin HEAD:ci-reports
