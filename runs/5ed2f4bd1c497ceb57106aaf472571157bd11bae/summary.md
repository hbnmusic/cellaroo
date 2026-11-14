# CI run 43624459000 @ 5ed2f4bd
outcome: success

## Errors

## What went wrong

## Tests
tests=34 failed=0 skipped=0

## APKs
14835918 bytes  app/build/outputs/apk/debug/app-debug.apk
10367603 bytes  app/build/outputs/apk/release/app-release-unsigned.apk
10379915 bytes  app/build/outputs/apk/preview/app-preview.apk

## Signing
release key: absent (release APK is unsigned; preview APK is signed with the committed key)

## Log tail
> Task :app:mergePreviewGlobalSynthetics
> Task :app:processPreviewJavaRes
> Task :app:sdkReleaseDependencyData
> Task :app:writeReleaseAppMetadata
> Task :app:writeReleaseSigningConfigVersions
> Task :app:mergePreviewJavaResource
> Task :app:mergeDexPreview
> Task :app:compilePreviewArtProfile
> Task :app:packagePreview
> Task :app:createPreviewApkListingFileRedirect
> Task :app:assemblePreview

> Task :app:compileReleaseKotlin
w: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/Paste.kt:76:21 'val LocalClipboardManager: ProvidableCompositionLocal<ClipboardManager>' is deprecated. Use LocalClipboard instead which supports suspend functions.
w: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/Tonight.kt:141:46 Unnecessary safe call on a non-null receiver of type 'PairingTag'.

> Task :app:compileReleaseJavaWithJavac
> Task :app:dexBuilderRelease
> Task :app:mergeReleaseGlobalSynthetics FROM-CACHE
> Task :app:processReleaseJavaRes
> Task :app:mergeReleaseJavaResource
> Task :app:mergeDexRelease
> Task :app:compileReleaseArtProfile
> Task :app:packageRelease
> Task :app:createReleaseApkListingFileRedirect
> Task :app:assembleRelease
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/gradle-1794679435922.json

BUILD SUCCESSFUL in 5m 5s
148 actionable tasks: 133 executed, 15 from cache
