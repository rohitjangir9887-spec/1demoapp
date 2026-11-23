# Latest Android build diagnostics

Run: https://github.com/rohitjangir9887-spec/1demoapp/actions/runs/45150845808
Source commit: c5124f9380253581643ac96cbccae873480c335d

| Step | Outcome |
| --- | --- |
| Java setup | success |
| Android setup | success |
| Android SDK packages | success |
| Gradle setup | success |
| Firebase CI config | success |
| Debug keystore | success |
| Android compile | failure |

## Compiler and build errors
~~~
FAILURE: Build failed with an exception.
* What went wrong:
Caused by: org.gradle.api.internal.exceptions.MarkedVerificationException: There were failing tests. See the report at: file:///home/runner/work/1demoapp/1demoapp/app/build/reports/tests/testDebugUnitTest/index.html
~~~

## Failed unit tests
~~~
<testsuite name="com.example.ExampleRobolectricTest" tests="1" skipped="0" failures="1" errors="0" timestamp="2026-11-23T09:54:25.581Z" hostname="runnervm0hi8g" time="0.055">
  <testcase name="classMethod" classname="com.example.ExampleRobolectricTest" time="0.012">
    <failure message="java.lang.UnsupportedOperationException: Failed to create a Robolectric sandbox: Android SDK 36 requires Java 21 (have Java 17)" type="java.lang.UnsupportedOperationException">java.lang.UnsupportedO
<testsuite name="com.example.ExampleUnitTest" tests="1" skipped="0" failures="0" errors="0" timestamp="2026-11-23T09:54:25.646Z" hostname="runnervm0hi8g" time="0.013">
  <testcase name="addition_isCorrect" classname="com.example.ExampleUnitTest" time="0.006"/>
<testsuite name="com.example.JulieCoreTests" tests="8" skipped="0" failures="0" errors="0" timestamp="2026-11-23T09:54:25.667Z" hostname="runnervm0hi8g" time="0.276">
  <testcase name="httpErrorTaxonomyStopsOn401AndFailsOverOn404And429WithRetryAfter" classname="com.example.JulieCoreTests" time="0.046"/>
  <testcase name="longPrompt50kIsSplitIntoBoundedChunksWithoutLoss" classname="com.example.JulieCoreTests" time="0.022"/>
  <testcase name="nemotronFirstAndVisionNeverFallsBackToTextOnly" classname="com.example.JulieCoreTests" time="0.049"/>
  <testcase name="accessibilitySafetyNeverBypassesLockscreenOrReadsPasswords" classname="com.example.JulieCoreTests" time="0.016"/>
  <testcase name="catalogHasZeroGeminiModelsAndIsolatesNonChatModels" classname="com.example.JulieCoreTests" time="0.006"/>
  <testcase name="cryptoVaultBlocksInsecureOrPrivateUrlsAndAllowsPublicHttps" classname="com.example.JulieCoreTests" time="0.106"/>
  <testcase name="julieVoiceCommandsParseHindiHinglishAndEnglishAccurately" classname="com.example.JulieCoreTests" time="0.029"/>
  <testcase name="simpleChatBypassesManagerWhileComplexPromptsEngageManager" classname="com.example.JulieCoreTests" time="0.0"/>
<testsuite name="com.example.JulieOverlayCommandRouterTest" tests="2" skipped="0" failures="0" errors="0" timestamp="2026-11-23T09:54:25.951Z" hostname="runnervm0hi8g" time="0.012">
  <testcase name="ordinaryAiQuestionsAreNotDeviceActions" classname="com.example.JulieOverlayCommandRouterTest" time="0.006"/>
  <testcase name="explicitUserCommandsAreParsedWithoutExecutingAnything" classname="com.example.JulieOverlayCommandRouterTest" time="0.003"/>
~~~

## Tail of build log
~~~
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.executeWithNonEmptySources(AbstractSkipEmptyWorkStep.java:133)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:64)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:36)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.executeMutable(HandleStaleOutputsStep.java:77)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.executeMutable(HandleStaleOutputsStep.java:43)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:25)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.lambda$execute$0(AssignMutableWorkspaceStep.java:35)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:305)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:31)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:22)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:40)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:23)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:44)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:31)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:68)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:132)
	... 30 more


BUILD FAILED in 1m 58s
46 actionable tasks: 25 executed, 21 from cache
Configuration cache entry stored.
~~~
