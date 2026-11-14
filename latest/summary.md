# CI run 43626683163 @ e071a804
outcome: failure

## Errors
183:ViewModelFlowTest > scanThenShelfCheckThenUndo FAILED
188:> Task :app:testDebugUnitTest FAILED
223:Execution failed for task ':app:testDebugUnitTest'.

## What went wrong
222:* What went wrong:
223-Execution failed for task ':app:testDebugUnitTest'.
224-> There were failing tests. See the report at: file:///home/runner/work/cellaroo/cellaroo/app/build/reports/tests/testDebugUnitTest/index.html
225-
226-* Try:
227-> Run with --scan to get full insights.
228-
229-BUILD FAILED in 3m 45s
230-148 actionable tasks: 133 executed, 15 from cache

## Tests
tests=36 failed=1 skipped=0
- app.cellaroo.ViewModelFlowTest.scanThenShelfCheckThenUndo
    java.lang.AssertionError: Expected value to be true.
    java.lang.AssertionError: Expected value to be true.
    	at org.junit.Assert.fail(Assert.java:89)
    	at kotlin.test.junit.JUnitAsserter.fail(JUnitSupport.kt:56)
    	at kotlin.test.Asserter.assertTrue(Assertions.kt:765)
    	at kotlin.test.junit.JUnitAsserter.assertTrue(JUnitSupport.kt:30)
    	at kotlin.test.Asserter.assertTrue(Assertions.kt:775)
    	at kotlin.test.junit.JUnitAsserter.assertTrue(JUnitSupport.kt:30)

## APKs
14523400 bytes  app/build/outputs/apk/debug/app-debug.apk
10120691 bytes  app/build/outputs/apk/release/app-release-unsigned.apk
10128911 bytes  app/build/outputs/apk/preview/app-preview.apk

## Signing
release key: absent (release APK is unsigned; preview APK is signed with the committed key)

## Log tail
> Task :app:mergePreviewGlobalSynthetics
> Task :app:processPreviewJavaRes
> Task :app:mergePreviewJavaResource
> Task :app:dexBuilderRelease
> Task :app:mergeReleaseGlobalSynthetics FROM-CACHE
> Task :app:processReleaseJavaRes
> Task :app:mergeReleaseJavaResource
> Task :app:mergeDexPreview
> Task :app:compilePreviewArtProfile
> Task :app:mergeDexRelease
> Task :app:packagePreview
> Task :app:createPreviewApkListingFileRedirect
> Task :app:assemblePreview
> Task :app:compileReleaseArtProfile
> Task :app:packageRelease
> Task :app:createReleaseApkListingFileRedirect
> Task :app:assembleRelease
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/gradle-1794680856318.json

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:testDebugUnitTest'.
> There were failing tests. See the report at: file:///home/runner/work/cellaroo/cellaroo/app/build/reports/tests/testDebugUnitTest/index.html

* Try:
> Run with --scan to get full insights.

BUILD FAILED in 3m 45s
148 actionable tasks: 133 executed, 15 from cache
