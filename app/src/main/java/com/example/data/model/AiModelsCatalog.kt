package com.example.data.model

enum class ModelProvider(val label: String) {
    NVIDIA_NIM("NVIDIA NIM"),
    FIREBASE_GEMINI("Gemini AI Logic")
}

enum class ModelSpecialty(val label: String) {
    REASONING("Reasoning"),
    CODING("Coding"),
    VISION("Vision"),
    SEARCH("Web Search"),
    SPEED("Ultra Fast"),
    CREATIVE("Creative"),
    MATH("Math & STEM"),
    AGENTIC("24h Agentic")
}

data class AiModelSpec(
    val id: String,
    val displayName: String,
    val vendor: String,
    val provider: ModelProvider,
    val specialty: ModelSpecialty,
    val parameterScale: String,
    val powerScore: Int, // 1..100
    val speedScore: Int, // 1..100
    val supportsVision: Boolean = false,
    val supportsSearch: Boolean = false,
    val accentHexStart: Long,
    val accentHexEnd: Long,
    val shortBadge: String,
    val roleInstruction: String
)

object AiModelsCatalog {

    const val AUTO_SWARM_ID = "auto-swarm"

    // Exactly 33 curated models: 30 NVIDIA NIM Free Endpoint catalog models + 3 Firebase AI Logic Gemini models
    val allModels: List<AiModelSpec> = listOf(
        // --- Core Firebase AI Logic Flagships (3) ---
        AiModelSpec(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            vendor = "Google",
            provider = ModelProvider.FIREBASE_GEMINI,
            specialty = ModelSpecialty.SEARCH,
            parameterScale = "Frontier",
            powerScore = 97,
            speedScore = 96,
            supportsVision = true,
            supportsSearch = true,
            accentHexStart = 0xFF0A84FF,
            accentHexEnd = 0xFF38BDF8,
            shortBadge = "G3.8",
            roleInstruction = "You are the Frontier Multimodal & Web-Grounded Synthesizer. Deliver crisp, verified, structured answers with maximum clarity."
        ),
        AiModelSpec(
            id = "gemini-4-argon",
            displayName = "Gemini 4 Argon",
            vendor = "Google",
            provider = ModelProvider.FIREBASE_GEMINI,
            specialty = ModelSpecialty.REASONING,
            parameterScale = "Ultra",
            powerScore = 100,
            speedScore = 84,
            supportsVision = true,
            supportsSearch = true,
            accentHexStart = 0xFF6366F1,
            accentHexEnd = 0xFF06B6D4,
            shortBadge = "G4A",
            roleInstruction = "You are the Deep Strategic Reasoner. Analyze edge cases, multi-step logic, architecture, and high-stakes decisions."
        ),
        AiModelSpec(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            vendor = "Google",
            provider = ModelProvider.FIREBASE_GEMINI,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "Lite",
            powerScore = 88,
            speedScore = 100,
            supportsVision = true,
            supportsSearch = false,
            accentHexStart = 0xFF0EA5E9,
            accentHexEnd = 0xFF2DD4BF,
            shortBadge = "G3.5",
            roleInstruction = "You are the Ultra-Fast Swarm Router & Triage Specialist. Decompose intents cleanly and rapidly."
        ),

        // --- NVIDIA NIM Free-Tier Catalog Models (30) ---
        AiModelSpec(
            id = "nvidia/llama-3.1-nemotron-ultra-253b-v1",
            displayName = "Nemotron Ultra 253B",
            vendor = "NVIDIA",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.REASONING,
            parameterScale = "253B",
            powerScore = 99,
            speedScore = 76,
            accentHexStart = 0xFF76B900,
            accentHexEnd = 0xFF10B981,
            shortBadge = "NU253",
            roleInstruction = "Operate as NVIDIA Nemotron Ultra 253B: deep chain-of-thought reasoning, scientific rigor, and zero-fluff precision."
        ),
        AiModelSpec(
            id = "nvidia/llama-3.3-nemotron-super-49b-v1",
            displayName = "Nemotron Super 49B",
            vendor = "NVIDIA",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "49B",
            powerScore = 95,
            speedScore = 90,
            accentHexStart = 0xFF65A30D,
            accentHexEnd = 0xFF059669,
            shortBadge = "NS49",
            roleInstruction = "Operate as NVIDIA Nemotron Super 49B: autonomous 24h task planning, tool decomposition, and actionable checklists."
        ),
        AiModelSpec(
            id = "nvidia/llama-3.1-nemotron-70b-instruct",
            displayName = "Nemotron 70B Instruct",
            vendor = "NVIDIA",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "70B",
            powerScore = 94,
            speedScore = 86,
            accentHexStart = 0xFF84CC16,
            accentHexEnd = 0xFF14B8A6,
            shortBadge = "N70B",
            roleInstruction = "Operate as NVIDIA Nemotron 70B Instruct: high-helpfulness instruction following and executive execution."
        ),
        AiModelSpec(
            id = "nvidia/llama-3.1-nemotron-nano-8b-v1",
            displayName = "Nemotron Nano 8B",
            vendor = "NVIDIA",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "8B",
            powerScore = 84,
            speedScore = 99,
            accentHexStart = 0xFF4ADE80,
            accentHexEnd = 0xFF06B6D4,
            shortBadge = "NN8B",
            roleInstruction = "Operate as NVIDIA Nemotron Nano 8B: instant lightweight response specialist."
        ),
        AiModelSpec(
            id = "deepseek-ai/deepseek-r1",
            displayName = "DeepSeek R1",
            vendor = "DeepSeek",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.REASONING,
            parameterScale = "671B MoE",
            powerScore = 99,
            speedScore = 75,
            accentHexStart = 0xFF3B82F6,
            accentHexEnd = 0xFF8B5CF6,
            shortBadge = "DSR1",
            roleInstruction = "Operate as DeepSeek R1: first-principles mathematical, algorithmic, and logical deduction."
        ),
        AiModelSpec(
            id = "deepseek-ai/deepseek-r1-distill-llama-70b",
            displayName = "DeepSeek R1 Llama 70B",
            vendor = "DeepSeek",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.MATH,
            parameterScale = "70B",
            powerScore = 93,
            speedScore = 87,
            accentHexStart = 0xFF2563EB,
            accentHexEnd = 0xFF4F46E5,
            shortBadge = "R1-70",
            roleInstruction = "Operate as DeepSeek R1 Distill 70B: fast structured STEM verification and quantitative reasoning."
        ),
        AiModelSpec(
            id = "deepseek-ai/deepseek-r1-distill-qwen-32b",
            displayName = "DeepSeek R1 Qwen 32B",
            vendor = "DeepSeek",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.MATH,
            parameterScale = "32B",
            powerScore = 91,
            speedScore = 91,
            accentHexStart = 0xFF1D4ED8,
            accentHexEnd = 0xFF0EA5E9,
            shortBadge = "R1-32",
            roleInstruction = "Operate as DeepSeek R1 Qwen 32B: compact formal proof and symbolic math specialist."
        ),
        AiModelSpec(
            id = "meta/llama-3.3-70b-instruct",
            displayName = "Llama 3.3 70B Instruct",
            vendor = "Meta",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "70B",
            powerScore = 95,
            speedScore = 89,
            accentHexStart = 0xFF0284C7,
            accentHexEnd = 0xFF2563EB,
            shortBadge = "L3.3",
            roleInstruction = "Operate as Meta Llama 3.3 70B: balanced multilingual reasoning, synthesis, and workflow execution."
        ),
        AiModelSpec(
            id = "meta/llama-3.1-405b-instruct",
            displayName = "Llama 3.1 405B Flagship",
            vendor = "Meta",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.REASONING,
            parameterScale = "405B",
            powerScore = 98,
            speedScore = 70,
            accentHexStart = 0xFF1E40AF,
            accentHexEnd = 0xFF0EA5E9,
            shortBadge = "L405",
            roleInstruction = "Operate as Meta Llama 3.1 405B: encyclopedia-depth synthesis and complex domain mastery."
        ),
        AiModelSpec(
            id = "meta/llama-3.2-90b-vision-instruct",
            displayName = "Llama 3.2 90B Vision",
            vendor = "Meta",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.VISION,
            parameterScale = "90B",
            powerScore = 94,
            speedScore = 82,
            supportsVision = true,
            accentHexStart = 0xFF0369A1,
            accentHexEnd = 0xFF38BDF8,
            shortBadge = "L90V",
            roleInstruction = "Operate as Llama 3.2 90B Vision: UI screen inspection, diagram reading, and visual troubleshooting."
        ),
        AiModelSpec(
            id = "meta/llama-3.2-11b-vision-instruct",
            displayName = "Llama 3.2 11B Vision",
            vendor = "Meta",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.VISION,
            parameterScale = "11B",
            powerScore = 87,
            speedScore = 95,
            supportsVision = true,
            accentHexStart = 0xFF0891B2,
            accentHexEnd = 0xFF22D3EE,
            shortBadge = "L11V",
            roleInstruction = "Operate as Llama 3.2 11B Vision: rapid screenshot OCR and interface context extraction."
        ),
        AiModelSpec(
            id = "meta/llama-3.1-8b-instruct",
            displayName = "Llama 3.1 8B Fast",
            vendor = "Meta",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "8B",
            powerScore = 82,
            speedScore = 98,
            accentHexStart = 0xFF38BDF8,
            accentHexEnd = 0xFF818CF8,
            shortBadge = "L8B",
            roleInstruction = "Operate as Llama 3.1 8B: snappy low-latency assistant for quick tasks."
        ),
        AiModelSpec(
            id = "qwen/qwen2.5-coder-32b-instruct",
            displayName = "Qwen 2.5 Coder 32B",
            vendor = "Alibaba Qwen",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CODING,
            parameterScale = "32B",
            powerScore = 96,
            speedScore = 90,
            accentHexStart = 0xFF7C3AED,
            accentHexEnd = 0xFFA855F7,
            shortBadge = "QC32",
            roleInstruction = "Operate as Qwen 2.5 Coder 32B: production-grade Kotlin, Python, TypeScript, architecture, and debugging."
        ),
        AiModelSpec(
            id = "qwen/qwq-32b",
            displayName = "Qwen QwQ 32B Think",
            vendor = "Alibaba Qwen",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.REASONING,
            parameterScale = "32B",
            powerScore = 95,
            speedScore = 86,
            accentHexStart = 0xFF6D28D9,
            accentHexEnd = 0xFFEC4899,
            shortBadge = "QwQ",
            roleInstruction = "Operate as QwQ 32B: systematic self-checking reasoning and complex constraint solving."
        ),
        AiModelSpec(
            id = "qwen/qwen2.5-72b-instruct",
            displayName = "Qwen 2.5 72B Instruct",
            vendor = "Alibaba Qwen",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "72B",
            powerScore = 95,
            speedScore = 85,
            accentHexStart = 0xFF8B5CF6,
            accentHexEnd = 0xFF38BDF8,
            shortBadge = "Q72B",
            roleInstruction = "Operate as Qwen 2.5 72B: structured data analysis, multilingual Hinglish/English fluency, and long context."
        ),
        AiModelSpec(
            id = "qwen/qwen2.5-7b-instruct",
            displayName = "Qwen 2.5 7B Turbo",
            vendor = "Alibaba Qwen",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "7B",
            powerScore = 84,
            speedScore = 98,
            accentHexStart = 0xFFA78BFA,
            accentHexEnd = 0xFF60A5FA,
            shortBadge = "Q7B",
            roleInstruction = "Operate as Qwen 2.5 7B: ultra-fast drafting and formatting."
        ),
        AiModelSpec(
            id = "mistralai/mistral-large-2-instruct",
            displayName = "Mistral Large 2",
            vendor = "Mistral AI",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CREATIVE,
            parameterScale = "123B",
            powerScore = 96,
            speedScore = 82,
            accentHexStart = 0xFFF97316,
            accentHexEnd = 0xFFEF4444,
            shortBadge = "ML2",
            roleInstruction = "Operate as Mistral Large 2: articulate writing, executive synthesis, and polyglot clarity."
        ),
        AiModelSpec(
            id = "mistralai/mixtral-8x22b-instruct-v0.1",
            displayName = "Mixtral 8x22B MoE",
            vendor = "Mistral AI",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CODING,
            parameterScale = "141B MoE",
            powerScore = 92,
            speedScore = 86,
            accentHexStart = 0xFFEA580C,
            accentHexEnd = 0xFFF59E0B,
            shortBadge = "M8x22",
            roleInstruction = "Operate as Mixtral 8x22B MoE: versatile Mixture-of-Experts coding and analysis."
        ),
        AiModelSpec(
            id = "mistralai/codestral-22b-instruct-v0.1",
            displayName = "Codestral 22B",
            vendor = "Mistral AI",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CODING,
            parameterScale = "22B",
            powerScore = 92,
            speedScore = 93,
            accentHexStart = 0xFFF59E0B,
            accentHexEnd = 0xFFFB7185,
            shortBadge = "CD22",
            roleInstruction = "Operate as Codestral 22B: blazing-fast code generation, refactoring, and unit test synthesis."
        ),
        AiModelSpec(
            id = "mistralai/mathstral-7b-v0.1",
            displayName = "Mathstral 7B",
            vendor = "Mistral AI",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.MATH,
            parameterScale = "7B",
            powerScore = 88,
            speedScore = 96,
            accentHexStart = 0xFFD97706,
            accentHexEnd = 0xFFFACC15,
            shortBadge = "MT7B",
            roleInstruction = "Operate as Mathstral 7B: step-by-step equations, calculus, probability, and finance math."
        ),
        AiModelSpec(
            id = "mistralai/mistral-nemo-12b-instruct",
            displayName = "Mistral NeMo 12B",
            vendor = "Mistral AI",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "12B",
            powerScore = 86,
            speedScore = 95,
            accentHexStart = 0xFFFB923C,
            accentHexEnd = 0xFFF472B6,
            shortBadge = "MN12",
            roleInstruction = "Operate as Mistral NeMo 12B: clean concise summaries and rapid Q&A."
        ),
        AiModelSpec(
            id = "google/gemma-2-27b-it",
            displayName = "Gemma 2 27B IT",
            vendor = "Google Open",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CREATIVE,
            parameterScale = "27B",
            powerScore = 91,
            speedScore = 91,
            accentHexStart = 0xFF10B981,
            accentHexEnd = 0xFF3B82F6,
            shortBadge = "GM27",
            roleInstruction = "Operate as Gemma 2 27B: clear writing, brainstorming, and approachable explanations."
        ),
        AiModelSpec(
            id = "google/gemma-2-9b-it",
            displayName = "Gemma 2 9B IT",
            vendor = "Google Open",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "9B",
            powerScore = 85,
            speedScore = 97,
            accentHexStart = 0xFF34D399,
            accentHexEnd = 0xFF38BDF8,
            shortBadge = "GM9B",
            roleInstruction = "Operate as Gemma 2 9B: ultra-snappy phrasing and quick checklist generation."
        ),
        AiModelSpec(
            id = "microsoft/phi-4-mini-instruct",
            displayName = "Phi-4 Mini Instruct",
            vendor = "Microsoft",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.MATH,
            parameterScale = "3.8B",
            powerScore = 89,
            speedScore = 99,
            accentHexStart = 0xFF0EA5E9,
            accentHexEnd = 0xFF10B981,
            shortBadge = "PHI4",
            roleInstruction = "Operate as Microsoft Phi-4 Mini: razor-sharp compact logic and textbook precision."
        ),
        AiModelSpec(
            id = "microsoft/phi-3.5-vision-instruct",
            displayName = "Phi-3.5 Vision",
            vendor = "Microsoft",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.VISION,
            parameterScale = "4.2B",
            powerScore = 86,
            speedScore = 96,
            supportsVision = true,
            accentHexStart = 0xFF0284C7,
            accentHexEnd = 0xFF14B8A6,
            shortBadge = "P35V",
            roleInstruction = "Operate as Phi-3.5 Vision: dense chart reading, document OCR, and visual reasoning."
        ),
        AiModelSpec(
            id = "nvidia/neva-22b",
            displayName = "NVIDIA NeVA 22B",
            vendor = "NVIDIA",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.VISION,
            parameterScale = "22B",
            powerScore = 90,
            speedScore = 88,
            supportsVision = true,
            accentHexStart = 0xFF76B900,
            accentHexEnd = 0xFF06B6D4,
            shortBadge = "NEVA",
            roleInstruction = "Operate as NVIDIA NeVA 22B: visual scene comprehension and multimodal assistant."
        ),
        AiModelSpec(
            id = "nvidia/vila",
            displayName = "NVIDIA VILA Multimodal",
            vendor = "NVIDIA",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.VISION,
            parameterScale = "40B",
            powerScore = 92,
            speedScore = 84,
            supportsVision = true,
            accentHexStart = 0xFF84CC16,
            accentHexEnd = 0xFF22C55E,
            shortBadge = "VILA",
            roleInstruction = "Operate as NVIDIA VILA: multi-frame screen context and live UI guidance."
        ),
        AiModelSpec(
            id = "ibm/granite-3.1-8b-instruct",
            displayName = "IBM Granite 3.1 8B",
            vendor = "IBM",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "8B",
            powerScore = 86,
            speedScore = 96,
            accentHexStart = 0xFF2563EB,
            accentHexEnd = 0xFF06B6D4,
            shortBadge = "GR31",
            roleInstruction = "Operate as IBM Granite 3.1: enterprise workflow structuring, compliance, and reliable tool calling."
        ),
        AiModelSpec(
            id = "ibm/granite-34b-code-instruct",
            displayName = "IBM Granite 34B Code",
            vendor = "IBM",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CODING,
            parameterScale = "34B",
            powerScore = 90,
            speedScore = 88,
            accentHexStart = 0xFF1D4ED8,
            accentHexEnd = 0xFF3B82F6,
            shortBadge = "GC34",
            roleInstruction = "Operate as IBM Granite 34B Code: clean backend engineering, SQL, and DevOps scripts."
        ),
        AiModelSpec(
            id = "ai21labs/jamba-1.5-large-instruct",
            displayName = "Jamba 1.5 Large",
            vendor = "AI21 Labs",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CREATIVE,
            parameterScale = "398B SSM",
            powerScore = 94,
            speedScore = 84,
            accentHexStart = 0xFFEC4899,
            accentHexEnd = 0xFF8B5CF6,
            shortBadge = "JM15",
            roleInstruction = "Operate as Jamba 1.5 Large: hybrid Mamba-Transformer long-document executive synthesis."
        ),
        AiModelSpec(
            id = "snowflake/arctic",
            displayName = "Snowflake Arctic",
            vendor = "Snowflake",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CODING,
            parameterScale = "480B MoE",
            powerScore = 91,
            speedScore = 85,
            accentHexStart = 0xFF38BDF8,
            accentHexEnd = 0xFF0284C7,
            shortBadge = "ARCT",
            roleInstruction = "Operate as Snowflake Arctic: SQL analytics, data engineering, and KPI insights."
        ),
        AiModelSpec(
            id = "upstage/solar-10.7b-instruct",
            displayName = "Upstage SOLAR 10.7B",
            vendor = "Upstage",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.CREATIVE,
            parameterScale = "10.7B",
            powerScore = 85,
            speedScore = 94,
            accentHexStart = 0xFFF59E0B,
            accentHexEnd = 0xFFEC4899,
            shortBadge = "SOLR",
            roleInstruction = "Operate as Upstage SOLAR 10.7B: crisp copywriting and conversational warmth."
        ),
        AiModelSpec(
            id = "databricks/dbrx-instruct",
            displayName = "Databricks DBRX",
            vendor = "Databricks",
            provider = ModelProvider.NVIDIA_NIM,
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "132B MoE",
            powerScore = 92,
            speedScore = 84,
            accentHexStart = 0xFFEF4444,
            accentHexEnd = 0xFFF97316,
            shortBadge = "DBRX",
            roleInstruction = "Operate as Databricks DBRX: analytical data pipeline orchestration and synthesis."
        )
    )

    fun findById(id: String): AiModelSpec? = allModels.firstOrNull { it.id == id }

    /**
     * Automatically selects the strongest collaborating trio from the 33-model roster
     * tailored to the user's prompt, vision attachment, or web search need.
     */
    fun selectBestSwarmForPrompt(
        prompt: String,
        hasImage: Boolean,
        needsSearch: Boolean,
        pinnedModelId: String
    ): List<AiModelSpec> {
        val lower = prompt.lowercase()
        val chosen = mutableListOf<AiModelSpec>()

        if (pinnedModelId != AUTO_SWARM_ID) {
            findById(pinnedModelId)?.let { chosen.add(it) }
        }

        if (hasImage) {
            chosen.add(findById("gemini-3.8-flash")!!)
            chosen.add(findById("meta/llama-3.2-90b-vision-instruct")!!)
            chosen.add(findById("nvidia/vila")!!)
        } else if (listOf("code", "kotlin", "python", "bug", "error", "api", "app", "html", "sql", "function", "build").any { lower.contains(it) }) {
            chosen.add(findById("qwen/qwen2.5-coder-32b-instruct")!!)
            chosen.add(findById("deepseek-ai/deepseek-r1")!!)
            chosen.add(findById("gemini-3.8-flash")!!)
        } else if (listOf("math", "calculate", "solve", "equation", "physics", "formula", "profit", "percent").any { lower.contains(it) }) {
            chosen.add(findById("deepseek-ai/deepseek-r1")!!)
            chosen.add(findById("nvidia/llama-3.1-nemotron-ultra-253b-v1")!!)
            chosen.add(findById("microsoft/phi-4-mini-instruct")!!)
        } else if (needsSearch || listOf("latest", "today", "news", "price", "weather", "search", "google", "2026", "current", "live").any { lower.contains(it) }) {
            chosen.add(findById("gemini-3.8-flash")!!)
            chosen.add(findById("nvidia/llama-3.1-nemotron-ultra-253b-v1")!!)
            chosen.add(findById("meta/llama-3.3-70b-instruct")!!)
        } else if (listOf("write", "email", "story", "script", "post", "caption", "blog", "letter").any { lower.contains(it) }) {
            chosen.add(findById("mistralai/mistral-large-2-instruct")!!)
            chosen.add(findById("google/gemma-2-27b-it")!!)
            chosen.add(findById("gemini-3.8-flash")!!)
        } else {
            chosen.add(findById("nvidia/llama-3.1-nemotron-ultra-253b-v1")!!)
            chosen.add(findById("deepseek-ai/deepseek-r1")!!)
            chosen.add(findById("gemini-3.8-flash")!!)
        }

        return chosen.distinctBy { it.id }.take(3)
    }
}
