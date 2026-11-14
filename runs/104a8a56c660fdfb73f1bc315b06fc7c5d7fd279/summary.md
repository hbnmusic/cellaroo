# CI run 43622897193 @ 104a8a56
outcome: failure

## Errors
103:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:120:35 Unresolved reference 'StatsScreen'.
104:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:132:38 Unresolved reference 'SettingsScreen'.
105:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:133:39 Unresolved reference 'LocationsScreen'.
108:> Task :app:compileDebugKotlin FAILED
168:> Task :app:compilePreviewKotlin FAILED
169:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:120:35 Unresolved reference 'StatsScreen'.
170:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:132:38 Unresolved reference 'SettingsScreen'.
171:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:133:39 Unresolved reference 'LocationsScreen'.
173:> Task :app:compileReleaseKotlin FAILED
174:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:120:35 Unresolved reference 'StatsScreen'.
175:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:132:38 Unresolved reference 'SettingsScreen'.
176:e: file:///home/runner/work/cellaroo/cellaroo/app/src/main/java/app/cellaroo/ui/AppRoot.kt:133:39 Unresolved reference 'LocationsScreen'.
184:Execution failed for task ':app:compileDebugKotlin'.
198:Execution failed for task ':app:compilePreviewKotlin'.
212:Execution failed for task ':app:compileReleaseKotlin'.

## What went wrong
183:* What went wrong:
184-Execution failed for task ':app:compileDebugKotlin'.
185-> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
186-   > Compilation error. See log for more details
187-
188-* Try:
189-> Run with --stacktrace option to get the stack trace.
190-> Run with --info or --debug option to get more log output.
191-> Run with --scan to get full insights.
192-> Get more help at https://help.gradle.org.
193-==============================================================================
194-
195-2: Task failed with an exception.
196------------
197:* What went wrong:
198-Execution failed for task ':app:compilePreviewKotlin'.
199-> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
200-   > Compilation error. See log for more details
201-
202-* Try:
203-> Run with --stacktrace option to get the stack trace.
204-> Run with --info or --debug option to get more log output.
205-> Run with --scan to get full insights.
206-> Get more help at https://help.gradle.org.
207-==============================================================================
208-
209-3: Task failed with an exception.
210------------
211:* What went wrong:
212-Execution failed for task ':app:compileReleaseKotlin'.
213-> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
214-   > Compilation error. See log for more details
215-
216-* Try:
217-> Run with --stacktrace option to get the stack trace.
218-> Run with --info or --debug option to get more log output.
219-> Run with --scan to get full insights.
220-> Get more help at https://help.gradle.org.
221-==============================================================================
222-
223-BUILD FAILED in 3m 50s
224-116 actionable tasks: 103 executed, 13 from cache

## Tests
tests=15 failed=0 skipped=0

## APKs

## Signing
release key: absent (release APK is unsigned; preview APK is signed with the committed key)

## Log tail
2: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':app:compilePreviewKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
==============================================================================

3: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':app:compileReleaseKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
==============================================================================

BUILD FAILED in 3m 50s
116 actionable tasks: 103 executed, 13 from cache
