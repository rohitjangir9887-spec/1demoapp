package com.example

import com.example.overlay.JulieOverlayCommand
import com.example.overlay.JulieOverlayCommandRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JulieOverlayCommandRouterTest {
    @Test
    fun explicitUserCommandsAreParsedWithoutExecutingAnything() {
        assertEquals(JulieOverlayCommand.ScrollDown, JulieOverlayCommandRouter.parse("scroll down"))
        assertEquals(JulieOverlayCommand.ScrollUp, JulieOverlayCommandRouter.parse("ऊपर स्क्रॉल"))
        assertEquals(JulieOverlayCommand.Back, JulieOverlayCommandRouter.parse("पीछे जाओ"))
        assertEquals(JulieOverlayCommand.EmergencyStop, JulieOverlayCommandRouter.parse("STOP"))
        assertEquals(JulieOverlayCommand.InspectUi, JulieOverlayCommandRouter.parse("Read UI"))
        assertEquals(JulieOverlayCommand.TapAt(220f, 450f), JulieOverlayCommandRouter.parse("tap 220,450"))
        assertEquals(JulieOverlayCommand.ClickLabel("YouTube"), JulieOverlayCommandRouter.parse("click YouTube"))
        assertEquals(JulieOverlayCommand.TypeText("hello"), JulieOverlayCommandRouter.parse("type hello"))
        assertEquals(JulieOverlayCommand.OpenApp("YouTube"), JulieOverlayCommandRouter.parse("open YouTube"))
    }

    @Test
    fun ordinaryAiQuestionsAreNotDeviceActions() {
        assertNull(JulieOverlayCommandRouter.parse("What is the weather today?"))
        assertNull(JulieOverlayCommandRouter.parse("Please explain how tapping a phone works"))
        assertNull(JulieOverlayCommandRouter.parse("Hey Julie tell me a story"))
        assertTrue(JulieOverlayCommandRouter.parse("type ") == null)
    }
}
