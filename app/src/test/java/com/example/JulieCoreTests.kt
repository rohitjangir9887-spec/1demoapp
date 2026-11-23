package com.example

import com.example.data.ai.LongPromptProcessor
import com.example.data.model.AiModelsCatalog
import com.example.data.model.ModelModality
import com.example.data.model.SpecialistRole
import com.example.data.remote.NimErrorCategory
import com.example.data.remote.NimErrorClassifier
import com.example.data.security.CryptoVault
import com.example.tools.JulieToolType
import com.example.voice.VoiceCommandResult
import com.example.voice.VoiceInputController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JulieCoreTests {

    @Test
    fun catalogHasZeroGeminiModelsAndIsolatesNonChatModels() {
        val allIds = AiModelsCatalog.allModels.map { it.id.lowercase() }
        assertTrue(
            "No Gemini model IDs should remain in chat catalog",
            allIds.none { it.contains("gemini") }
        )
        assertTrue(AiModelsCatalog.allModels.size >= 33)

        val nonChatIds = AiModelsCatalog.nonChatModels.map { it.id }.toSet()
        assertTrue(nonChatIds.contains("nvidia/nemotron-3.5-embed-1b"))
        assertTrue(nonChatIds.contains("nvidia/riva-translate-4b-instruct-v2"))

        // Every chat/vision failover chain must exclude non-chat embedding/translation models
        SpecialistRole.entries.forEach { role ->
            val chain = AiModelsCatalog.buildFailoverChain(
                role = role,
                requireVision = (role == SpecialistRole.VISION_SCREEN),
                backupsAllowed = true
            )
            assertTrue(chain.isNotEmpty())
            assertTrue(
                "Failover chain for $role must not contain non-chat models",
                chain.none { it.modality == ModelModality.EMBEDDING || it.modality == ModelModality.TRANSLATION }
            )
        }
    }

    @Test
    fun nemotronFirstAndVisionNeverFallsBackToTextOnly() {
        val fastChain = AiModelsCatalog.buildFailoverChain(
            role = SpecialistRole.FAST_CHAT,
            requireVision = false,
            backupsAllowed = true
        )
        assertTrue("First candidate in Fast Chat must be Nemotron", fastChain.first().isNemotron)

        val nemotronOnlyChain = AiModelsCatalog.buildFailoverChain(
            role = SpecialistRole.CODING,
            requireVision = false,
            backupsAllowed = false
        )
        assertTrue("Nemotron-only policy must return only Nemotron models", nemotronOnlyChain.all { it.isNemotron })

        val visionChain = AiModelsCatalog.buildFailoverChain(
            role = SpecialistRole.VISION_SCREEN,
            requireVision = true,
            backupsAllowed = true
        )
        assertTrue("Vision failover chain must strictly contain vision-capable models", visionChain.all { it.supportsVision })
    }

    @Test
    fun simpleChatBypassesManagerWhileComplexPromptsEngageManager() {
        assertFalse(AiModelsCatalog.isComplexMultiStepPrompt("Hi Julie, how are you?", hasImage = false))
        assertTrue(
            AiModelsCatalog.isComplexMultiStepPrompt(
                "Please architect an end-to-end production system design and roadmap with step by step tradeoffs.",
                hasImage = false
            )
        )
    }

    @Test
    fun httpErrorTaxonomyStopsOn401AndFailsOverOn404And429WithRetryAfter() {
        val err401 = NimErrorClassifier.classifyHttpCode(401, "Invalid token nvapi-secret123456")
        assertEquals(NimErrorCategory.INVALID_KEY_401, err401.category)
        assertTrue(err401.category.shouldAbortAllModels)
        assertFalse(err401.category.shouldFailover)
        assertFalse(err401.safeMessage.contains("secret123456"))

        val err404 = NimErrorClassifier.classifyHttpCode(404, "model not found")
        assertEquals(NimErrorCategory.MODEL_NOT_FOUND_404, err404.category)
        assertFalse(err404.category.shouldAbortAllModels)
        assertTrue(err404.category.shouldFailover)

        val err429 = NimErrorClassifier.classifyHttpCode(429, "rate limit", retryAfterHeader = "17")
        assertEquals(NimErrorCategory.RATE_LIMITED_429, err429.category)
        assertEquals(17L, err429.retryAfterSeconds)

        assertEquals("nvapi-••••9876", NimErrorClassifier.maskKeyFingerprint("Bearer nvapi-abcdef1234569876"))
    }

    @Test
    fun longPrompt50kIsSplitIntoBoundedChunksWithoutLoss() {
        val paragraph = "Requirement section: validate Nemotron routing, live screen frames, and security controls.\n\n"
        val bigPrompt = paragraph.repeat(600) // ~57,000 chars
        assertTrue(bigPrompt.length > 50_000)
        assertTrue(LongPromptProcessor.needsChunking(bigPrompt))

        val chunks = LongPromptProcessor.splitIntoBoundedChunks(bigPrompt, maxChunkChars = 12_000, overlapChars = 300)
        assertTrue("57K prompt must produce multiple bounded chunks", chunks.size >= 4)
        assertTrue("Every chunk must remain within max bound", chunks.all { it.length <= 12_000 })
        assertTrue("First chunk keeps start of prompt", chunks.first().startsWith("Requirement section:"))
        assertTrue("Last chunk keeps end of prompt", chunks.last().endsWith("security controls."))
    }

    @Test
    fun julieVoiceCommandsParseHindiHinglishAndEnglishAccurately() {
        val yt = VoiceInputController.parseVoiceCommand("हाय जुली YouTube खोलो")
        assertTrue(yt is VoiceCommandResult.ToolIntent)
        assertEquals(JulieToolType.LAUNCH_APP, (yt as VoiceCommandResult.ToolIntent).action.type)

        val search = VoiceInputController.parseVoiceCommand("Hey Julie search Google for Kotlin Coroutines")
        assertTrue(search is VoiceCommandResult.ToolIntent)
        assertEquals(JulieToolType.SEARCH_WEB, (search as VoiceCommandResult.ToolIntent).action.type)
        assertEquals("Kotlin Coroutines", search.action.argument)

        val dial = VoiceInputController.parseVoiceCommand("जुली कॉल लगाओ 9876543210")
        assertTrue(dial is VoiceCommandResult.ToolIntent)
        assertEquals(JulieToolType.DIAL_PHONE, (dial as VoiceCommandResult.ToolIntent).action.type)
        assertTrue(dial.action.type.requiresConfirmation)

        val alarm = VoiceInputController.parseVoiceCommand("Hey Julie set alarm 6:45")
        assertTrue(alarm is VoiceCommandResult.ToolIntent)
        assertEquals(6, (alarm as VoiceCommandResult.ToolIntent).action.extraHour)
        assertEquals(45, alarm.action.extraMinute)

        val task = VoiceInputController.parseVoiceCommand("Hey Julie task banao daily coding revision")
        assertTrue(task is VoiceCommandResult.TaskInitiation)
        assertEquals("daily coding revision", (task as VoiceCommandResult.TaskInitiation).prompt)
    }

    @Test
    fun cryptoVaultBlocksInsecureOrPrivateUrlsAndAllowsPublicHttps() {
        assertTrue(CryptoVault.validatePublicHttpsUrl("https://ik.imagekit.io/julie_demo").isSuccess)
        assertTrue(CryptoVault.validatePublicHttpsUrl("http://ik.imagekit.io/julie_demo").isFailure)
        assertTrue(CryptoVault.validatePublicHttpsUrl("https://localhost/api").isFailure)
        assertTrue(CryptoVault.validatePublicHttpsUrl("https://127.0.0.1/api").isFailure)
        assertTrue(CryptoVault.validatePublicHttpsUrl("https://192.168.1.50/mongo").isFailure)
        assertTrue(CryptoVault.validatePublicHttpsUrl("https://10.0.0.5/mongo").isFailure)
    }

    @Test
    fun accessibilitySafetyNeverBypassesLockscreenOrReadsPasswords() {
        assertTrue(
            "Gestures must be blocked while lockscreen is active",
            com.example.accessibility.JulieAccessibilitySafety.evaluateGestureSafety(
                isKeyguardLocked = true,
                isEmergencyStopped = false,
                targetPackage = "com.google.android.youtube"
            ).isFailure
        )
        assertTrue(
            "Gestures must be blocked when Emergency STOP is active",
            com.example.accessibility.JulieAccessibilitySafety.evaluateGestureSafety(
                isKeyguardLocked = false,
                isEmergencyStopped = true,
                targetPackage = "com.google.android.youtube"
            ).isFailure
        )
        assertTrue(
            "Gestures are allowed on normal unlocked apps",
            com.example.accessibility.JulieAccessibilitySafety.evaluateGestureSafety(
                isKeyguardLocked = false,
                isEmergencyStopped = false,
                targetPackage = "com.google.android.youtube"
            ).isSuccess
        )
        assertEquals(
            "Password fields must never be exposed",
            null,
            com.example.accessibility.JulieAccessibilitySafety.sanitizeNodeText("mySuperSecret123", "Password", isPassword = true)
        )
        assertEquals(
            "API key patterns must be redacted",
            "[REDACTED]",
            com.example.accessibility.JulieAccessibilitySafety.sanitizeNodeText("nvapi-secretkey1234", null, isPassword = false)
        )
        assertEquals(
            "Normal labels pass through cleanly",
            "Search YouTube",
            com.example.accessibility.JulieAccessibilitySafety.sanitizeNodeText("Search YouTube", null, isPassword = false)
        )
    }
}
