package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.model.AiModelSpec
import com.example.data.model.AiModelsCatalog
import com.example.data.model.ModelProvider
import com.example.data.remote.NimChatRequest
import com.example.data.remote.NimImageUrl
import com.example.data.remote.NimMessage
import com.example.data.remote.NimVisionChatRequest
import com.example.data.remote.NimVisionContentPart
import com.example.data.remote.NimVisionMessage
import com.example.data.remote.NvidiaNimClient
import com.example.firebase.FirebaseRuntime
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private const val TAG = "AuraSwarmEngine"

data class SwarmStepUpdate(
    val activeModelName: String,
    val stageLabel: String,
    val progressPercent: Int,
    val completedSteps: List<String>
)

data class SwarmResult(
    val finalText: String,
    val primaryEngineLabel: String,
    val collaboratingModels: List<String>,
    val searchSources: List<String>,
    val usedWebSearch: Boolean,
    val steps: List<String>
)

data class PlannedSubTask(
    val title: String,
    val prompt: String,
    val category: String,
    val priority: String
)

class AuraSwarmEngine(private val appContext: Context) {

    // Lazily initialized Firebase AI Logic models (never eagerly evaluated at construction)
    private val flashModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val fastRouterModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    private val searchGroundedModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.googleSearch())
        )
    }

    private val taskSplitterModel by lazy {
        val taskItemSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short actionable task title under 60 chars"),
                "prompt" to Schema.string(description = "Complete instructions for the AI swarm to execute"),
                "category" to Schema.enumeration(
                    values = listOf("GENERAL", "RESEARCH", "CODE", "WRITING", "PLANNING", "SCREEN", "AUTOMATION"),
                    description = "Domain category"
                ),
                "priority" to Schema.enumeration(
                    values = listOf("LOW", "NORMAL", "HIGH", "URGENT"),
                    description = "Execution priority"
                )
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "tasks" to Schema.array(
                    items = taskItemSchema,
                    description = "3 to 5 parallel sub-tasks to achieve the user goal"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    private val isFirebaseAiAvailable: Boolean
        get() = FirebaseRuntime.isFirebaseConfigured(appContext)

    fun isNvidiaNimDirectActive(): Boolean = NvidiaNimClient.isConfigured()

    private fun requireAnyBackendReady() {
        if (!isFirebaseAiAvailable && !NvidiaNimClient.isConfigured()) {
            throw IllegalStateException(
                "No AI engine connected yet. Open the 33 Models tab and paste your free NVIDIA NIM API key (nvapi-...) to run all models."
            )
        }
    }

    /**
     * Executes a prompt using either Multi-Model Automatic Swarm Collaboration (Max Power)
     * or a focused specialist model, with optional live Google Search grounding and
     * real multimodal screen-capture Bitmap analysis.
     */
    suspend fun executeQuery(
        userPrompt: String,
        conversationContext: String = "",
        preferredModelId: String = AiModelsCatalog.AUTO_SWARM_ID,
        swarmEnabled: Boolean = true,
        maxPowerEnabled: Boolean = true,
        useGoogleSearch: Boolean = true,
        attachedBitmap: Bitmap? = null,
        onStageUpdate: (SwarmStepUpdate) -> Unit = {},
        onStreamToken: (String) -> Unit = {}
    ): SwarmResult {
        requireAnyBackendReady()

        val steps = mutableListOf<String>()
        val selectedSwarm = AiModelsCatalog.selectBestSwarmForPrompt(
            prompt = userPrompt,
            hasImage = attachedBitmap != null,
            needsSearch = useGoogleSearch,
            pinnedModelId = preferredModelId
        )
        val modelNames = selectedSwarm.map { it.displayName }

        // 1. Vision + Screen Share Mode (Firebase AI Gemini Vision OR NVIDIA NIM Vision fallback)
        if (attachedBitmap != null) {
            return executeVisionQuery(
                userPrompt = userPrompt,
                conversationContext = conversationContext,
                selectedSwarm = selectedSwarm,
                attachedBitmap = attachedBitmap,
                onStageUpdate = onStageUpdate,
                onStreamToken = onStreamToken
            )
        }

        // 2. Multi-Model Automatic Swarm Collaboration (Max Power)
        if (swarmEnabled && maxPowerEnabled) {
            return if (isFirebaseAiAvailable) {
                executeCollaborativeSwarm(
                    userPrompt = userPrompt,
                    conversationContext = conversationContext,
                    selectedSwarm = selectedSwarm,
                    useGoogleSearch = useGoogleSearch,
                    onStageUpdate = onStageUpdate,
                    onStreamToken = onStreamToken
                )
            } else {
                executePureNimCollaborativeSwarm(
                    userPrompt = userPrompt,
                    conversationContext = conversationContext,
                    selectedSwarm = selectedSwarm,
                    onStageUpdate = onStageUpdate,
                    onStreamToken = onStreamToken
                )
            }
        }

        // 3. Direct Single-Model Execution
        val primaryModel = selectedSwarm.firstOrNull() ?: AiModelsCatalog.allModels.first()
        val sources = mutableListOf<String>()
        var didSearch = false

        steps.add("Selected ${primaryModel.displayName} (${primaryModel.provider.label})")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = primaryModel.displayName,
                stageLabel = if (useGoogleSearch && isFirebaseAiAvailable) {
                    "Searching Google & generating..."
                } else {
                    "Generating direct response..."
                },
                progressPercent = 40,
                completedSteps = steps.toList()
            )
        )

        val shouldTryNimDirect = NvidiaNimClient.isConfigured() &&
            (!isFirebaseAiAvailable || (!useGoogleSearch && primaryModel.provider == ModelProvider.NVIDIA_NIM))

        if (shouldTryNimDirect) {
            val nimModel = if (primaryModel.provider == ModelProvider.NVIDIA_NIM) {
                primaryModel
            } else {
                defaultNimModel()
            }
            val nimOutput = tryCallNvidiaNim(nimModel, userPrompt, conversationContext)
            if (!nimOutput.isNullOrBlank()) {
                onStreamToken(nimOutput)
                steps.add("Direct NVIDIA NIM endpoint succeeded")
                onStageUpdate(
                    SwarmStepUpdate(
                        activeModelName = nimModel.displayName,
                        stageLabel = "Complete via NVIDIA NIM",
                        progressPercent = 100,
                        completedSteps = steps.toList()
                    )
                )
                return SwarmResult(
                    finalText = nimOutput,
                    primaryEngineLabel = "${nimModel.displayName} (NIM)",
                    collaboratingModels = listOf(nimModel.displayName),
                    searchSources = emptyList(),
                    usedWebSearch = false,
                    steps = steps
                )
            }
            if (!isFirebaseAiAvailable) {
                throw IllegalStateException(
                    "NVIDIA NIM request failed for ${nimModel.displayName}. Check your internet connection or verify your nvapi- key in 33 Models."
                )
            }
            steps.add("NIM endpoint busy — seamlessly routed via Firebase AI Logic")
        }

        val fullPrompt = buildString {
            append("You are Aura AI operating with the expertise of ${primaryModel.displayName}. ")
            append("${primaryModel.roleInstruction}\n")
            append("Match the user's language naturally (English or Hinglish).\n")
            if (conversationContext.isNotBlank()) {
                append("\nConversation history:\n$conversationContext\n")
            }
            append("\nUser request:\n$userPrompt")
        }

        val finalText = if (useGoogleSearch) {
            didSearch = true
            val (text, foundSources) = runSearchGroundedGeneration(fullPrompt)
            sources.addAll(foundSources)
            onStreamToken(text)
            text
        } else {
            streamGemini(fullPrompt, onStreamToken)
        }

        steps.add("Response verified (${finalText.length} chars)")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = primaryModel.displayName,
                stageLabel = "Complete",
                progressPercent = 100,
                completedSteps = steps.toList()
            )
        )

        return SwarmResult(
            finalText = finalText,
            primaryEngineLabel = primaryModel.displayName,
            collaboratingModels = modelNames.take(1),
            searchSources = sources.distinct().take(8),
            usedWebSearch = didSearch,
            steps = steps
        )
    }

    private suspend fun executeVisionQuery(
        userPrompt: String,
        conversationContext: String,
        selectedSwarm: List<AiModelSpec>,
        attachedBitmap: Bitmap,
        onStageUpdate: (SwarmStepUpdate) -> Unit,
        onStreamToken: (String) -> Unit
    ): SwarmResult {
        val steps = mutableListOf("Visual frame locked (${attachedBitmap.width}x${attachedBitmap.height})")
        val visionModels = selectedSwarm.map { it.displayName }

        val promptText = buildString {
            append("You are Aura AI, an expert 24/7 autonomous assistant inspecting the user's shared screen or image.\n")
            append("Respond clearly in the user's language (English or Hinglish if they write in Hinglish).\n")
            append("Provide immediate, accurate, step-by-step help based directly on what is visible.\n\n")
            if (conversationContext.isNotBlank()) {
                append("Recent context:\n$conversationContext\n\n")
            }
            append("User request: $userPrompt")
        }

        if (!isFirebaseAiAvailable) {
            val nimVisionModel = AiModelsCatalog.allModels.firstOrNull {
                it.provider == ModelProvider.NVIDIA_NIM && it.supportsVision
            } ?: defaultNimModel()

            onStageUpdate(
                SwarmStepUpdate(
                    activeModelName = nimVisionModel.displayName,
                    stageLabel = "Analyzing shared screen via NVIDIA NIM Vision...",
                    progressPercent = 35,
                    completedSteps = steps.toList()
                )
            )

            val nimVisionAnswer = tryCallNvidiaNimVision(
                modelId = nimVisionModel.id,
                prompt = promptText,
                bitmap = attachedBitmap
            ) ?: throw IllegalStateException(
                "NVIDIA NIM Vision could not process this screen right now. Verify your nvapi- key in 33 Models and try again."
            )

            onStreamToken(nimVisionAnswer)
            steps.add("Screen insights synthesized by ${nimVisionModel.displayName}")
            onStageUpdate(
                SwarmStepUpdate(
                    activeModelName = nimVisionModel.displayName,
                    stageLabel = "Screen analysis complete",
                    progressPercent = 100,
                    completedSteps = steps.toList()
                )
            )
            return SwarmResult(
                finalText = nimVisionAnswer,
                primaryEngineLabel = "${nimVisionModel.displayName} (NIM Vision)",
                collaboratingModels = listOf(nimVisionModel.displayName),
                searchSources = emptyList(),
                usedWebSearch = false,
                steps = steps
            )
        }

        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = "Gemini 3.8 Flash Vision",
                stageLabel = "Inspecting shared screen & UI context...",
                progressPercent = 30,
                completedSteps = steps.toList()
            )
        )

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                flashModel.generateContentStream(
                    content {
                        image(attachedBitmap)
                        text(promptText)
                    }
                ).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onStreamToken(accumulated.toString())
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vision stream timed out", e)
            throw IllegalStateException("Screen analysis timed out after 60 seconds. Please retry.")
        } catch (e: CancellationException) {
            throw e
        }

        val finalAnswer = accumulated.toString().trim()
            .ifBlank { throw IllegalStateException("Vision model returned an empty response.") }

        steps.add("Screen insights synthesized by Vision Swarm")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = "Vision Swarm",
                stageLabel = "Screen analysis complete",
                progressPercent = 100,
                completedSteps = steps.toList()
            )
        )

        return SwarmResult(
            finalText = finalAnswer,
            primaryEngineLabel = "Aura Vision Swarm (${visionModels.size} Models)",
            collaboratingModels = visionModels,
            searchSources = emptyList(),
            usedWebSearch = false,
            steps = steps
        )
    }

    private suspend fun executePureNimCollaborativeSwarm(
        userPrompt: String,
        conversationContext: String,
        selectedSwarm: List<AiModelSpec>,
        onStageUpdate: (SwarmStepUpdate) -> Unit,
        onStreamToken: (String) -> Unit
    ): SwarmResult = coroutineScope {
        val nimPool = selectedSwarm.filter { it.provider == ModelProvider.NVIDIA_NIM }
            .ifEmpty {
                AiModelsCatalog.allModels.filter { it.provider == ModelProvider.NVIDIA_NIM }.take(3)
            }
        val specialistA = nimPool.getOrElse(0) { defaultNimModel() }
        val specialistB = nimPool.getOrElse(1) {
            AiModelsCatalog.allModels.first { it.provider == ModelProvider.NVIDIA_NIM && it.id != specialistA.id }
        }
        val synthesizer = nimPool.getOrElse(2) { defaultNimModel() }
        val teamNames = listOf(specialistA.displayName, specialistB.displayName, synthesizer.displayName).distinct()

        val steps = mutableListOf("NIM Swarm Router picked ${teamNames.joinToString(" + ")}")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = specialistA.displayName,
                stageLabel = "Stage 1/2: Parallel NVIDIA NIM reasoning...",
                progressPercent = 30,
                completedSteps = steps.toList()
            )
        )

        val draftADeferred = async {
            tryCallNvidiaNim(
                model = specialistA,
                prompt = "Provide concise, high-signal technical & strategic analysis (max 240 words) for: $userPrompt",
                context = conversationContext
            )
        }
        val draftBDeferred = async {
            tryCallNvidiaNim(
                model = specialistB,
                prompt = "Provide an alternative verification, actionable steps & edge-case check (max 220 words) for: $userPrompt",
                context = ""
            )
        }

        val draftA = draftADeferred.await().orEmpty()
        val draftB = draftBDeferred.await().orEmpty()

        if (draftA.isBlank() && draftB.isBlank()) {
            throw IllegalStateException(
                "NVIDIA NIM models did not respond. Please verify your internet connection and nvapi- key in the 33 Models tab."
            )
        }

        steps.add("${specialistA.displayName} & ${specialistB.displayName} finished parallel pass")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = synthesizer.displayName,
                stageLabel = "Stage 2/2: Synthesizing Max Power answer...",
                progressPercent = 75,
                completedSteps = steps.toList()
            )
        )

        val synthesisPrompt = buildString {
            append("You are Aura AI's Max Power Multi-Model Synthesizer.\n")
            append("Combine the best insights from ${teamNames.joinToString(", ")}.\n")
            append("Respond in the user's language (English or Hinglish). Make the answer crisp, structured, and actionable.\n\n")
            if (conversationContext.isNotBlank()) {
                append("Conversation Context:\n$conversationContext\n\n")
            }
            append("User Request:\n$userPrompt\n\n")
            if (draftA.isNotBlank()) append("--- ${specialistA.displayName} ---\n$draftA\n\n")
            if (draftB.isNotBlank()) append("--- ${specialistB.displayName} ---\n$draftB\n\n")
            append("Synthesize the strongest verified final answer:")
        }

        val finalText = tryCallNvidiaNim(synthesizer, synthesisPrompt, "")
            ?.takeIf { it.isNotBlank() }
            ?: listOf(draftA, draftB).first { it.isNotBlank() }

        onStreamToken(finalText)
        steps.add("NVIDIA NIM Max Power synthesis complete")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = "NVIDIA NIM Swarm",
                stageLabel = "All ${teamNames.size} models finished",
                progressPercent = 100,
                completedSteps = steps.toList()
            )
        )

        SwarmResult(
            finalText = finalText,
            primaryEngineLabel = "NVIDIA NIM Max Swarm (${teamNames.size} Models)",
            collaboratingModels = teamNames,
            searchSources = emptyList(),
            usedWebSearch = false,
            steps = steps
        )
    }

    private suspend fun executeCollaborativeSwarm(
        userPrompt: String,
        conversationContext: String,
        selectedSwarm: List<AiModelSpec>,
        useGoogleSearch: Boolean,
        onStageUpdate: (SwarmStepUpdate) -> Unit,
        onStreamToken: (String) -> Unit
    ): SwarmResult = coroutineScope {
        val steps = mutableListOf<String>()
        val specialistA = selectedSwarm.getOrElse(0) { AiModelsCatalog.allModels[3] }
        val specialistB = selectedSwarm.getOrElse(1) { AiModelsCatalog.allModels[7] }
        val synthesizer = selectedSwarm.getOrElse(2) { AiModelsCatalog.allModels[0] }
        val teamNames = listOf(specialistA.displayName, specialistB.displayName, synthesizer.displayName).distinct()

        steps.add("Auto-Router picked ${teamNames.joinToString(" + ")}")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = specialistA.displayName,
                stageLabel = "Stage 1/3: Parallel reasoning & research...",
                progressPercent = 25,
                completedSteps = steps.toList()
            )
        )

        val collectedSources = mutableListOf<String>()

        // Run Specialist A (NVIDIA NIM if configured, else Gemini specialist) + Specialist B (Google Search or Deep Logic) in parallel
        val specialistADeferred = async {
            if (specialistA.provider == ModelProvider.NVIDIA_NIM && NvidiaNimClient.isConfigured()) {
                tryCallNvidiaNim(
                    model = specialistA,
                    prompt = "Provide concise, high-signal technical & strategic analysis (max 220 words) for: $userPrompt",
                    context = conversationContext
                ) ?: runFastSpecialist(specialistA, userPrompt)
            } else {
                runFastSpecialist(specialistA, userPrompt)
            }
        }

        val specialistBDeferred = async {
            if (useGoogleSearch) {
                val (searchSummary, foundSources) = runSearchGroundedGeneration(
                    "Perform live Google Search research and gather accurate, up-to-date facts, links, and key points (max 240 words) for: $userPrompt"
                )
                synchronized(collectedSources) {
                    collectedSources.addAll(foundSources)
                }
                searchSummary
            } else if (specialistB.provider == ModelProvider.NVIDIA_NIM && NvidiaNimClient.isConfigured()) {
                tryCallNvidiaNim(
                    model = specialistB,
                    prompt = "Provide an alternative verification & edge-case critique (max 200 words) for: $userPrompt",
                    context = ""
                ) ?: runFastSpecialist(specialistB, userPrompt)
            } else {
                runFastSpecialist(specialistB, userPrompt)
            }
        }

        val draftA = specialistADeferred.await()
        val draftB = specialistBDeferred.await()

        steps.add("${specialistA.displayName} finished specialist pass")
        steps.add(
            if (useGoogleSearch) "Google Search grounded ${collectedSources.size} web sources"
            else "${specialistB.displayName} verified logic & edge cases"
        )
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = synthesizer.displayName,
                stageLabel = "Stage 2/3: Max Power synthesis streaming...",
                progressPercent = 72,
                completedSteps = steps.toList()
            )
        )

        val synthesisPrompt = buildString {
            append("You are Aura AI's Max Power Multi-Model Synthesizer.\n")
            append("You are combining real collaborative insights from ${teamNames.joinToString(", ")}.\n")
            append("Respond in the same language/style as the user (English or Hinglish). Make the answer crisp, well-structured, actionable, and executive-grade.\n\n")
            if (conversationContext.isNotBlank()) {
                append("Conversation Context:\n$conversationContext\n\n")
            }
            append("User Request:\n$userPrompt\n\n")
            append("--- Input from ${specialistA.displayName} ---\n$draftA\n\n")
            append("--- Input from ${if (useGoogleSearch) "Live Google Search + ${specialistB.displayName}" else specialistB.displayName} ---\n$draftB\n\n")
            append("Synthesize the strongest, verified final answer now:")
        }

        val finalText = streamGemini(synthesisPrompt, onStreamToken)

        steps.add("Max Power synthesis completed by ${teamNames.size} collaborating models")
        onStageUpdate(
            SwarmStepUpdate(
                activeModelName = "Aura Max Swarm",
                stageLabel = "All ${teamNames.size} models finished",
                progressPercent = 100,
                completedSteps = steps.toList()
            )
        )

        SwarmResult(
            finalText = finalText,
            primaryEngineLabel = if (NvidiaNimClient.isConfigured()) {
                "NIM + Gemini Max Swarm (${teamNames.size} Models)"
            } else {
                "Aura Max Swarm (${teamNames.size} Models)"
            },
            collaboratingModels = teamNames,
            searchSources = collectedSources.distinct().take(8),
            usedWebSearch = useGoogleSearch,
            steps = steps
        )
    }

    /**
     * Splits a complex user goal into 3-5 concrete parallel tasks using Gemini Structured JSON output
     * (or NVIDIA NIM JSON fallback on standalone APK builds).
     */
    suspend fun splitGoalIntoTasks(goal: String): List<PlannedSubTask> {
        requireAnyBackendReady()

        val prompt = """
            Break down the following user goal into 3 to 5 clear, parallelizable autonomous tasks that an AI assistant swarm can execute immediately.
            Keep titles short and punchy. Match the user's language (English or Hinglish).
            Goal: $goal
        """.trimIndent()

        if (!isFirebaseAiAvailable) {
            val nimJsonPrompt = """
                $prompt
                Respond ONLY with valid JSON matching this exact structure (no markdown fences):
                {"tasks":[{"title":"Short title","prompt":"Full instructions","category":"GENERAL|RESEARCH|CODE|WRITING|PLANNING|SCREEN|AUTOMATION","priority":"LOW|NORMAL|HIGH|URGENT"}]}
            """.trimIndent()
            val raw = tryCallNvidiaNim(defaultNimModel(), nimJsonPrompt, "")
                ?: throw IllegalStateException("Task planner could not reach NVIDIA NIM. Check your API key in 33 Models.")
            return parsePlannedTasksJson(raw, goal)
        }

        try {
            val response = withTimeout(45_000L) {
                taskSplitterModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Task planner returned empty output.")
            return parsePlannedTasksJson(rawJson, goal)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Task splitter timed out", e)
            throw IllegalStateException("Task planning timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Task splitting failed", e)
            throw e
        }
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

    private fun defaultNimModel(): AiModelSpec {
        return AiModelsCatalog.allModels.firstOrNull {
            it.id == "meta/llama-3.3-70b-instruct"
        } ?: AiModelsCatalog.allModels.first { it.provider == ModelProvider.NVIDIA_NIM }
    }

    private suspend fun runFastSpecialist(model: AiModelSpec, prompt: String): String {
        return try {
            withTimeout(35_000L) {
                val response = fastRouterModel.generateContent(
                    "${model.roleInstruction}\nProvide a focused, high-density specialist brief (under 220 words) for:\n$prompt"
                )
                response.text?.trim().orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Fast specialist pass fallback: ${e.message}")
            ""
        }
    }

    private suspend fun runSearchGroundedGeneration(prompt: String): Pair<String, List<String>> {
        return try {
            withTimeout(55_000L) {
                val response = searchGroundedModel.generateContent(prompt)
                val text = response.text?.trim().orEmpty()
                val sources = mutableListOf<String>()
                response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { chunk ->
                    val web = chunk.web
                    val title = web?.title?.trim().orEmpty()
                    val uri = web?.uri?.trim().orEmpty()
                    if (uri.isNotBlank()) {
                        sources.add(if (title.isNotBlank()) "$title|$uri" else uri)
                    }
                }
                if (text.isBlank()) {
                    throw IllegalStateException("Search model returned empty response.")
                }
                text to sources
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Google Search grounded call failed, retrying standard Gemini: ${e.message}")
            val fallbackText = withTimeout(45_000L) {
                flashModel.generateContent(prompt).text?.trim()
                    ?: throw IllegalStateException("Model returned empty response.")
            }
            fallbackText to emptyList()
        }
    }

    private suspend fun streamGemini(
        prompt: String,
        onStreamToken: (String) -> Unit
    ): String {
        val builder = StringBuilder()
        try {
            withTimeout(60_000L) {
                flashModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        builder.append(piece)
                        onStreamToken(builder.toString())
                    }
                }
            }
            val result = builder.toString().trim()
            if (result.isBlank()) {
                throw IllegalStateException("Model returned an empty response.")
            }
            return result
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini stream timed out", e)
            throw IllegalStateException("AI generation timed out after 60 seconds. Please retry.")
        } catch (e: CancellationException) {
            throw e
        }
    }

    private suspend fun tryCallNvidiaNim(
        model: AiModelSpec,
        prompt: String,
        context: String
    ): String? {
        return try {
            withTimeout(45_000L) {
                val messages = mutableListOf(
                    NimMessage("system", model.roleInstruction)
                )
                if (context.isNotBlank()) {
                    messages.add(NimMessage("user", "Conversation context:\n$context"))
                }
                messages.add(NimMessage("user", prompt))

                val response = NvidiaNimClient.service.chatCompletion(
                    authorization = NvidiaNimClient.authHeader(),
                    request = NimChatRequest(
                        model = model.id,
                        messages = messages,
                        temperature = 0.6,
                        maxTokens = 2048
                    )
                )
                response.choices?.firstOrNull()?.message?.content?.trim()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "NVIDIA NIM call failed for ${model.id}: ${e.message}")
            null
        }
    }

    private suspend fun tryCallNvidiaNimVision(
        modelId: String,
        prompt: String,
        bitmap: Bitmap
    ): String? {
        return try {
            withTimeout(55_000L) {
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
                response.choices?.firstOrNull()?.message?.content?.trim()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "NVIDIA NIM vision call failed for $modelId: ${e.message}")
            null
        }
    }
}
