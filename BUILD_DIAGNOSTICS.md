# Latest Android build diagnostics

Run: https://github.com/rohitjangir9887-spec/1demoapp/actions/runs/45175231304
Source commit: 153181076707ff2e9469f20892d603b6eb546684

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
<testsuite name="com.example.ExampleRobolectricTest" tests="2" skipped="0" failures="0" errors="0" timestamp="2026-11-23T12:33:59.262Z" hostname="runnervm0hi8g" time="14.705">
  <testcase name="main activity launches and navigates all tabs without crash" classname="com.example.ExampleRobolectricTest" time="9.683"/>
  <testcase name="read string from context" classname="com.example.ExampleRobolectricTest" time="0.28"/>
<testsuite name="com.example.ExampleUnitTest" tests="1" skipped="0" failures="0" errors="0" timestamp="2026-11-23T12:34:13.970Z" hostname="runnervm0hi8g" time="0.0">
  <testcase name="addition_isCorrect" classname="com.example.ExampleUnitTest" time="0.0"/>
<testsuite name="com.example.JulieCoreTests" tests="11" skipped="0" failures="1" errors="0" timestamp="2026-11-23T12:34:13.973Z" hostname="runnervm0hi8g" time="0.043">
  <testcase name="httpErrorTaxonomyStopsOn401AndFailsOverOn404And429WithRetryAfter" classname="com.example.JulieCoreTests" time="0.002"/>
  <testcase name="speechSanitizerStripsMarkdownUrlsAndTablePipes" classname="com.example.JulieCoreTests" time="0.003"/>
  <testcase name="longPrompt50kIsSplitIntoBoundedChunksWithoutLoss" classname="com.example.JulieCoreTests" time="0.0"/>
  <testcase name="multiStepVoiceCommandParsesYouTubeWorkflowIntent" classname="com.example.JulieCoreTests" time="0.006">
    <failure message="org.junit.ComparisonFailure: expected:&lt;Android AI Agent[]&gt; but was:&lt;Android AI Agent[  karo]&gt;" type="org.junit.ComparisonFailure">org.junit.ComparisonFailure: expected:&lt;Android AI Age
  <testcase name="markdownParserProducesRealTablesHeadingsAndCodeBlocks" classname="com.example.JulieCoreTests" time="0.004"/>
  <testcase name="nemotronFirstAndVisionNeverFallsBackToTextOnly" classname="com.example.JulieCoreTests" time="0.002"/>
  <testcase name="accessibilitySafetyNeverBypassesLockscreenOrReadsPasswords" classname="com.example.JulieCoreTests" time="0.001"/>
  <testcase name="catalogHasZeroGeminiModelsAndIsolatesNonChatModels" classname="com.example.JulieCoreTests" time="0.0"/>
  <testcase name="cryptoVaultBlocksInsecureOrPrivateUrlsAndAllowsPublicHttps" classname="com.example.JulieCoreTests" time="0.02"/>
  <testcase name="julieVoiceCommandsParseHindiHinglishAndEnglishAccurately" classname="com.example.JulieCoreTests" time="0.004"/>
  <testcase name="simpleChatBypassesManagerWhileComplexPromptsEngageManager" classname="com.example.JulieCoreTests" time="0.0"/>
<testsuite name="com.example.JulieOverlayCommandRouterTest" tests="2" skipped="0" failures="0" errors="0" timestamp="2026-11-23T12:34:14.017Z" hostname="runnervm0hi8g" time="0.003">
  <testcase name="ordinaryAiQuestionsAreNotDeviceActions" classname="com.example.JulieOverlayCommandRouterTest" time="0.002"/>
  <testcase name="explicitUserCommandsAreParsedWithoutExecutingAnything" classname="com.example.JulieOverlayCommandRouterTest" time="0.001"/>
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
	... 61 more


BUILD FAILED in 1m 20s
49 actionable tasks: 29 executed, 20 from cache
Configuration cache entry stored.
~~~
