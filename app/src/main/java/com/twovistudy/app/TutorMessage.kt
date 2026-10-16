package com.twovistudy.app

internal enum class Speaker(val apiRole: String) {
    STUDENT("user"),
    TUTOR("assistant"),
}

internal data class TutorMessage(
    val speaker: Speaker,
    val content: String,
)

internal object TutorPrompt {
    const val MAX_CONTEXT_MESSAGES = 12

    fun systemMessage(subject: String): String = """
        You are 2VI Study, a patient study tutor. The student's selected subject is $subject.
        Explain ideas in clear, age-appropriate language. For a problem, show the reasoning in
        short steps, explain why each step works, and finish with a quick check-for-understanding.
        Ask a clarifying question when essential information is missing. If you are unsure, say so;
        never invent facts, citations, or claim that an answer was independently verified. Keep
        formatting readable on a phone and avoid tables.
    """.trimIndent()

    fun recentHistory(history: List<TutorMessage>): List<TutorMessage> =
        history.takeLast(MAX_CONTEXT_MESSAGES)
}
