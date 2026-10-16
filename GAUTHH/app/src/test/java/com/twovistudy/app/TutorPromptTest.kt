package com.twovistudy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorPromptTest {
    @Test
    fun recentHistoryKeepsTheNewestTwelveMessages() {
        val history = (1..15).map { number ->
            TutorMessage(Speaker.STUDENT, "question $number")
        }

        val recent = TutorPrompt.recentHistory(history)

        assertEquals(12, recent.size)
        assertEquals("question 4", recent.first().content)
        assertEquals("question 15", recent.last().content)
    }

    @Test
    fun systemMessageUsesTheSelectedSubjectAndTutorGuidance() {
        val prompt = TutorPrompt.systemMessage("Physics")

        assertTrue(prompt.contains("subject is Physics"))
        assertTrue(prompt.contains("show the reasoning"))
        assertTrue(prompt.contains("never invent facts"))
    }
}
