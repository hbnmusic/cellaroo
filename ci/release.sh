#!/usr/bin/env bash
# Publishes a GitHub release for the current versionName if none exists yet.
set -e
VER=$(grep '^cellaroo.versionName=' gradle.properties | cut -d= -f2)
TAG="v${VER}"
case "$VER" in 0.0.*) echo "Development version $VER: not publishing."; exit 0;; esac

mkdir -p dist
cp app/build/outputs/apk/preview/app-preview.apk "dist/cellaroo-${VER}-preview.apk"
if [ -f app/build/outputs/apk/release/app-release.apk ]; then
  cp app/build/outputs/apk/release/app-release.apk "dist/cellaroo-${VER}.apk"
fi

if gh release view "$TAG" >/dev/null 2>&1; then
  if [ "$FORCE" = "true" ]; then
    gh release upload "$TAG" dist/*.apk --clobber
    echo "Refreshed assets of $TAG."
  else
    echo "Release $TAG already exists."
  fi
else
  gh release create "$TAG" dist/*.apk --target "$GITHUB_SHA" --title "Cellaroo ${VER}" --notes-file ci/release-notes.md
fi
