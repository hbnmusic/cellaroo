# CI run 43619805471 @ c906c4ac
outcome: success

## Errors

## What went wrong

## Tests
tests=15 failed=0 skipped=0

## APKs
14224044 bytes  app/build/outputs/apk/debug/app-debug.apk
9969210 bytes  app/build/outputs/apk/release/app-release-unsigned.apk
9981522 bytes  app/build/outputs/apk/preview/app-preview.apk

## Signing
release key: absent (release APK is unsigned; preview APK is signed with the committed key)

## Log tail
> Task :app:extractReleaseVersionControlInfo
> Task :app:packageDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:assembleDebug
> Task :app:collectReleaseDependencies
> Task :app:sdkReleaseDependencyData
> Task :app:writeReleaseAppMetadata
> Task :app:writeReleaseSigningConfigVersions
> Task :app:kspReleaseKotlin
> Task :app:optimizeReleaseResources
> Task :app:compileReleaseKotlin
> Task :app:compileReleaseJavaWithJavac
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
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/gradle-1794676340034.json

BUILD SUCCESSFUL in 2m 45s
144 actionable tasks: 129 executed, 15 from cache
