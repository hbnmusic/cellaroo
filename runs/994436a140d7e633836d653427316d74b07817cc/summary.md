# CI run 43620566237 @ 994436a1
outcome: success

## Errors

## What went wrong

## Tests
tests=21 failed=0 skipped=0

## APKs
14458863 bytes  app/build/outputs/apk/debug/app-debug.apk
10121843 bytes  app/build/outputs/apk/release/app-release-unsigned.apk
10134155 bytes  app/build/outputs/apk/preview/app-preview.apk

## Signing
release key: absent (release APK is unsigned; preview APK is signed with the committed key)

## Log tail
> Task :app:processReleaseResources
> Task :app:optimizeReleaseResources
> Task :app:kspPreviewKotlin
> Task :app:kspReleaseKotlin
> Task :app:compilePreviewKotlin
> Task :app:compileReleaseKotlin
> Task :app:compilePreviewJavaWithJavac
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
> Task :app:mergeDexRelease
> Task :app:compileReleaseArtProfile
> Task :app:packagePreview
> Task :app:createPreviewApkListingFileRedirect
> Task :app:assemblePreview
> Task :app:packageRelease
> Task :app:createReleaseApkListingFileRedirect
> Task :app:assembleRelease
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/gradle-1794676841107.json

BUILD SUCCESSFUL in 3m 54s
148 actionable tasks: 133 executed, 15 from cache
