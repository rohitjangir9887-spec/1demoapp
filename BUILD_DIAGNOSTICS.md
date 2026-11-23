# Latest Android build diagnostics

Run: https://github.com/rohitjangir9887-spec/1demoapp/actions/runs/45179462839
Source commit: 83ed2d6bd1abc464c0875609d2d23b8bd80fd862

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
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/data/ai/AuraSwarmEngine.kt:1243:41 Unresolved reference 'Locale'.
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/data/model/TrendingAiProjectsCatalog.kt:37:41 Unresolved reference 'CODING'.
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/data/model/TrendingAiProjectsCatalog.kt:46:41 Unresolved reference 'DEVICE_ACTION'.
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/overlay/JulieFloatingBubbleService.kt:552:44 Unresolved reference 'modelUsed' on receiver of type 'SwarmResult'.
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/overlay/JulieFloatingBubbleService.kt:553:46 Unresolved reference 'participatingModels' on receiver of type 'SwarmResult'.
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/overlay/JulieFloatingBubbleService.kt:554:48 Unresolved reference 'groundingUrls' on receiver of type 'SwarmResult'.
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/overlay/JulieFloatingBubbleService.kt:555:48 Unresolved reference 'groundingUrls' on receiver of type 'SwarmResult'.
e: file:///home/runner/work/1demoapp/1demoapp/app/src/main/java/com/example/voice/JulieBackgroundVoiceService.kt:233:47 Unresolved reference 'EXTRA_ADDITIONAL_LANGUAGES'.
FAILURE: Build failed with an exception.
* What went wrong:
         Unresolved reference 'Locale'.
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.btapi.BuildToolsApiCompilationWork
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Unresolved reference 'Locale'.
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


BUILD FAILED in 42s
36 actionable tasks: 16 executed, 20 from cache
Configuration cache entry stored.
~~~
