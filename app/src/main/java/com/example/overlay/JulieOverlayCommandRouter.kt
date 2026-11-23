package com.example.overlay

import java.util.Locale

/**
 * Only explicitly typed commands are translated into device actions.
 * AI-generated prose is NEVER executed as an Android gesture.
 * The AccessibilityService separately enforces lockscreen/password protections.
 */
sealed interface JulieOverlayCommand {
    data object InspectUi : JulieOverlayCommand
    data object Back : JulieOverlayCommand
    data object ScrollUp : JulieOverlayCommand
    data object ScrollDown : JulieOverlayCommand
    data object EmergencyStop : JulieOverlayCommand
    data class TapAt(val x: Float, val y: Float) : JulieOverlayCommand
    data class ClickLabel(val label: String) : JulieOverlayCommand
    data class TypeText(val text: String) : JulieOverlayCommand
    data class OpenApp(val name: String) : JulieOverlayCommand
}

object JulieOverlayCommandRouter {
    private val coordinates = Regex("^(?:tap|click)\\s+(\\d{1,4})\\s*[, ]\\s*(\\d{1,4})$", RegexOption.IGNORE_CASE)
    private val clickLabel = Regex("^(?:click|tap)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val typeText = Regex("^(?:type|enter|लिखो)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val openApp = Regex("^(?:open|launch|खोलो)\\s+(.+)$", RegexOption.IGNORE_CASE)

    fun parse(input: String): JulieOverlayCommand? {
        val raw = input.trim()
        if (raw.isBlank()) return null
        val lower = raw.lowercase(Locale.ROOT)
        when (lower) {
            "inspect", "inspect ui", "read ui", "screen read", "स्क्रीन पढ़ो" ->
                return JulieOverlayCommand.InspectUi
            "back", "go back", "वापस", "पीछे जाओ" ->
                return JulieOverlayCommand.Back
            "scroll down", "swipe up", "नीचे स्क्रॉल", "नीचे जाओ" ->
                return JulieOverlayCommand.ScrollDown
            "scroll up", "swipe down", "ऊपर स्क्रॉल", "ऊपर जाओ" ->
                return JulieOverlayCommand.ScrollUp
            "stop", "emergency stop", "रुको", "बंद करो" ->
                return JulieOverlayCommand.EmergencyStop
        }
        coordinates.matchEntire(raw)?.let { match ->
            val x = match.groupValues[1].toFloatOrNull() ?: return null
            val y = match.groupValues[2].toFloatOrNull() ?: return null
            return JulieOverlayCommand.TapAt(x, y)
        }
        clickLabel.matchEntire(raw)?.let {
            val label = it.groupValues[1].trim()
            if (label.isNotEmpty()) return JulieOverlayCommand.ClickLabel(label.take(120))
        }
        typeText.matchEntire(raw)?.let {
            val content = it.groupValues[1].trim()
            if (content.isNotEmpty()) return JulieOverlayCommand.TypeText(content.take(4000))
        }
        openApp.matchEntire(raw)?.let {
            val app = it.groupValues[1].trim()
            if (app.isNotEmpty()) return JulieOverlayCommand.OpenApp(app.take(70))
        }
        return null
    }
}
