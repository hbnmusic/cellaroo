# CI run 43623374401 @ 794b8274
outcome: success

## Errors

## What went wrong

## Tests
tests=21 failed=0 skipped=0

## APKs
14835695 bytes  app/build/outputs/apk/debug/app-debug.apk
10367603 bytes  app/build/outputs/apk/release/app-release-unsigned.apk
10379915 bytes  app/build/outputs/apk/preview/app-preview.apk

## Signing
release key: absent (release APK is unsigned; preview APK is signed with the committed key)

## Log tail

> Task :app:compilePreviewJavaWithJavac

> Task :app:compileReleaseKotlin
w: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/Paste.kt:76:21 'val LocalClipboardManager: ProvidableCompositionLocal<ClipboardManager>' is deprecated. Use LocalClipboard instead which supports suspend functions.
w: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/Tonight.kt:141:46 Unnecessary safe call on a non-null receiver of type 'PairingTag'.

> Task :app:dexBuilderPreview
> Task :app:mergePreviewGlobalSynthetics
> Task :app:processPreviewJavaRes
> Task :app:compileReleaseJavaWithJavac
> Task :app:mergePreviewJavaResource
> Task :app:dexBuilderRelease
> Task :app:mergeReleaseGlobalSynthetics FROM-CACHE
> Task :app:processReleaseJavaRes
> Task :app:mergeReleaseJavaResource
> Task :app:mergeDexPreview
> Task :app:compilePreviewArtProfile
> Task :app:packagePreview
> Task :app:createPreviewApkListingFileRedirect
> Task :app:assemblePreview
> Task :app:mergeDexRelease
> Task :app:compileReleaseArtProfile
> Task :app:packageRelease
> Task :app:createReleaseApkListingFileRedirect
> Task :app:assembleRelease
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/gradle-1794678713424.json

BUILD SUCCESSFUL in 4m 52s
148 actionable tasks: 133 executed, 15 from cache
