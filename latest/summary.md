# CI run 43624053296 @ 60d83bdc
outcome: failure

## Errors
152:> Task :app:compileDebugUnitTestKotlin FAILED
153:e: file:///home/runner/work/cellaroo/cellaroo/app/src/test/java/app/cellaroo/ScreenshotTests.kt:81:24 Unresolved reference 'cellaroo'.
216:Execution failed for task ':app:compileDebugUnitTestKotlin'.

## What went wrong
215:* What went wrong:
216-Execution failed for task ':app:compileDebugUnitTestKotlin'.
217-> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
218-   > Compilation error. See log for more details
219-
220-* Try:
221-> Run with --stacktrace option to get the stack trace.
222-> Run with --info or --debug option to get more log output.
223-> Run with --scan to get full insights.
224-> Get more help at https://help.gradle.org.
225-
226-BUILD FAILED in 3m 27s
227-146 actionable tasks: 131 executed, 15 from cache

## Tests
tests=15 failed=0 skipped=0

## APKs
14835918 bytes  app/build/outputs/apk/debug/app-debug.apk
10379915 bytes  app/build/outputs/apk/preview/app-preview.apk
10367603 bytes  app/build/outputs/apk/release/app-release-unsigned.apk

## Signing
release key: absent (release APK is unsigned; preview APK is signed with the committed key)

## Log tail
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
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/gradle-1794679180554.json

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugUnitTestKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 3m 27s
146 actionable tasks: 131 executed, 15 from cache
