package com.example.data.model

enum class ModelProvider(val label: String) {
    NVIDIA_NIM("NVIDIA NIM")
}

enum class ModelModality(val label: String) {
    CHAT("Chat"),
    VISION("Vision"),
    EMBEDDING("Embedding"),
    TRANSLATION("Translation")
}

enum class ModelSpecialty(val label: String) {
    TRENDING("Trending"),
    REASONING("Reasoning"),
    CODING("Coding"),
    VISION("Vision"),
    SPEED("Ultra Fast"),
    CREATIVE("Creative"),
    MATH("Math & STEM"),
    AGENTIC("24h Agentic")
}

enum class SpecialistRole(val label: String, val summary: String) {
    MANAGER("Manager", "Complex multi-step planning & final synthesis (ONE big model)"),
    FAST_CHAT("Fast Chat", "Low-latency daily replies, voice & simple Q&A"),
    REASONING_MATH("Reasoning & Math", "Deep logic, proofs, architecture & constraints"),
    CODING("Coding", "Kotlin, Python, TypeScript, debugging & code review"),
    PLANNING_TASKS("Planning & Tasks", "Structured Android workflows & 24h task splitting"),
    VISION_SCREEN("Vision & Screen", "Live screen inspection, screenshots & OCR"),
    HINDI_CREATIVE("Hindi & Creative", "Hindi/Hinglish fluency, writing & summaries"),
    LONG_CONTEXT("Long Context", "Large documents, logs & 50K+ prompt synthesis")
}

data class RolePoolConfig(
    val role: SpecialistRole,
    val nemotronPrimaryIds: List<String>,
    val backupAId: String,
    val backupBId: String
)

data class AiModelSpec(
    val id: String,
    val displayName: String,
    val vendor: String,
    val provider: ModelProvider = ModelProvider.NVIDIA_NIM,
    val modality: ModelModality = ModelModality.CHAT,
    val specialty: ModelSpecialty,
    val parameterScale: String,
    val contextWindow: String = "128K",
    val contextTokens: Int = 128_000,
    val powerScore: Int, // 1..100
    val speedScore: Int, // 1..100
    val supportsVision: Boolean = false,
    val supportsThinking: Boolean = false,
    val isTrending: Boolean = false,
    val isNemotron: Boolean = false,
    val isCandidateSlot: Boolean = false,
    val endpointPath: String = "v1/chat/completions",
    val accentHexStart: Long,
    val accentHexEnd: Long,
    val shortBadge: String,
    val roleInstruction: String
)

object AiModelsCatalog {

    const val AUTO_SWARM_ID = "auto-swarm"

    /**
     * Nemotron-first + ONE Manager + precisely TWO optional backups per role.
     * Each role lists primary Nemotron candidates first (newest starter candidates + proven stable
     * NVIDIA NIM Nemotron endpoints), followed by strictly TWO cross-vendor backups (Backup A & B).
     */
    val rolePools: List<RolePoolConfig> = listOf(
        RolePoolConfig(
            role = SpecialistRole.MANAGER,
            nemotronPrimaryIds = listOf(
                "nvidia/nemotron-3-ultra-550b-a55b",
                "nvidia/nemotron-3-super-120b-a12b",
                "nvidia/llama-3.1-nemotron-ultra-253b-v1",
                "nvidia/llama-3.3-nemotron-super-49b-v1"
            ),
            backupAId = "mistralai/mistral-large-4-1t-a52b",
            backupBId = "moonshotai/kimi-k3.1-pro"
        ),
        RolePoolConfig(
            role = SpecialistRole.FAST_CHAT,
            nemotronPrimaryIds = listOf(
                "nvidia/nemotron-3.5-bolt-e4b",
                "nvidia/llama-3.1-nemotron-nano-8b-v1",
                "nvidia/llama-3.3-nemotron-super-49b-v1"
            ),
            backupAId = "google/gemma-4-31b-it",
            backupBId = "minimaxai/minimax-m3.1-flash"
        ),
        RolePoolConfig(
            role = SpecialistRole.REASONING_MATH,
            nemotronPrimaryIds = listOf(
                "nvidia/nemotron-3-super-120b-a12b",
                "nvidia/llama-3.1-nemotron-ultra-253b-v1",
                "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning"
            ),
            backupAId = "moonshotai/kimi-k3.1-pro",
            backupBId = "mistralai/mistral-large-4-1t-a52b"
        ),
        RolePoolConfig(
            role = SpecialistRole.CODING,
            nemotronPrimaryIds = listOf(
                "nvidia/nemotron-3.5-lightning-30b-a3b",
                "nvidia/llama-3.3-nemotron-super-49b-v1",
                "nvidia/llama-3.1-nemotron-70b-instruct"
            ),
            backupAId = "moonshotai/kimi-k3.1-pro",
            backupBId = "minimaxai/minimax-m3.1-flash"
        ),
        RolePoolConfig(
            role = SpecialistRole.PLANNING_TASKS,
            nemotronPrimaryIds = listOf(
                "nvidia/nemotron-3.5-lightning-30b-a3b",
                "nvidia/llama-3.3-nemotron-super-49b-v1",
                "nvidia/llama-3.1-nemotron-70b-instruct"
            ),
            backupAId = "mistralai/mistral-large-4-1t-a52b",
            backupBId = "moonshotai/kimi-k3.1-pro"
        ),
        RolePoolConfig(
            role = SpecialistRole.VISION_SCREEN,
            nemotronPrimaryIds = listOf(
                "nvidia/nemotron-3.5-super-vl-120b-a12b",
                "nvidia/vila",
                "nvidia/neva-22b"
            ),
            backupAId = "google/gemma-4-31b-it",
            backupBId = "minimaxai/minimax-m3.1-flash"
        ),
        RolePoolConfig(
            role = SpecialistRole.HINDI_CREATIVE,
            nemotronPrimaryIds = listOf(
                "nvidia/nemotron-3.5-bolt-e4b",
                "nvidia/llama-3.3-nemotron-super-49b-v1",
                "nvidia/llama-3.1-nemotron-70b-instruct"
            ),
            backupAId = "google/gemma-4-31b-it",
            backupBId = "mistralai/mistral-large-4-1t-a52b"
        ),
        RolePoolConfig(
            role = SpecialistRole.LONG_CONTEXT,
            nemotronPrimaryIds = listOf(
                "nvidia/nemotron-3-super-120b-a12b",
                "nvidia/nemotron-3-ultra-550b-a55b",
                "nvidia/llama-3.1-nemotron-ultra-253b-v1"
            ),
            backupAId = "moonshotai/kimi-k3.1-pro",
            backupBId = "mistralai/mistral-large-4-1t-a52b"
        )
    )

    // Curated Pure NVIDIA NIM Chat & Vision Models (Zero Gemini models)
    // Newer starter candidates are marked isCandidateSlot = true and verified live before routing priority.
    val allModels: List<AiModelSpec> = listOf(
        // --- Nemotron Primary Fleet (Manager + Specialists) ---
        AiModelSpec(
            id = "nvidia/nemotron-3-ultra-550b-a55b",
            displayName = "Nemotron 3 Ultra 550B",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.REASONING,
            parameterScale = "550B MoE",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 100,
            speedScore = 76,
            supportsThinking = true,
            isTrending = true,
            isNemotron = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF76B900,
            accentHexEnd = 0xFF059669,
            shortBadge = "N3U",
            roleInstruction = "Operate as Julie's Nemotron 3 Ultra Manager: decompose complex multi-step goals, verify specialist outputs, and deliver one unified answer."
        ),
        AiModelSpec(
            id = "nvidia/nemotron-3-super-120b-a12b",
            displayName = "Nemotron 3 Super 120B",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.REASONING,
            parameterScale = "120B MoE",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 99,
            speedScore = 88,
            supportsThinking = true,
            isTrending = true,
            isNemotron = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF65A30D,
            accentHexEnd = 0xFF10B981,
            shortBadge = "N3S",
            roleInstruction = "Operate as Nemotron 3 Super 120B: rigorous mathematical deduction, architecture validation, and crisp structured reasoning."
        ),
        AiModelSpec(
            id = "nvidia/nemotron-3.5-bolt-e4b",
            displayName = "Nemotron 3.5 Bolt E4B",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.SPEED,
            parameterScale = "E4B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 91,
            speedScore = 99,
            isTrending = true,
            isNemotron = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF4ADE80,
            accentHexEnd = 0xFF06B6D4,
            shortBadge = "N35B",
            roleInstruction = "Operate as Nemotron 3.5 Bolt: instant natural English, Hindi, and Hinglish replies for Julie voice and fast daily chat."
        ),
        AiModelSpec(
            id = "nvidia/nemotron-3.5-lightning-30b-a3b",
            displayName = "Nemotron 3.5 Lightning 30B",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.CODING,
            parameterScale = "30B MoE",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 96,
            speedScore = 96,
            isTrending = true,
            isNemotron = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF84CC16,
            accentHexEnd = 0xFF22D3EE,
            shortBadge = "N35L",
            roleInstruction = "Operate as Nemotron 3.5 Lightning: fast, production-grade Kotlin/Android coding, tool planning, and task decomposition."
        ),
        AiModelSpec(
            id = "nvidia/nemotron-3.5-super-vl-120b-a12b",
            displayName = "Nemotron 3.5 Super VL",
            vendor = "NVIDIA",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "120B VL",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 98,
            speedScore = 89,
            supportsVision = true,
            isTrending = true,
            isNemotron = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF76B900,
            accentHexEnd = 0xFF38BDF8,
            shortBadge = "N35V",
            roleInstruction = "Operate as Nemotron 3.5 Super VL: inspect live shared screens and screenshots accurately and guide the user step by step."
        ),
        AiModelSpec(
            id = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning",
            displayName = "Nemotron 3 Nano Omni 30B",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.REASONING,
            parameterScale = "30B MoE",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 94,
            speedScore = 95,
            supportsThinking = true,
            isTrending = true,
            isNemotron = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF22C55E,
            accentHexEnd = 0xFF0EA5E9,
            shortBadge = "N3NO",
            roleInstruction = "Operate as Nemotron 3 Nano Omni Reasoning: compact multi-step logic and fast verification."
        ),
        AiModelSpec(
            id = "nvidia/llama-3.1-nemotron-ultra-253b-v1",
            displayName = "Nemotron Ultra 253B",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.REASONING,
            parameterScale = "253B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 100,
            speedScore = 78,
            supportsThinking = true,
            isTrending = true,
            isNemotron = true,
            accentHexStart = 0xFF76B900,
            accentHexEnd = 0xFF10B981,
            shortBadge = "NU253",
            roleInstruction = "Operate as NVIDIA Nemotron Ultra 253B: deep chain-of-thought reasoning, scientific precision, and executive answers."
        ),
        AiModelSpec(
            id = "nvidia/llama-3.3-nemotron-super-49b-v1",
            displayName = "Nemotron Super 49B",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "49B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 96,
            speedScore = 92,
            supportsThinking = true,
            isTrending = true,
            isNemotron = true,
            accentHexStart = 0xFF65A30D,
            accentHexEnd = 0xFF059669,
            shortBadge = "NS49",
            roleInstruction = "Operate as NVIDIA Nemotron Super 49B: autonomous task planning, execution checklists, and high-speed reasoning."
        ),
        AiModelSpec(
            id = "nvidia/llama-3.1-nemotron-70b-instruct",
            displayName = "Nemotron 70B Instruct",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "70B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 94,
            speedScore = 86,
            isNemotron = true,
            accentHexStart = 0xFF84CC16,
            accentHexEnd = 0xFF14B8A6,
            shortBadge = "N70B",
            roleInstruction = "Operate as NVIDIA Nemotron 70B Instruct: high-helpfulness instruction following and clear execution."
        ),
        AiModelSpec(
            id = "nvidia/llama-3.1-nemotron-nano-8b-v1",
            displayName = "Nemotron Nano 8B",
            vendor = "NVIDIA",
            specialty = ModelSpecialty.SPEED,
            parameterScale = "8B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 86,
            speedScore = 99,
            supportsThinking = true,
            isNemotron = true,
            accentHexStart = 0xFF4ADE80,
            accentHexEnd = 0xFF06B6D4,
            shortBadge = "NN8B",
            roleInstruction = "Operate as NVIDIA Nemotron Nano 8B: instant lightweight response specialist."
        ),

        // --- Documented Optional Backup Starter Candidates (Verified Live Before Priority) ---
        AiModelSpec(
            id = "google/gemma-4-31b-it",
            displayName = "Gemma 4 31B IT",
            vendor = "Open Weights",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "31B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 95,
            speedScore = 93,
            supportsVision = true,
            isTrending = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF10B981,
            accentHexEnd = 0xFF3B82F6,
            shortBadge = "G4-31",
            roleInstruction = "Operate as Gemma 4 31B backup: clear multimodal vision, Hindi/Hinglish warmth, and concise summaries."
        ),
        AiModelSpec(
            id = "minimaxai/minimax-m3.1-flash",
            displayName = "MiniMax M3.1 Flash",
            vendor = "MiniMax",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "Flash MoE",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 93,
            speedScore = 98,
            supportsVision = true,
            isTrending = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF06B6D4,
            accentHexEnd = 0xFF3B82F6,
            shortBadge = "MM31",
            roleInstruction = "Operate as MiniMax M3.1 Flash backup: ultra-low-latency chat, coding, and visual assistance."
        ),
        AiModelSpec(
            id = "moonshotai/kimi-k3.1-pro",
            displayName = "Kimi K3.1 Pro",
            vendor = "Moonshot AI",
            specialty = ModelSpecialty.CODING,
            parameterScale = "Pro MoE",
            contextWindow = "256K",
            contextTokens = 256_000,
            powerScore = 98,
            speedScore = 88,
            supportsThinking = true,
            isTrending = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF6366F1,
            accentHexEnd = 0xFFEC4899,
            shortBadge = "K31P",
            roleInstruction = "Operate as Kimi K3.1 Pro backup: deep coding, long-context synthesis, and formal reasoning."
        ),
        AiModelSpec(
            id = "mistralai/mistral-large-4-1t-a52b",
            displayName = "Mistral Large 4 1T",
            vendor = "Mistral AI",
            specialty = ModelSpecialty.REASONING,
            parameterScale = "1T MoE",
            contextWindow = "256K",
            contextTokens = 256_000,
            powerScore = 99,
            speedScore = 82,
            isTrending = true,
            isCandidateSlot = true,
            accentHexStart = 0xFFF97316,
            accentHexEnd = 0xFFEF4444,
            shortBadge = "ML4",
            roleInstruction = "Operate as Mistral Large 4 backup: multilingual precision, executive synthesis, and complex workflow planning."
        ),
        AiModelSpec(
            id = "meta/muse-glimmer-30b",
            displayName = "Meta Muse Glimmer 30B",
            vendor = "Meta",
            specialty = ModelSpecialty.CREATIVE,
            parameterScale = "30B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 92,
            speedScore = 94,
            isTrending = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF0A84FF,
            accentHexEnd = 0xFF8B5CF6,
            shortBadge = "MG30",
            roleInstruction = "Operate as Meta Muse Glimmer 30B: articulate creative drafting and conversational clarity."
        ),
        AiModelSpec(
            id = "z-ai/glm-5.3-flash",
            displayName = "GLM 5.3 Flash",
            vendor = "Z.ai",
            specialty = ModelSpecialty.SPEED,
            parameterScale = "Flash",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 92,
            speedScore = 97,
            isTrending = true,
            isCandidateSlot = true,
            accentHexStart = 0xFF14B8A6,
            accentHexEnd = 0xFF3B82F6,
            shortBadge = "GLM5",
            roleInstruction = "Operate as GLM 5.3 Flash: rapid structured reasoning and coding utility."
        ),

        // --- Proven NVIDIA NIM Extended Fleet ---
        AiModelSpec(
            id = "deepseek-ai/deepseek-r1",
            displayName = "DeepSeek R1",
            vendor = "DeepSeek",
            specialty = ModelSpecialty.REASONING,
            parameterScale = "671B MoE",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 100,
            speedScore = 75,
            supportsThinking = true,
            isTrending = true,
            accentHexStart = 0xFF3B82F6,
            accentHexEnd = 0xFF8B5CF6,
            shortBadge = "DSR1",
            roleInstruction = "Operate as DeepSeek R1: first-principles mathematical, algorithmic, and step-by-step logical deduction."
        ),
        AiModelSpec(
            id = "deepseek-ai/deepseek-v3",
            displayName = "DeepSeek V3",
            vendor = "DeepSeek",
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "671B MoE",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 98,
            speedScore = 88,
            isTrending = true,
            accentHexStart = 0xFF2563EB,
            accentHexEnd = 0xFF06B6D4,
            shortBadge = "DSV3",
            roleInstruction = "Operate as DeepSeek V3: frontier coding, multilingual English/Hinglish fluency, and crisp synthesis."
        ),
        AiModelSpec(
            id = "meta/llama-4-maverick-17b-128e-instruct",
            displayName = "Llama 4 Maverick 128E",
            vendor = "Meta",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "400B MoE",
            contextWindow = "1M",
            contextTokens = 1_000_000,
            powerScore = 99,
            speedScore = 89,
            supportsVision = true,
            isTrending = true,
            accentHexStart = 0xFF0A84FF,
            accentHexEnd = 0xFF38BDF8,
            shortBadge = "L4MV",
            roleInstruction = "Operate as Meta Llama 4 Maverick: frontier multimodal vision, screen analysis, and ultra-long-context synthesis."
        ),
        AiModelSpec(
            id = "meta/llama-4-scout-17b-16e-instruct",
            displayName = "Llama 4 Scout 16E",
            vendor = "Meta",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "109B MoE",
            contextWindow = "10M",
            contextTokens = 1_000_000,
            powerScore = 95,
            speedScore = 95,
            supportsVision = true,
            isTrending = true,
            accentHexStart = 0xFF0284C7,
            accentHexEnd = 0xFF2DD4BF,
            shortBadge = "L4SC",
            roleInstruction = "Operate as Meta Llama 4 Scout: rapid multimodal screen inspection and massive context document analysis."
        ),
        AiModelSpec(
            id = "qwen/qwen3-235b-a22b",
            displayName = "Qwen 3 235B Think",
            vendor = "Alibaba Qwen",
            specialty = ModelSpecialty.REASONING,
            parameterScale = "235B MoE",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 99,
            speedScore = 84,
            supportsThinking = true,
            isTrending = true,
            accentHexStart = 0xFF7C3AED,
            accentHexEnd = 0xFFEC4899,
            shortBadge = "Q3-235",
            roleInstruction = "Operate as Qwen 3 235B: elite hybrid thinking, agentic tool orchestration, and multilingual mastery."
        ),
        AiModelSpec(
            id = "qwen/qwen2.5-coder-32b-instruct",
            displayName = "Qwen 2.5 Coder 32B",
            vendor = "Alibaba Qwen",
            specialty = ModelSpecialty.CODING,
            parameterScale = "32B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 97,
            speedScore = 91,
            isTrending = true,
            accentHexStart = 0xFF7C3AED,
            accentHexEnd = 0xFFA855F7,
            shortBadge = "QC32",
            roleInstruction = "Operate as Qwen 2.5 Coder 32B: production-grade Kotlin, Python, TypeScript, architecture, and bug fixing."
        ),
        AiModelSpec(
            id = "qwen/qwq-32b",
            displayName = "Qwen QwQ 32B Think",
            vendor = "Alibaba Qwen",
            specialty = ModelSpecialty.REASONING,
            parameterScale = "32B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 96,
            speedScore = 87,
            supportsThinking = true,
            accentHexStart = 0xFF6D28D9,
            accentHexEnd = 0xFFEC4899,
            shortBadge = "QwQ",
            roleInstruction = "Operate as QwQ 32B: systematic self-checking reasoning and complex constraint solving."
        ),
        AiModelSpec(
            id = "meta/llama-3.3-70b-instruct",
            displayName = "Llama 3.3 70B Instruct",
            vendor = "Meta",
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "70B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 96,
            speedScore = 90,
            isTrending = true,
            accentHexStart = 0xFF0284C7,
            accentHexEnd = 0xFF2563EB,
            shortBadge = "L3.3",
            roleInstruction = "Operate as Meta Llama 3.3 70B: balanced multilingual reasoning, synthesis, and reliable workflow execution."
        ),
        AiModelSpec(
            id = "google/gemma-3-27b-it",
            displayName = "Gemma 3 27B Open",
            vendor = "Open Weights",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "27B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 94,
            speedScore = 92,
            supportsVision = true,
            accentHexStart = 0xFF10B981,
            accentHexEnd = 0xFF3B82F6,
            shortBadge = "G3-27",
            roleInstruction = "Operate as Gemma 3 27B on NVIDIA NIM: clear creative writing, visual reasoning, and approachable explanations."
        ),
        AiModelSpec(
            id = "mistralai/mistral-small-24b-instruct",
            displayName = "Mistral Small 3 24B",
            vendor = "Mistral AI",
            specialty = ModelSpecialty.SPEED,
            parameterScale = "24B",
            contextWindow = "32K",
            contextTokens = 32_000,
            powerScore = 93,
            speedScore = 96,
            accentHexStart = 0xFFF97316,
            accentHexEnd = 0xFFF59E0B,
            shortBadge = "MS24",
            roleInstruction = "Operate as Mistral Small 3 24B: ultra-snappy enterprise answers, crisp formatting, and low latency."
        ),
        AiModelSpec(
            id = "microsoft/phi-4-mini-instruct",
            displayName = "Phi-4 Mini Instruct",
            vendor = "Microsoft",
            specialty = ModelSpecialty.MATH,
            parameterScale = "3.8B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 90,
            speedScore = 99,
            accentHexStart = 0xFF0EA5E9,
            accentHexEnd = 0xFF10B981,
            shortBadge = "PHI4",
            roleInstruction = "Operate as Microsoft Phi-4 Mini: razor-sharp compact logic, math proofs, and textbook precision."
        ),
        AiModelSpec(
            id = "deepseek-ai/deepseek-r1-distill-llama-70b",
            displayName = "DeepSeek R1 Llama 70B",
            vendor = "DeepSeek",
            specialty = ModelSpecialty.MATH,
            parameterScale = "70B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 94,
            speedScore = 88,
            supportsThinking = true,
            accentHexStart = 0xFF2563EB,
            accentHexEnd = 0xFF4F46E5,
            shortBadge = "R1-70",
            roleInstruction = "Operate as DeepSeek R1 Distill 70B: fast structured STEM verification and quantitative reasoning."
        ),
        AiModelSpec(
            id = "meta/llama-3.2-90b-vision-instruct",
            displayName = "Llama 3.2 90B Vision",
            vendor = "Meta",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "90B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 95,
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
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "11B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 88,
            speedScore = 95,
            supportsVision = true,
            accentHexStart = 0xFF0891B2,
            accentHexEnd = 0xFF22D3EE,
            shortBadge = "L11V",
            roleInstruction = "Operate as Llama 3.2 11B Vision: rapid screenshot OCR and interface context extraction."
        ),
        AiModelSpec(
            id = "qwen/qwen2.5-72b-instruct",
            displayName = "Qwen 2.5 72B Instruct",
            vendor = "Alibaba Qwen",
            specialty = ModelSpecialty.AGENTIC,
            parameterScale = "72B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 95,
            speedScore = 85,
            accentHexStart = 0xFF8B5CF6,
            accentHexEnd = 0xFF38BDF8,
            shortBadge = "Q72B",
            roleInstruction = "Operate as Qwen 2.5 72B: structured data analysis, multilingual Hinglish/English fluency, and long context."
        ),
        AiModelSpec(
            id = "mistralai/mistral-large-2-instruct",
            displayName = "Mistral Large 2",
            vendor = "Mistral AI",
            specialty = ModelSpecialty.CREATIVE,
            parameterScale = "123B",
            contextWindow = "128K",
            contextTokens = 128_000,
            powerScore = 96,
            speedScore = 82,
            accentHexStart = 0xFFF97316,
            accentHexEnd = 0xFFEF4444,
            shortBadge = "ML2",
            roleInstruction = "Operate as Mistral Large 2: articulate writing, executive synthesis, and polyglot clarity."
        ),
        AiModelSpec(
            id = "mistralai/codestral-22b-instruct-v0.1",
            displayName = "Codestral 22B",
            vendor = "Mistral AI",
            specialty = ModelSpecialty.CODING,
            parameterScale = "22B",
            contextWindow = "32K",
            contextTokens = 32_000,
            powerScore = 92,
            speedScore = 93,
            accentHexStart = 0xFFF59E0B,
            accentHexEnd = 0xFFFB7185,
            shortBadge = "CD22",
            roleInstruction = "Operate as Codestral 22B: blazing-fast code generation, refactoring, and unit test synthesis."
        ),
        AiModelSpec(
            id = "nvidia/vila",
            displayName = "NVIDIA VILA Multimodal",
            vendor = "NVIDIA",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "40B",
            contextWindow = "32K",
            contextTokens = 32_000,
            powerScore = 92,
            speedScore = 84,
            supportsVision = true,
            isNemotron = true,
            accentHexStart = 0xFF84CC16,
            accentHexEnd = 0xFF22C55E,
            shortBadge = "VILA",
            roleInstruction = "Operate as NVIDIA VILA: multi-frame screen context and live UI guidance."
        ),
        AiModelSpec(
            id = "nvidia/neva-22b",
            displayName = "NVIDIA NeVA 22B",
            vendor = "NVIDIA",
            modality = ModelModality.VISION,
            specialty = ModelSpecialty.VISION,
            parameterScale = "22B",
            contextWindow = "32K",
            contextTokens = 32_000,
            powerScore = 90,
            speedScore = 88,
            supportsVision = true,
            isNemotron = true,
            accentHexStart = 0xFF76B900,
            accentHexEnd = 0xFF06B6D4,
            shortBadge = "NEVA",
            roleInstruction = "Operate as NVIDIA NeVA 22B: visual scene comprehension and multimodal assistant."
        )
    )

    /**
     * Non-chat specialized models (Embeddings & Translation) are strictly separated from
     * chat/vision failover pools so a chat request can NEVER accidentally hit an embedding endpoint.
     */
    val nonChatModels: List<AiModelSpec> = listOf(
        AiModelSpec(
            id = "nvidia/nemotron-3.5-embed-1b",
            displayName = "Nemotron 3.5 Embed 1B",
            vendor = "NVIDIA",
            modality = ModelModality.EMBEDDING,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "1B Embed",
            contextWindow = "8K",
            contextTokens = 8_192,
            powerScore = 90,
            speedScore = 99,
            isNemotron = true,
            isCandidateSlot = true,
            endpointPath = "v1/embeddings",
            accentHexStart = 0xFF10B981,
            accentHexEnd = 0xFF0EA5E9,
            shortBadge = "EMB1",
            roleInstruction = "Embeddings-only model for Memory Vault / retrieval vectors (never routed to chat completions)."
        ),
        AiModelSpec(
            id = "nvidia/riva-translate-4b-instruct-v2",
            displayName = "Riva Translate 4B v2",
            vendor = "NVIDIA",
            modality = ModelModality.TRANSLATION,
            specialty = ModelSpecialty.SPEED,
            parameterScale = "4B",
            contextWindow = "16K",
            contextTokens = 16_384,
            powerScore = 90,
            speedScore = 98,
            isNemotron = true,
            isCandidateSlot = true,
            endpointPath = "v1/chat/completions",
            accentHexStart = 0xFF14B8A6,
            accentHexEnd = 0xFF6366F1,
            shortBadge = "RIVA",
            roleInstruction = "Dedicated translation model for Hindi/English/multilingual translation tasks."
        )
    )

    fun findById(id: String): AiModelSpec? =
        allModels.firstOrNull { it.id == id } ?: nonChatModels.firstOrNull { it.id == id }

    fun poolForRole(role: SpecialistRole): RolePoolConfig =
        rolePools.firstOrNull { it.role == role } ?: rolePools.first()

    /**
     * Classifies a user prompt into the best specialist role (never mixes embeddings/translation
     * into general chat, and isolates vision requests strictly to vision models).
     */
    fun classifyRole(
        prompt: String,
        hasImage: Boolean,
        isLongPrompt: Boolean = prompt.length >= 12_000
    ): SpecialistRole {
        if (hasImage) return SpecialistRole.VISION_SCREEN
        if (isLongPrompt) return SpecialistRole.LONG_CONTEXT

        val lower = prompt.lowercase()
        val hasDevanagari = prompt.any { it in '\u0900'..'\u097F' }

        return when {
            listOf(
                "code", "kotlin", "python", "java", "bug", "error", "stacktrace", "gradle",
                "compose", "api", "sql", "mongodb", "imagekit", "regex", "refactor", "function", "compile"
            ).any { lower.contains(it) } -> SpecialistRole.CODING

            listOf(
                "plan", "schedule", "roadmap", "task", "workflow", "24h", "checklist",
                "step by step", "steps", "architecture", "system design", "audit"
            ).any { lower.contains(it) } -> SpecialistRole.PLANNING_TASKS

            listOf(
                "math", "solve", "equation", "proof", "calculate", "physics", "theorem",
                "algorithm", "complexity", "compare", "tradeoff", "analyze", "why does"
            ).any { lower.contains(it) } -> SpecialistRole.REASONING_MATH

            hasDevanagari || listOf(
                "hindi", "hinglish", "story", "poem", "email", "letter", "caption", "script",
                "banao", "likho", "samjhao", "translate", "summary"
            ).any { lower.contains(it) } -> SpecialistRole.HINDI_CREATIVE

            else -> SpecialistRole.FAST_CHAT
        }
    }

    /**
     * Detects whether a prompt is genuinely complex enough to warrant the ONE big Manager +
     * specialist workers. Simple short chat bypasses the Manager for speed & zero bloat.
     */
    fun isComplexMultiStepPrompt(prompt: String, hasImage: Boolean): Boolean {
        if (hasImage) return false
        val trimmed = prompt.trim()
        if (trimmed.length >= 260) return true
        val lower = trimmed.lowercase()
        val complexitySignals = listOf(
            "architect", "compare", "multi-step", "complete plan", "end-to-end", "audit",
            "production", "full design", "step by step", "roadmap", "refactor", "benchmark",
            "strategy", "debug", "analyze thoroughly"
        )
        return complexitySignals.any { lower.contains(it) }
    }

    /**
     * Builds the ordered, deduplicated failover chain for a role:
     * 1. Optional pinned model (if compatible with vision requirement)
     * 2. Nemotron primary models for the role
     * 3. Strictly Backup A -> Backup B (only if backupsAllowed == true)
     * 4. Verified capability-compatible fallback pool (only if backupsAllowed == true)
     */
    fun buildFailoverChain(
        role: SpecialistRole,
        requireVision: Boolean,
        pinnedModelId: String = AUTO_SWARM_ID,
        backupsAllowed: Boolean = true,
        verifiedLiveIds: Set<String> = emptySet(),
        disabledIds: Set<String> = emptySet()
    ): List<AiModelSpec> {
        val pool = poolForRole(role)
        val raw = mutableListOf<AiModelSpec>()

        if (pinnedModelId != AUTO_SWARM_ID) {
            findById(pinnedModelId)?.let { pinned ->
                if (!requireVision || pinned.supportsVision) {
                    if (pinned.modality == ModelModality.CHAT || pinned.modality == ModelModality.VISION) {
                        raw.add(pinned)
                    }
                }
            }
        }

        // Prioritize verified Nemotron primaries first, then remaining Nemotron primaries
        val nemotronSpecs = pool.nemotronPrimaryIds.mapNotNull { findById(it) }
        val (verifiedNemotron, unverifiedNemotron) = nemotronSpecs.partition { it.id in verifiedLiveIds }
        raw.addAll(verifiedNemotron)
        raw.addAll(unverifiedNemotron)

        if (backupsAllowed) {
            findById(pool.backupAId)?.let { raw.add(it) }
            findById(pool.backupBId)?.let { raw.add(it) }

            // Capability-compatible reliable fallbacks on NVIDIA NIM
            val extraPool = if (requireVision) {
                allModels.filter { it.supportsVision }
            } else {
                listOfNotNull(
                    findById("meta/llama-3.3-70b-instruct"),
                    findById("deepseek-ai/deepseek-v3"),
                    findById("qwen/qwen2.5-coder-32b-instruct"),
                    findById("mistralai/mistral-large-2-instruct")
                )
            }
            val (verifiedExtras, unverifiedExtras) = extraPool.partition { it.id in verifiedLiveIds }
            raw.addAll(verifiedExtras)
            raw.addAll(unverifiedExtras)
        } else {
            // Nemotron-only mode: add remaining Nemotron models of matching modality
            val extraNemotron = allModels.filter {
                it.isNemotron && (!requireVision || it.supportsVision)
            }
            raw.addAll(extraNemotron)
        }

        return raw
            .distinctBy { it.id }
            .filter { it.id !in disabledIds }
            .filter { !requireVision || it.supportsVision }
            .filter { it.modality == ModelModality.CHAT || it.modality == ModelModality.VISION }
    }

    /**
     * Backward-compatible helper used by Task creation UI to preview recommended models.
     */
    fun selectBestSwarmForPrompt(
        prompt: String,
        hasImage: Boolean,
        @Suppress("UNUSED_PARAMETER") needsSearch: Boolean,
        pinnedModelId: String
    ): List<AiModelSpec> {
        val role = classifyRole(prompt, hasImage)
        return buildFailoverChain(
            role = role,
            requireVision = hasImage,
            pinnedModelId = pinnedModelId,
            backupsAllowed = true
        ).take(3)
    }
}
