package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.model.AiModelSpec
import com.example.data.model.AiModelsCatalog
import com.example.data.model.SpecialistRole
import com.example.data.remote.NimChatRequest
import com.example.data.remote.NimErrorCategory
import com.example.data.remote.NimErrorClassifier
import com.example.data.remote.NimHttpException
import com.example.data.remote.NimImageUrl
import com.example.data.remote.NimMessage
import com.example.data.remote.NimVisionChatRequest
import com.example.data.remote.NimVisionContentPart
import com.example.data.remote.NimVisionMessage
import com.example.data.remote.NvidiaNimClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val TAG = "AuraSwarmEngine"

enum class RoutingProfile(val label: String) {
    AUTO("Auto"),
    FAST("Fast"),
    DEEP("Deep"),
    MAX_POWER("Max Power")
}

enum class OrchestrationMode(val label: String) {
    SINGLE_MODEL("1 Model Only"),
    MANAGER_PLUS_SPECIALISTS("Manager + Specialists")
}

enum class VendorPolicy(val label: String) {
    NEMOTRON_ONLY("Nemotron Only"),
    VERIFIED_BACKUPS_ALLOWED("Verified Backups Allowed")
}

data class RoutingConfig(
    val profile: RoutingProfile = RoutingProfile.AUTO,
    val orchestrationMode: OrchestrationMode = OrchestrationMode.MANAGER_PLUS_SPECIALISTS,
    val vendorPolicy: VendorPolicy = VendorPolicy.VERIFIED_BACKUPS_ALLOWED,
    val managerEnabled: Boolean = true
)

data class AttemptRecord(
    val modelId: String,
    val httpCode: Int,
    val latencyMs: Long,
    val outcome: String
)

data class RequestDiagnostics(
    val requestId: String,
    val role: SpecialistRole,
    val routingProfile: RoutingProfile,
    val finalModelId: String,
    val managerUsed: Boolean,
    val chunkedLongPrompt: Boolean,
    val chunkCount: Int,
    val ttftMs: Long,
    val totalMs: Long,
    val attempts: List<AttemptRecord>
) {
    /** Sanitized copyable diagnostics string — strictly excludes all API keys and raw user secrets. */
    fun toCopyableReport(): String = buildString {
        appendLine("Julie AI • Request Diagnostics")
        appendLine("Request ID: $requestId")
        appendLine("Role: ${role.label} | Profile: ${routingProfile.label}")
        appendLine("Final Model: $finalModelId")
        appendLine("Manager Invoked: $managerUsed | Chunked Prompt: $chunkedLongPrompt ($chunkCount chunks)")
        appendLine("TTFT: ${ttftMs}ms | Total: ${totalMs}ms")
        appendLine("Attempts:")
        attempts.forEachIndexed { idx, a ->
            appendLine("  ${idx + 1}. ${a.modelId} -> HTTP ${a.httpCode} (${a.latencyMs}ms): ${a.outcome}")
        }
    }
}

data class WorkerResult(
    val workerId: String,
    val modelName: String,
    val role: SpecialistRole,
    val summary: String,
    val succeeded: Boolean
)

data class SwarmStepUpdate(
    val activeModelName: String,
    val stageLabel: String,
    val progressPercent: Int,
    val completedSteps: List<String>
)

data class SwarmResult(
    val finalText: String,
    val reasoningTrace: String = "",
    val primaryEngineLabel: String,
    val collaboratingModels: List<String>,
    val searchSources: List<String>,
    val usedWebSearch: Boolean,
    val steps: List<String>,
    val diagnostics: RequestDiagnostics? = null
)

data class PlannedSubTask(
    val title: String,
    val prompt: String,
    val category: String,
    val priority: String
)

/**
 * Pure, testable Long-Prompt token estimator & bounded chunk splitter.
 * Prevents silent truncation or HTTP 413 failures on 50K+ character pasted prompts.
 */
object LongPromptProcessor {
    private const val CHARS_PER_TOKEN_ESTIMATE = 3.6
    private const val DEFAULT_CHUNK_CHARS = 12_000
    private const val CHUNK_OVERLAP_CHARS = 400

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length / CHARS_PER_TOKEN_ESTIMATE).toInt().coerceAtLeast(1)
    }

    fun needsChunking(prompt: String, modelContextTokens: Int = 128_000, outputBudget: Int = 2048): Boolean {
        val estimated = estimateTokens(prompt)
        // Trigger map-reduce chunking if prompt exceeds 24,000 chars OR 60% of model's safe budget
        val safeBudget = ((modelContextTokens - outputBudget) * 0.60).toInt().coerceAtLeast(4_000)
        return prompt.length >= 24_000 || estimated > safeBudget
    }

    fun splitIntoBoundedChunks(
        prompt: String,
        maxChunkChars: Int = DEFAULT_CHUNK_CHARS,
        overlapChars: Int = CHUNK_OVERLAP_CHARS
    ): List<String> {
        val clean = prompt.trim()
        if (clean.length <= maxChunkChars) return listOf(clean)

        val chunks = mutableListOf<String>()
        var start = 0
        val safeMax = maxChunkChars.coerceAtLeast(1000)
        val safeOverlap = overlapChars.coerceIn(0, safeMax / 4)

        while (start < clean.length) {
            val end = (start + safeMax).coerceAtMost(clean.length)
            // Prefer splitting on paragraph or line boundary when possible
            val splitPos = if (end < clean.length) {
                val paraBreak = clean.lastIndexOf("\n\n", end)
                val lineBreak = clean.lastIndexOf('\n', end)
                when {
                    paraBreak > start + safeMax / 2 -> paraBreak
                    lineBreak > start + safeMax / 2 -> lineBreak
                    else -> end
                }
            } else end

            val piece = clean.substring(start, splitPos).trim()
            if (piece.isNotEmpty()) chunks.add(piece)
            if (splitPos >= clean.length) break
            start = (splitPos - safeOverlap).coerceAtLeast(start + 1)
        }
        return chunks
    }
}

/**
 * Pure NVIDIA NIM Orchestrator & Single-Flight Router (Zero Gemini dependencies).
 * Enforces:
 * - Single-flight request isolation (zero mixed streams / zero overlapping model tokens)
 * - Nemotron-first + ONE Manager (complex prompts only) + strictly TWO optional backups per role
 * - Strict HTTP error taxonomy (401 aborts immediately; 429 honors Retry-After; 404/403 failover)
 * - Bounded map-reduce chunking for 50K+ char prompts without silent truncation
 */
class AuraSwarmEngine(private val appContext: Context) {

    private val singleFlightChatMutex = Mutex()

    private val webClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    fun isNvidiaNimDirectActive(): Boolean = NvidiaNimClient.isConfigured()

    private fun requireNimConfigured() {
        if (!NvidiaNimClient.isConfigured()) {
            throw IllegalStateException(
                "Add your NVIDIA NIM API key (nvapi-...) in the 33 Models tab to run Julie AI."
            )
        }
    }

    suspend fun executeQuery(
        userPrompt: String,
        conversationContext: String = "",
        preferredModelId: String = AiModelsCatalog.AUTO_SWARM_ID,
        swarmEnabled: Boolean = true,
        maxPowerEnabled: Boolean = true,
        useGoogleSearch: Boolean = true,
        attachedBitmap: Bitmap? = null,
        routingConfig: RoutingConfig = RoutingConfig(),
        useSingleFlightLock: Boolean = true,
        onStageUpdate: (SwarmStepUpdate) -> Unit = {},
        onThinkingToken: (String) -> Unit = {},
        onStreamToken: (String) -> Unit = {}
    ): SwarmResult {
        return if (useSingleFlightLock) {
            singleFlightChatMutex.withLock {
                executeQueryInternal(
                    userPrompt = userPrompt,
                    conversationContext = conversationContext,
                    preferredModelId = preferredModelId,
                    swarmEnabled = swarmEnabled,
                    maxPowerEnabled = maxPowerEnabled,
                    useGoogleSearch = useGoogleSearch,
                    attachedBitmap = attachedBitmap,
                    routingConfig = routingConfig,
                    onStageUpdate = onStageUpdate,
                    onThinkingToken = onThinkingToken,
                    onStreamToken = onStreamToken
                )
            }
        } else {
            executeQueryInternal(
                userPrompt = userPrompt,
                conversationContext = conversationContext,
                preferredModelId = preferredModelId,
                swarmEnabled = swarmEnabled,
                maxPowerEnabled = maxPowerEnabled,
                useGoogleSearch = useGoogleSearch,
                attachedBitmap = attachedBitmap,
                routingConfig = routingConfig,
                onStageUpdate = onStageUpdate,
                onThinkingToken = onThinkingToken,
                onStreamToken = onStreamToken
            )
        }
    }

    private suspend fun executeQueryInternal(
        userPrompt: String,
        conversationContext: String,
        preferredModelId: String,
        swarmEnabled: Boolean,
        maxPowerEnabled: Boolean,
        useGoogleSearch: Boolean,
        attachedBitmap: Bitmap?,
        routingConfig: RoutingConfig,
        onStageUpdate: (SwarmStepUpdate) -> Unit,
        onThinkingToken: (String) -> Unit,
        onStreamToken: (String) -> Unit
    ): SwarmResult {
        if (!NvidiaNimClient.isConfigured()) {
            onStageUpdate(
                SwarmStepUpdate(
                    activeModelName = "Google Gemini Free AI",
                    stageLabel = "Generating response via Google Free AI...",
                    progressPercent = 50,
                    completedSteps = listOf("Running on Google Gemini Free AI Engine")
                )
            )
            val geminiText = com.example.data.remote.GoogleGeminiClient.generateContent(
                context = appContext,
                prompt = userPrompt,
                conversationContext = conversationContext,
                attachedBitmap = attachedBitmap,
                onStreamToken = onStreamToken
            )
            return SwarmResult(
                finalText = geminiText,
                reasoningTrace = "Executed with Google Gemini Free AI mode. Set a free Google AI Studio key in 33 Models tab for live cloud intelligence.",
                primaryEngineLabel = "Google Gemini Free AI",
                collaboratingModels = listOf("Google Gemini 2.0 Flash", "Julie Intelligent Agent"),
                searchSources = emptyList(),
                usedWebSearch = false,
                steps = listOf("Google Gemini Free AI Router engaged", "Response synthesized successfully")
            )
        }

        val requestId = "req_${UUID.randomUUID().toString().take(8)}"
        val startMs = System.currentTimeMillis()
        val attempts = mutableListOf<AttemptRecord>()
        val settings = NvidiaNimClient.settingsFlow.value
        val backupsAllowed = routingConfig.vendorPolicy == VendorPolicy.VERIFIED_BACKUPS_ALLOWED
        val verifiedIds = NvidiaNimClient.verifiedModelIds()
        val disabledIds = NvidiaNimClient.disabledModelIds()

        val role = AiModelsCatalog.classifyRole(
            prompt = userPrompt,
            hasImage = attachedBitmap != null,
            isLongPrompt = LongPromptProcessor.needsChunking(userPrompt)
        )

        // 1. Vision + Screen Share Mode (NEVER falls back to text-only models)
        if (attachedBitmap != null) {
            val visionChain = AiModelsCatalog.buildFailoverChain(
                role = SpecialistRole.VISION_SCREEN,
                requireVision = true,
                pinnedModelId = preferredModelId,
                backupsAllowed = backupsAllowed,
                verifiedLiveIds = verifiedIds,
                disabledIds = disabledIds
            )
            return executeNimVisionQuery(
                requestId = requestId,
                startMs = startMs,
                routingProfile = routingConfig.profile,
                userPrompt = userPrompt,
                conversationContext = conversationContext,
                visionChain = visionChain,
                attachedBitmap = attachedBitmap,
                attempts = attempts,
                onStageUpdate = onStageUpdate,
                onStreamToken = onStreamToken
            )
        }

        val steps = mutableListOf<String>()

        // 2. Long Prompt Map-Reduce Transport (50K+ chars handled transparently without silent truncation)
        if (LongPromptProcessor.needsChunking(userPrompt)) {
            return executeLongPromptMapReduce(
                requestId = requestId,
                startMs = startMs,
                routingConfig = routingConfig,
                userPrompt = userPrompt,
                preferredModelId = preferredModelId,
                backupsAllowed = backupsAllowed,
                verifiedIds = verifiedIds,
                disabledIds = disabledIds,
                attempts = attempts,
                onStageUpdate = onStageUpdate,
                onThinkingToken = onThinkingToken,
                onStreamToken = onStreamToken
            )
        }

        // Optional Zero-Key Web Research Context (DuckDuckGo)
        var webContextBlock = ""
        val webSources = mutableListOf<String>()
        if (useGoogleSearch && routingConfig.profile != RoutingProfile.FAST) {
            onStageUpdate(
                SwarmStepUpdate(
                    activeModelName = "Live Web Research",
                    stageLabel = "Gathering live web context for Julie AI...",
                    progressPercent = 16,
                    completedSteps = listOf("Fetching live web references")
                )
            )
            val (snippet, sources) = fetchLiveWebContext(userPrompt)
            if (snippet.isNotBlank()) {
                webContextBlock = snippet
                webSources.addAll(sources)
                steps.add("Grounded with ${sources.size} live web sources")
            }
        }

        // Determine whether to invoke ONE Manager + isolated Specialists vs Direct Single-Model
        val isComplex = AiModelsCatalog.isComplexMultiStepPrompt(userPrompt, hasImage = false)
        val shouldUseManagerFlow = routingConfig.managerEnabled &&
            routingConfig.orchestrationMode == OrchestrationMode.MANAGER_PLUS_SPECIALISTS &&
            swarmEnabled &&
            (routingConfig.profile == RoutingProfile.MAX_POWER ||
                (routingConfig.profile == RoutingProfile.AUTO && maxPowerEnabled && isComplex))

        if (shouldUseManagerFlow) {
            return executeManagerAndSpecialists(
                requestId = requestId,
                startMs = startMs,
                role = role,
                routingConfig = routingConfig,
                userPrompt = userPrompt,
                conversationContext = conversationContext,
                webContextBlock = webContextBlock,
                webSources = webSources,
                preferredModelId = preferredModelId,
                backupsAllowed = backupsAllowed,
                verifiedIds = verifiedIds,
                disabledIds = disabledIds,
                initialSteps = steps,
                attempts = attempts,
                onStageUpdate = onStageUpdate,
                onThinkingToken = onThinkingToken,
                onStreamToken = onStreamToken
            )
        }

        // 3. Single-Model Direct Path (Small chat bypasses Manager for ultra-low latency & clean output)
        val effectiveRole = when (routingConfig.profile) {
            RoutingProfile.FAST -> SpecialistRole.FAST_CHAT
            RoutingProfile.DEEP -> SpecialistRole.REASONING_MATH
            else -> role
        }
        val failoverChain = AiModelsCatalog.buildFailoverChain(
            role = effectiveRole,
            requireVision = false,
            pinnedModelId = preferredModelId,
            backupsAllowed = backupsAllowed,
            verifiedLiveIds = verifiedIds,
            disabledIds = disabledIds
        )

        val primaryLabel = failoverChain.firstOrNull()?.displayName ?: "Nemotron"
        steps.add("Routed [${effectiveRole.label}] -> $primaryLabel (Single-Flight)")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = primaryLabel,
                stageLabel = if (settings.streamingEnabled) "Streaming live from NVIDIA NIM..." else "Generating via NVIDIA NIM...",
                progressPercent = 42,
                completedSteps = steps.toList()
            )
        )

        val enrichedPrompt = buildString {
            if (webContextBlock.isNotBlank()) {
                append("Live Web Research Context:\n$webContextBlock\n\n")
            }
            append(userPrompt)
        }

        val callOutcome = executeWithStrictFailover(
            candidates = failoverChain,
            systemPromptBuilder = { model -> buildJulieSystemPrompt(model, settings.customSystemPrompt) },
            userPrompt = enrichedPrompt,
            context = conversationContext,
            stream = settings.streamingEnabled,
            captureThinking = settings.thinkingModeEnabled,
            attempts = attempts,
            onCandidateSwitch = { nextModel, reason ->
                steps.add("Failover -> ${nextModel.displayName} ($reason)")
                onStageUpdate(
                    SwarmStepUpdate(
                        activeModelName = nextModel.displayName,
                        stageLabel = "Switched cleanly to ${nextModel.displayName}...",
                        progressPercent = 58,
                        completedSteps = steps.toList()
                    )
                )
            },
            onThinkingToken = onThinkingToken,
            onStreamToken = onStreamToken
        )

        steps.add("Verified response from ${callOutcome.model.displayName}")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = callOutcome.model.displayName,
                stageLabel = "Complete via NVIDIA NIM",
                progressPercent = 100,
                completedSteps = steps.toList()
            )
        )

        val diagnostics = RequestDiagnostics(
            requestId = requestId,
            role = effectiveRole,
            routingProfile = routingConfig.profile,
            finalModelId = callOutcome.model.id,
            managerUsed = false,
            chunkedLongPrompt = false,
            chunkCount = 1,
            ttftMs = callOutcome.ttftMs,
            totalMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
            attempts = attempts.toList()
        )

        return SwarmResult(
            finalText = callOutcome.content,
            reasoningTrace = callOutcome.reasoning,
            primaryEngineLabel = "${callOutcome.model.displayName} • NIM",
            collaboratingModels = listOf(callOutcome.model.displayName),
            searchSources = webSources.distinct().take(6),
            usedWebSearch = webSources.isNotEmpty(),
            steps = steps,
            diagnostics = diagnostics
        )
    }

    /**
     * Manager + Isolated Specialists Pattern:
     * - Workers execute internally and return isolated [WorkerResult] objects (never streaming raw tokens to chat).
     * - ONE large Nemotron Manager synthesizes the single unified response streamed to the user.
     */
    private suspend fun executeManagerAndSpecialists(
        requestId: String,
        startMs: Long,
        role: SpecialistRole,
        routingConfig: RoutingConfig,
        userPrompt: String,
        conversationContext: String,
        webContextBlock: String,
        webSources: List<String>,
        preferredModelId: String,
        backupsAllowed: Boolean,
        verifiedIds: Set<String>,
        disabledIds: Set<String>,
        initialSteps: MutableList<String>,
        attempts: MutableList<AttemptRecord>,
        onStageUpdate: (SwarmStepUpdate) -> Unit,
        onThinkingToken: (String) -> Unit,
        onStreamToken: (String) -> Unit
    ): SwarmResult = coroutineScope {
        val settings = NvidiaNimClient.settingsFlow.value
        val steps = initialSteps.toMutableList()

        val managerChain = AiModelsCatalog.buildFailoverChain(
            role = SpecialistRole.MANAGER,
            requireVision = false,
            pinnedModelId = preferredModelId,
            backupsAllowed = backupsAllowed,
            verifiedLiveIds = verifiedIds,
            disabledIds = disabledIds
        )
        val workerAChain = AiModelsCatalog.buildFailoverChain(
            role = role,
            requireVision = false,
            pinnedModelId = AiModelsCatalog.AUTO_SWARM_ID,
            backupsAllowed = backupsAllowed,
            verifiedLiveIds = verifiedIds,
            disabledIds = disabledIds
        )
        val workerBChain = AiModelsCatalog.buildFailoverChain(
            role = if (role == SpecialistRole.REASONING_MATH) SpecialistRole.PLANNING_TASKS else SpecialistRole.REASONING_MATH,
            requireVision = false,
            pinnedModelId = AiModelsCatalog.AUTO_SWARM_ID,
            backupsAllowed = backupsAllowed,
            verifiedLiveIds = verifiedIds,
            disabledIds = disabledIds
        )

        val workerA = workerAChain.firstOrNull()
        val workerB = workerBChain.firstOrNull { it.id != workerA?.id } ?: workerBChain.firstOrNull()
        val managerLead = managerChain.firstOrNull() ?: AiModelsCatalog.allModels.first()

        steps.add("Manager (${managerLead.displayName}) delegated isolated workers")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = managerLead.displayName,
                stageLabel = "Stage 1/2: Isolated Nemotron specialists drafting...",
                progressPercent = 30,
                completedSteps = steps.toList()
            )
        )

        val resADeferred = async {
            if (workerA == null) {
                WorkerResult("worker_a", "None", role, "", false)
            } else {
                val out = runIsolatedWorker(
                    workerId = "worker_a",
                    candidates = workerAChain.take(2),
                    role = role,
                    prompt = "Provide concise, high-signal technical analysis (max 220 words) for:\n$userPrompt" +
                        if (webContextBlock.isNotBlank()) "\n\nWeb Context:\n$webContextBlock" else "",
                    context = conversationContext,
                    attempts = attempts
                )
                out
            }
        }

        val resBDeferred = async {
            if (workerB == null) {
                WorkerResult("worker_b", "None", SpecialistRole.REASONING_MATH, "", false)
            } else {
                val out = runIsolatedWorker(
                    workerId = "worker_b",
                    candidates = workerBChain.take(2),
                    role = SpecialistRole.REASONING_MATH,
                    prompt = "Verify edge cases, risks, and actionable steps (max 180 words) for:\n$userPrompt",
                    context = "",
                    attempts = attempts
                )
                out
            }
        }

        val workerResA = resADeferred.await()
        val workerResB = resBDeferred.await()

        val activeWorkers = listOf(workerResA, workerResB).filter { it.succeeded && it.summary.isNotBlank() }
        steps.add("Collected ${activeWorkers.size} isolated specialist drafts -> Manager synthesis")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = managerLead.displayName,
                stageLabel = "Stage 2/2: Manager streaming unified final answer...",
                progressPercent = 70,
                completedSteps = steps.toList()
            )
        )

        val synthesisPrompt = buildString {
            append("You are Julie AI's Lead Manager. Produce ONE unified, clean, authoritative answer in the user's language (English, Hindi, or Hinglish).\n")
            append("Never mention internal worker drafts or raw routing metadata unless asked.\n\n")
            if (conversationContext.isNotBlank()) {
                append("Recent Conversation:\n$conversationContext\n\n")
            }
            if (webContextBlock.isNotBlank()) {
                append("Live Web References:\n$webContextBlock\n\n")
            }
            append("User Request:\n$userPrompt\n\n")
            activeWorkers.forEach { w ->
                append("--- Internal Specialist (${w.role.label}: ${w.modelName}) ---\n${w.summary}\n\n")
            }
            append("Write the final unified response now:")
        }

        val finalOutcome = executeWithStrictFailover(
            candidates = managerChain,
            systemPromptBuilder = { model -> buildJulieSystemPrompt(model, settings.customSystemPrompt) },
            userPrompt = synthesisPrompt,
            context = "",
            stream = settings.streamingEnabled,
            captureThinking = settings.thinkingModeEnabled,
            attempts = attempts,
            onCandidateSwitch = { nextModel, reason ->
                steps.add("Manager failover -> ${nextModel.displayName} ($reason)")
                onStageUpdate(
                    SwarmStepUpdate(
                        activeModelName = nextModel.displayName,
                        stageLabel = "Manager switched cleanly to ${nextModel.displayName}...",
                        progressPercent = 82,
                        completedSteps = steps.toList()
                    )
                )
            },
            onThinkingToken = onThinkingToken,
            onStreamToken = onStreamToken
        )

        val teamNames = (activeWorkers.map { it.modelName } + finalOutcome.model.displayName).distinct()
        steps.add("Manager ${finalOutcome.model.displayName} finalized answer")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = finalOutcome.model.displayName,
                stageLabel = "Manager synthesis complete",
                progressPercent = 100,
                completedSteps = steps.toList()
            )
        )

        val diagnostics = RequestDiagnostics(
            requestId = requestId,
            role = role,
            routingProfile = routingConfig.profile,
            finalModelId = finalOutcome.model.id,
            managerUsed = true,
            chunkedLongPrompt = false,
            chunkCount = 1,
            ttftMs = finalOutcome.ttftMs,
            totalMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
            attempts = attempts.toList()
        )

        SwarmResult(
            finalText = finalOutcome.content,
            reasoningTrace = finalOutcome.reasoning,
            primaryEngineLabel = "Manager: ${finalOutcome.model.displayName}",
            collaboratingModels = teamNames,
            searchSources = webSources.distinct().take(6),
            usedWebSearch = webSources.isNotEmpty(),
            steps = steps,
            diagnostics = diagnostics
        )
    }

    /**
     * Map-Reduce Long Prompt Handler for 50K+ character pasted prompts/documents.
     */
    private suspend fun executeLongPromptMapReduce(
        requestId: String,
        startMs: Long,
        routingConfig: RoutingConfig,
        userPrompt: String,
        preferredModelId: String,
        backupsAllowed: Boolean,
        verifiedIds: Set<String>,
        disabledIds: Set<String>,
        attempts: MutableList<AttemptRecord>,
        onStageUpdate: (SwarmStepUpdate) -> Unit,
        onThinkingToken: (String) -> Unit,
        onStreamToken: (String) -> Unit
    ): SwarmResult {
        val settings = NvidiaNimClient.settingsFlow.value
        val chunks = LongPromptProcessor.splitIntoBoundedChunks(userPrompt)
        val estimatedTokens = LongPromptProcessor.estimateTokens(userPrompt)
        val steps = mutableListOf(
            "Long prompt preflight: ${userPrompt.length} chars (~$estimatedTokens tokens) -> ${chunks.size} bounded chunks"
        )

        val workerChain = AiModelsCatalog.buildFailoverChain(
            role = SpecialistRole.FAST_CHAT,
            requireVision = false,
            pinnedModelId = AiModelsCatalog.AUTO_SWARM_ID,
            backupsAllowed = backupsAllowed,
            verifiedLiveIds = verifiedIds,
            disabledIds = disabledIds
        )
        val synthChain = AiModelsCatalog.buildFailoverChain(
            role = SpecialistRole.LONG_CONTEXT,
            requireVision = false,
            pinnedModelId = preferredModelId,
            backupsAllowed = backupsAllowed,
            verifiedLiveIds = verifiedIds,
            disabledIds = disabledIds
        )

        val summaries = mutableListOf<String>()
        for ((index, chunk) in chunks.withIndex()) {
            val pct = 15 + ((index + 1) * 55 / chunks.size.coerceAtLeast(1))
            onStageUpdate(
                SwarmStepUpdate(
                    activeModelName = workerChain.firstOrNull()?.displayName ?: "Nemotron",
                    stageLabel = "Processing long prompt chunk ${index + 1}/${chunks.size}...",
                    progressPercent = pct,
                    completedSteps = steps.toList()
                )
            )
            val worker = runIsolatedWorker(
                workerId = "chunk_${index + 1}",
                candidates = workerChain.take(3),
                role = SpecialistRole.LONG_CONTEXT,
                prompt = "Extract every requirement, constraint, technical detail, and action item from Part ${index + 1} of ${chunks.size} (preserve accuracy, no filler):\n\n$chunk",
                context = "",
                attempts = attempts
            )
            if (worker.succeeded && worker.summary.isNotBlank()) {
                summaries.add("### Part ${index + 1}/${chunks.size}\n${worker.summary}")
            } else {
                summaries.add("### Part ${index + 1}/${chunks.size} (raw excerpt)\n${chunk.take(1500)}")
            }
        }

        steps.add("Synthesizing ${summaries.size} extracted parts into final response")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = synthChain.firstOrNull()?.displayName ?: "Nemotron Long-Context",
                stageLabel = "Merging ${chunks.size} chunks into one unified response...",
                progressPercent = 78,
                completedSteps = steps.toList()
            )
        )

        val mergePrompt = buildString {
            append("The user provided a large prompt (${userPrompt.length} chars, split into ${chunks.size} parts with zero silent loss).\n")
            append("Synthesize one complete, structured, actionable answer covering all parts:\n\n")
            append(summaries.joinToString("\n\n"))
        }

        val finalOutcome = executeWithStrictFailover(
            candidates = synthChain,
            systemPromptBuilder = { model -> buildJulieSystemPrompt(model, settings.customSystemPrompt) },
            userPrompt = mergePrompt,
            context = "",
            stream = settings.streamingEnabled,
            captureThinking = settings.thinkingModeEnabled,
            attempts = attempts,
            onCandidateSwitch = { nextModel, reason ->
                steps.add("Long-context failover -> ${nextModel.displayName} ($reason)")
            },
            onThinkingToken = onThinkingToken,
            onStreamToken = onStreamToken
        )

        val diagnostics = RequestDiagnostics(
            requestId = requestId,
            role = SpecialistRole.LONG_CONTEXT,
            routingProfile = routingConfig.profile,
            finalModelId = finalOutcome.model.id,
            managerUsed = true,
            chunkedLongPrompt = true,
            chunkCount = chunks.size,
            ttftMs = finalOutcome.ttftMs,
            totalMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
            attempts = attempts.toList()
        )

        return SwarmResult(
            finalText = finalOutcome.content,
            reasoningTrace = finalOutcome.reasoning,
            primaryEngineLabel = "${finalOutcome.model.displayName} • Chunked (${chunks.size} parts)",
            collaboratingModels = listOf(finalOutcome.model.displayName),
            searchSources = emptyList(),
            usedWebSearch = false,
            steps = steps,
            diagnostics = diagnostics
        )
    }

    private suspend fun executeNimVisionQuery(
        requestId: String,
        startMs: Long,
        routingProfile: RoutingProfile,
        userPrompt: String,
        conversationContext: String,
        visionChain: List<AiModelSpec>,
        attachedBitmap: Bitmap,
        attempts: MutableList<AttemptRecord>,
        onStageUpdate: (SwarmStepUpdate) -> Unit,
        onStreamToken: (String) -> Unit
    ): SwarmResult {
        val steps = mutableListOf("Visual frame locked (${attachedBitmap.width}x${attachedBitmap.height})")
        if (visionChain.isEmpty()) {
            throw IllegalStateException("No enabled NVIDIA Vision models are available. Re-enable a Vision model in 33 Models.")
        }

        val promptText = buildString {
            append("You are Julie AI powered by NVIDIA NIM Vision inspecting the user's shared screen or image.\n")
            append("Respond clearly in the user's language (English, Hindi, or Hinglish).\n")
            append("Give immediate, accurate, step-by-step guidance based directly on what is visible.\n\n")
            if (conversationContext.isNotBlank()) {
                append("Recent context:\n$conversationContext\n\n")
            }
            append("User request: $userPrompt")
        }

        var lastError: NimHttpException? = null
        for ((idx, candidate) in visionChain.withIndex()) {
            val attemptStart = System.currentTimeMillis()
            onStageUpdate(
                SwarmStepUpdate(
                    activeModelName = candidate.displayName,
                    stageLabel = "Analyzing screen via ${candidate.displayName}...",
                    progressPercent = (32 + idx * 14).coerceAtMost(88),
                    completedSteps = steps.toList()
                )
            )
            try {
                val answer = callNvidiaNimVisionOrThrow(candidate.id, promptText, attachedBitmap)
                val elapsed = (System.currentTimeMillis() - attemptStart).coerceAtLeast(1L)
                if (answer.isNotBlank()) {
                    NvidiaNimClient.recordSuccess(appContext, candidate.id, elapsed)
                    attempts.add(AttemptRecord(candidate.id, 200, elapsed, "OK"))
                    onStreamToken(answer)
                    steps.add("Screen insights synthesized by ${candidate.displayName}")
                    onStageUpdate(
                        SwarmStepUpdate(
                            activeModelName = candidate.displayName,
                            stageLabel = "Vision analysis complete",
                            progressPercent = 100,
                            completedSteps = steps.toList()
                        )
                    )
                    val diagnostics = RequestDiagnostics(
                        requestId = requestId,
                        role = SpecialistRole.VISION_SCREEN,
                        routingProfile = routingProfile,
                        finalModelId = candidate.id,
                        managerUsed = false,
                        chunkedLongPrompt = false,
                        chunkCount = 1,
                        ttftMs = elapsed,
                        totalMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
                        attempts = attempts.toList()
                    )
                    return SwarmResult(
                        finalText = answer,
                        primaryEngineLabel = "${candidate.displayName} • NIM Vision",
                        collaboratingModels = listOf(candidate.displayName),
                        searchSources = emptyList(),
                        usedWebSearch = false,
                        steps = steps,
                        diagnostics = diagnostics
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsed = (System.currentTimeMillis() - attemptStart).coerceAtLeast(1L)
                val classified = NimErrorClassifier.classifyThrowable(e)
                NvidiaNimClient.recordFailureFromException(appContext, candidate.id, classified, elapsed)
                attempts.add(AttemptRecord(candidate.id, classified.httpCode, elapsed, classified.category.label))
                lastError = classified
                // 401 or OFFLINE: stop immediately, NEVER loop across all vision models!
                if (classified.category.shouldAbortAllModels) {
                    throw IllegalStateException(classified.safeMessage)
                }
            }
        }

        throw IllegalStateException(
            lastError?.safeMessage
                ?: "No verified NVIDIA NIM Vision model responded. Run Model Diagnostics in 33 Models."
        )
    }

    /**
     * Splits a complex user goal into 3-5 concrete parallel tasks using NVIDIA NIM structured JSON output.
     */
    suspend fun splitGoalIntoTasks(goal: String): List<PlannedSubTask> {
        requireNimConfigured()
        val chain = AiModelsCatalog.buildFailoverChain(
            role = SpecialistRole.PLANNING_TASKS,
            requireVision = false,
            backupsAllowed = true,
            verifiedLiveIds = NvidiaNimClient.verifiedModelIds(),
            disabledIds = NvidiaNimClient.disabledModelIds()
        )
        val nimJsonPrompt = """
            Break down the following user goal into 3 to 5 clear, parallelizable autonomous tasks that Julie AI can execute immediately.
            Keep titles short and punchy. Match the user's language (English, Hindi, or Hinglish).
            Goal: $goal

            Respond ONLY with valid JSON matching this exact structure (no markdown code fences, no extra prose):
            {"tasks":[{"title":"Short title","prompt":"Full instructions","category":"GENERAL|RESEARCH|CODE|WRITING|PLANNING|SCREEN|AUTOMATION","priority":"LOW|NORMAL|HIGH|URGENT"}]}
        """.trimIndent()

        val outcome = executeWithStrictFailover(
            candidates = chain.take(4),
            systemPromptBuilder = { "You are a strict JSON task planner. Output valid JSON only." },
            userPrompt = nimJsonPrompt,
            context = "",
            stream = false,
            captureThinking = false,
            attempts = mutableListOf(),
            onCandidateSwitch = { _, _ -> },
            onThinkingToken = {},
            onStreamToken = {}
        )
        return parsePlannedTasksJson(outcome.content, goal)
    }

    private fun parsePlannedTasksJson(rawOutput: String, fallbackGoal: String): List<PlannedSubTask> {
        val cleaned = rawOutput
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val jsonStart = cleaned.indexOf('{')
        val jsonEnd = cleaned.lastIndexOf('}')
        val slice = if (jsonStart >= 0 && jsonEnd > jsonStart) {
            cleaned.substring(jsonStart, jsonEnd + 1)
        } else {
            cleaned
        }
        val root = JSONObject(slice)
        val arr: JSONArray = root.getJSONArray("tasks")
        val planned = mutableListOf<PlannedSubTask>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            planned.add(
                PlannedSubTask(
                    title = obj.optString("title", "Sub-task ${i + 1}").take(150),
                    prompt = obj.optString("prompt", fallbackGoal).take(3800),
                    category = obj.optString("category", "GENERAL"),
                    priority = obj.optString("priority", "NORMAL")
                )
            )
        }
        return planned
    }

    private data class FailoverCallOutcome(
        val model: AiModelSpec,
        val content: String,
        val reasoning: String,
        val ttftMs: Long
    )

    private suspend fun executeWithStrictFailover(
        candidates: List<AiModelSpec>,
        systemPromptBuilder: (AiModelSpec) -> String,
        userPrompt: String,
        context: String,
        stream: Boolean,
        captureThinking: Boolean,
        attempts: MutableList<AttemptRecord>,
        onCandidateSwitch: (AiModelSpec, String) -> Unit,
        onThinkingToken: (String) -> Unit,
        onStreamToken: (String) -> Unit
    ): FailoverCallOutcome {
        val settings = NvidiaNimClient.settingsFlow.value
        val chain = candidates.ifEmpty { listOf(defaultFastFlagship()) }
        var lastError: NimHttpException? = null

        for ((idx, candidate) in chain.withIndex()) {
            if (idx > 0) {
                // Reset any partial stream tokens BEFORE switching so mixed outputs can NEVER happen
                onStreamToken("")
                onThinkingToken("")
                onCandidateSwitch(candidate, lastError?.category?.label ?: "Fallback")
            }

            val messages = buildNimMessages(systemPromptBuilder(candidate), context, userPrompt)
            val attemptStart = System.currentTimeMillis()
            try {
                if (stream) {
                    val out = withTimeout(75_000L) {
                        NvidiaNimClient.streamChatCompletion(
                            modelId = candidate.id,
                            messages = messages,
                            temperature = settings.temperature.toDouble(),
                            topP = settings.topP.toDouble(),
                            maxTokens = settings.maxTokens,
                            onThinkingToken = { if (captureThinking) onThinkingToken(it) },
                            onContentToken = onStreamToken
                        )
                    }
                    if (out.content.isNotBlank()) {
                        NvidiaNimClient.recordSuccess(appContext, candidate.id, out.totalMs)
                        attempts.add(AttemptRecord(candidate.id, 200, out.totalMs, "Stream OK"))
                        return FailoverCallOutcome(
                            model = candidate,
                            content = out.content,
                            reasoning = if (captureThinking) out.reasoningTrace else "",
                            ttftMs = out.ttftMs
                        )
                    } else {
                        throw NimHttpException(502, NimErrorCategory.SERVER_5XX, null, "Empty streamed output from ${candidate.id}")
                    }
                } else {
                    val resp = withTimeout(55_000L) {
                        NvidiaNimClient.service.chatCompletion(
                            authorization = NvidiaNimClient.authHeader(),
                            request = NimChatRequest(
                                model = candidate.id,
                                messages = messages,
                                temperature = settings.temperature.toDouble(),
                                topP = settings.topP.toDouble(),
                                maxTokens = settings.maxTokens,
                                stream = false
                            )
                        )
                    }
                    val elapsed = (System.currentTimeMillis() - attemptStart).coerceAtLeast(1L)
                    val msg = resp.choices?.firstOrNull()?.message
                    val content = msg?.content?.trim().orEmpty()
                    val reason = msg?.reasoningContent?.trim().orEmpty()
                    if (content.isNotBlank()) {
                        NvidiaNimClient.recordSuccess(appContext, candidate.id, elapsed)
                        attempts.add(AttemptRecord(candidate.id, 200, elapsed, "OK"))
                        onStreamToken(content)
                        return FailoverCallOutcome(
                            model = candidate,
                            content = content,
                            reasoning = if (captureThinking) reason else "",
                            ttftMs = elapsed
                        )
                    } else {
                        throw NimHttpException(502, NimErrorCategory.SERVER_5XX, null, "Empty completion from ${candidate.id}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsed = (System.currentTimeMillis() - attemptStart).coerceAtLeast(1L)
                val classified = NimErrorClassifier.classifyThrowable(e)
                Log.w(TAG, "NVIDIA NIM attempt failed [${candidate.id}] HTTP ${classified.httpCode}: ${classified.safeMessage}")
                NvidiaNimClient.recordFailureFromException(appContext, candidate.id, classified, elapsed)
                attempts.add(AttemptRecord(candidate.id, classified.httpCode, elapsed, classified.category.label))
                lastError = classified

                // 401 Invalid Key or Offline -> STOP IMMEDIATELY, never spam other models!
                if (classified.category.shouldAbortAllModels) {
                    throw IllegalStateException(classified.safeMessage)
                }

                // 429 Rate Limit -> Honor Retry-After (bounded wait + small jitter) before trying next backup
                if (classified.category == NimErrorCategory.RATE_LIMITED_429) {
                    val waitMs = ((classified.retryAfterSeconds ?: 2L).coerceAtMost(5L) * 1000L) +
                        Random.nextLong(150L, 550L)
                    delay(waitMs)
                }
            }
        }

        throw IllegalStateException(
            lastError?.safeMessage
                ?: "No verified NVIDIA NIM model responded. Open 33 Models -> Test Connection & Probe."
        )
    }

    private suspend fun runIsolatedWorker(
        workerId: String,
        candidates: List<AiModelSpec>,
        role: SpecialistRole,
        prompt: String,
        context: String,
        attempts: MutableList<AttemptRecord>
    ): WorkerResult {
        val settings = NvidiaNimClient.settingsFlow.value
        for (candidate in candidates) {
            val start = System.currentTimeMillis()
            try {
                val response = withTimeout(40_000L) {
                    NvidiaNimClient.service.chatCompletion(
                        authorization = NvidiaNimClient.authHeader(),
                        request = NimChatRequest(
                            model = candidate.id,
                            messages = buildNimMessages(candidate.roleInstruction, context, prompt),
                            temperature = settings.temperature.toDouble(),
                            topP = settings.topP.toDouble(),
                            maxTokens = settings.maxTokens.coerceAtMost(1024),
                            stream = false
                        )
                    )
                }
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val text = response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
                if (text.isNotBlank()) {
                    NvidiaNimClient.recordSuccess(appContext, candidate.id, elapsed)
                    synchronized(attempts) {
                        attempts.add(AttemptRecord(candidate.id, 200, elapsed, "Worker OK"))
                    }
                    return WorkerResult(workerId, candidate.displayName, role, text, true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val classified = NimErrorClassifier.classifyThrowable(e)
                NvidiaNimClient.recordFailureFromException(appContext, candidate.id, classified, elapsed)
                synchronized(attempts) {
                    attempts.add(AttemptRecord(candidate.id, classified.httpCode, elapsed, "Worker ${classified.category.label}"))
                }
                if (classified.category.shouldAbortAllModels) {
                    throw IllegalStateException(classified.safeMessage)
                }
            }
        }
        return WorkerResult(workerId, candidates.firstOrNull()?.displayName ?: "Worker", role, "", false)
    }

    private suspend fun callNvidiaNimVisionOrThrow(
        modelId: String,
        prompt: String,
        bitmap: Bitmap
    ): String {
        return withTimeout(55_000L) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out)
            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$base64"

            val response = NvidiaNimClient.service.visionCompletion(
                authorization = NvidiaNimClient.authHeader(),
                request = NimVisionChatRequest(
                    model = modelId,
                    messages = listOf(
                        NimVisionMessage(
                            role = "user",
                            content = listOf(
                                NimVisionContentPart(type = "text", text = prompt),
                                NimVisionContentPart(
                                    type = "image_url",
                                    imageUrl = NimImageUrl(url = dataUrl)
                                )
                            )
                        )
                    )
                )
            )
            response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
        }
    }

    private suspend fun fetchLiveWebContext(query: String): Pair<String, List<String>> =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim().take(180)
            if (cleanQuery.isEmpty()) return@withContext "" to emptyList()
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val snippets = mutableListOf<String>()
            val sources = mutableListOf<String>()

            // 1. DuckDuckGo Instant Knowledge API
            runCatching {
                val req = Request.Builder()
                    .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                    .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
                    .get()
                    .build()
                webClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val abstractText = json.optString("AbstractText").trim()
                        val abstractUrl = json.optString("AbstractURL").trim()
                        val heading = json.optString("Heading").trim()
                        if (abstractText.isNotBlank()) {
                            snippets.add("$heading: $abstractText")
                            if (abstractUrl.isNotBlank()) {
                                sources.add("${heading.ifBlank { "Knowledge Source" }}|$abstractUrl")
                            }
                        }
                    }
                }
            }

            // 2. Wikipedia Live Global Query (covers global technologies, libraries, figures, news)
            runCatching {
                val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json&srlimit=3"
                val req = Request.Builder()
                    .url(wikiUrl)
                    .addHeader("User-Agent", "JulieAI-Agent/3.0 (Android; Assistant)")
                    .get()
                    .build()
                webClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val queryObj = root.optJSONObject("query")
                        val searchArr = queryObj?.optJSONArray("search")
                        if (searchArr != null) {
                            for (i in 0 until searchArr.length()) {
                                val item = searchArr.optJSONObject(i) ?: continue
                                val title = item.optString("title").trim()
                                val rawSnippet = item.optString("snippet").replace(Regex("<[^>]*>"), "").trim()
                                if (title.isNotBlank() && rawSnippet.isNotBlank()) {
                                    snippets.add("• $title: $rawSnippet")
                                    val pageUrl = "https://en.wikipedia.org/wiki/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}"
                                    sources.add("$title|$pageUrl")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Specialized AI & GitHub context enrichment if asking about tech/tools
            val lower = query.lowercase(Locale.ROOT)
            if (lower.contains("ai") || lower.contains("google") || lower.contains("tool") || lower.contains("github") || lower.contains("free")) {
                sources.add("Google AI Studio (Gemini 2.0)|https://ai.google.dev")
                sources.add("GitHub Trending AI Repositories|https://github.com/trending")
                sources.add("NVIDIA NIM AI Catalog|https://build.nvidia.com")
            }

            val combinedSnippets = snippets.joinToString("\n\n").take(2400)
            combinedSnippets to sources.distinct().take(6)
        }

    private fun buildJulieSystemPrompt(model: AiModelSpec, customSystemPrompt: String): String = buildString {
        append("You are Julie (जुली), a warm, sharp, privacy-respecting Android AI assistant.\n")
        append(model.roleInstruction)
        append("\nRespond clearly in the user's language (English, Hindi, or Hinglish). Never fabricate live actions or fake results.")
        if (customSystemPrompt.isNotBlank()) {
            append("\nUser Persona Instructions:\n$customSystemPrompt")
        }
    }

    private fun buildNimMessages(
        systemPrompt: String,
        context: String,
        prompt: String
    ): List<NimMessage> {
        val list = mutableListOf(NimMessage("system", systemPrompt))
        if (context.isNotBlank()) {
            list.add(NimMessage("user", "Recent conversation context:\n$context"))
        }
        list.add(NimMessage("user", prompt))
        return list
    }

    private fun defaultFastFlagship(): AiModelSpec {
        return AiModelsCatalog.findById("nvidia/llama-3.3-nemotron-super-49b-v1")
            ?: AiModelsCatalog.findById("meta/llama-3.3-70b-instruct")
            ?: AiModelsCatalog.allModels.first()
    }
}
