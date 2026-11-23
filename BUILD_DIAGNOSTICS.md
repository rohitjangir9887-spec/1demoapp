# Latest Android build diagnostics

Run: https://github.com/rohitjangir9887-spec/1demoapp/actions/runs/45174343780
Source commit: c1d445c696715f8e3e79564dbb08a55928f70ec7

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
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/ui/screens/ChatScreen.kt:617:17 'fun ColumnScope.AnimatedVisibility(visible: Boolean, modifier: Modifier = ..., enter: EnterTransition = ..., ex
FAILURE: Build failed with an exception.
* What went wrong:
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.btapi.BuildToolsApiCompilationWork
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: 'fun ColumnScope.AnimatedVisibility(visible: Boolean, modifier: Modifier = ..., enter: EnterTransition = ..., exit: ExitTransition = ..., label: Str
~~~

## Failed unit tests
~~~
~~~

## Tail of build log
~~~
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:44)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:41)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.workers.internal.AbstractWorker.executeWrappedInBuildOperation(AbstractWorker.java:41)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.execute(NoIsolationWorkerFactory.java:58)
	at org.gradle.workers.internal.DefaultWorkerExecutor.lambda$submitWork$0(DefaultWorkerExecutor.java:176)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runExecution(DefaultConditionalExecutionQueue.java:194)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.access$700(DefaultConditionalExecutionQueue.java:127)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner$1.run(DefaultConditionalExecutionQueue.java:169)
	at org.gradle.internal.Factories$1.create(Factories.java:30)
	at org.gradle.internal.work.DefaultWorkerLeaseService.lambda$withLocksAcquired$0(DefaultWorkerLeaseService.java:269)
	at org.gradle.internal.work.ResourceLockStatistics$1.measure(ResourceLockStatistics.java:42)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocksAcquired(DefaultWorkerLeaseService.java:267)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:259)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:132)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runBatch(DefaultConditionalExecutionQueue.java:164)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.run(DefaultConditionalExecutionQueue.java:133)
	... 2 more


BUILD FAILED in 41s
36 actionable tasks: 20 executed, 16 from cache
Configuration cache entry stored.
~~~
