package com.twovistudy.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

private val Paper = Color(0xFFF7F6F0)
private val CardWhite = Color(0xFFFFFEFB)
private val Ink = Color(0xFF1C3028)
private val Muted = Color(0xFF68766F)
private val Forest = Color(0xFF246B53)
private val Mint = Color(0xFFE1F1E8)
private val Line = Color(0xFFE7E8DF)
private val Amber = Color(0xFFFFF0D2)
private val Subjects = listOf("Math", "Physics", "Chemistry", "Biology", "Other")
private const val MAX_QUESTION_CHARACTERS = 4_000
private const val MAX_VISIBLE_MESSAGES = 40

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Forest,
                    onPrimary = Color.White,
                    background = Paper,
                    surface = CardWhite,
                    onSurface = Ink,
                    secondary = Color(0xFFE3B86B),
                ),
            ) {
                TwoViStudyApp()
            }
        }
    }
}

@Composable
private fun TwoViStudyApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<TutorMessage>() }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("Math") }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var consentAccepted by remember { mutableStateOf(false) }
    var showPrivacyNotice by remember { mutableStateOf(true) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var speakNextAnswer by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    val textToSpeech = remember(context) {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
    DisposableEffect(textToSpeech) {
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    DisposableEffect(speechRecognizer) {
        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
    }

    val submit: (String, Boolean) -> Unit = { question, speakAnswer ->
        val cleanQuestion = question.trim()
        if (cleanQuestion.length > MAX_QUESTION_CHARACTERS) {
            errorMessage = "Questions must be 4,000 characters or less."
        } else if (cleanQuestion.isNotEmpty() && !isLoading) {
            messages.add(TutorMessage(Speaker.STUDENT, cleanQuestion))
            if (messages.size > MAX_VISIBLE_MESSAGES) messages.removeAt(0)
            draft = ""
            errorMessage = null
            voiceMessage = null
            isLoading = true
            val requestSubject = selectedSubject
            val requestHistory = messages.toList()
            scope.launch {
                try {
                    val answer = withContext(Dispatchers.IO) {
                        TutorApi.answer(requestSubject, requestHistory)
                    }
                    messages.add(TutorMessage(Speaker.TUTOR, answer))
                    if (messages.size > MAX_VISIBLE_MESSAGES) messages.removeAt(0)
                    if (speakAnswer && ttsReady) {
                        textToSpeech.speak(
                            answer,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "2vi-tutor-answer",
                        )
                    } else if (speakAnswer) {
                        voiceMessage = "Speech output is unavailable on this device."
                    }
                } catch (failure: IOException) {
                    errorMessage = failure.message ?: "Could not reach the free AI service."
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val sendQuestion: (String, Boolean) -> Unit = { question, speakAnswer ->
        if (!consentAccepted) {
            pendingQuestion = question
            speakNextAnswer = speakAnswer
            showPrivacyNotice = true
        } else {
            submit(question, speakAnswer)
        }
    }

    val startListening: () -> Unit = {
        if (speechRecognizer == null) {
            voiceMessage = "Speech recognition is not available on this device."
        } else {
            isListening = true
            voiceMessage = "Listening… say one question."
            val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speechRecognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        voiceMessage = "Listening… say one question."
                    }

                    override fun onBeginningOfSpeech() {
                        voiceMessage = "Listening…"
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        voiceMessage = "Thinking…"
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        voiceMessage = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "I didn’t catch that. Tap Talk and try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission is needed for voice questions."
                            else -> "Speech recognition stopped. Tap Talk to try again."
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val recognized = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                        if (recognized.isNullOrBlank()) {
                            voiceMessage = "I didn’t catch that. Tap Talk and try again."
                        } else {
                            draft = recognized
                            sendQuestion(recognized, true)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )
            speechRecognizer.startListening(recognitionIntent)
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            voiceMessage = "Microphone permission is needed for voice questions."
        }
    }

    val startVoiceTutor = {
        if (isLoading || isListening) {
            speechRecognizer?.cancel()
            isListening = false
            voiceMessage = null
        } else if (!consentAccepted) {
            pendingVoiceStart = true
            showPrivacyNotice = true
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        val lastItem = messages.lastIndex + if (isLoading) 1 else 0
        if (lastItem >= 0) listState.animateScrollToItem(lastItem)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader(
            showNewChat = messages.isNotEmpty() && !isLoading,
            onNewChat = {
                textToSpeech.stop()
                speechRecognizer?.cancel()
                isListening = false
                messages.clear()
                draft = ""
                errorMessage = null
                voiceMessage = null
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Amber)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("✦", color = Forest, fontSize = 19.sp)
            Text(
                "Free AI • no account • conversations stay on this screen only",
                color = Ink,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    WelcomeCard(
                        onPrompt = { prompt -> sendQuestion(prompt, false) },
                    )
                }
            }
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    onSpeak = { text ->
                        if (ttsReady) {
                            textToSpeech.speak(
                                text,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "2vi-tutor-answer",
                            )
                        } else {
                            voiceMessage = "Speech output is unavailable on this device."
                        }
                    },
                )
            }
            if (isLoading) {
                item {
                    TutorThinking()
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                "CHOOSE A SUBJECT",
                color = Muted,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Subjects.forEach { subject ->
                    FilterChip(
                        selected = selectedSubject == subject,
                        onClick = { selectedSubject = subject },
                        label = { Text(subject) },
                    )
                }
            }
        }

        if (voiceMessage != null) {
            Text(
                voiceMessage.orEmpty(),
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (errorMessage != null) {
            Text(
                errorMessage.orEmpty(),
                color = Color(0xFF9D3B31),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    if (it.length <= MAX_QUESTION_CHARACTERS) {
                        draft = it
                        errorMessage = null
                    } else {
                        errorMessage = "Questions must be 4,000 characters or less."
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask a study question…") },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
                enabled = !isLoading && !isListening,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { sendQuestion(draft, false) },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Forest,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite,
                ),
            )
            TextButton(
                onClick = startVoiceTutor,
                enabled = !isLoading || isListening,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isListening) Mint else CardWhite),
            ) {
                Text(if (isListening) "Stop" else "Talk")
            }
            Button(
                onClick = { sendQuestion(draft, false) },
                enabled = draft.isNotBlank() && !isLoading && !isListening,
                shape = RoundedCornerShape(16.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(if (isLoading) "…" else "Ask")
            }
        }

        Text(
            "AI questions go to Pollinations. Free access can be busy or limited. Voice uses your Android speech service.",
            modifier = Modifier.fillMaxWidth(),
            color = Muted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }

    if (showPrivacyNotice) {
        AlertDialog(
            onDismissRequest = { showPrivacyNotice = false },
            title = {
                Text(
                    "A quick note before you study",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    "2VI Study has no account, ads, or paid features, and does not save chat history. " +
                        "When you ask a question, its text and recent in-session messages are sent " +
                        "to Pollinations for an AI answer. Its free anonymous service is rate-limited " +
                        "(currently one text request per IP about every 30 seconds), may be unavailable, " +
                        "and has no uptime guarantee. Voice input uses Android's selected speech " +
                        "recognition and speech services; those services may process audio or text " +
                        "online. Avoid sharing " +
                        "personal or sensitive information.",
                    color = Ink,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        consentAccepted = true
                        showPrivacyNotice = false
                        val queuedQuestion = pendingQuestion
                        pendingQuestion = null
                        if (!queuedQuestion.isNullOrBlank()) {
                            submit(queuedQuestion, speakNextAnswer)
                        } else if (pendingVoiceStart) {
                            pendingVoiceStart = false
                            if (
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startListening()
                            } else {
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrivacyNotice = false
                        pendingQuestion = null
                        pendingVoiceStart = false
                    },
                ) {
                    Text("Not now")
                }
            },
            containerColor = CardWhite,
        )
    }
}

@Composable
private fun BrandHeader(
    showNewChat: Boolean,
    onNewChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Forest),
                contentAlignment = Alignment.Center,
            ) {
                Text("2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
            }
            Column {
                Text(
                    "2VI STUDY",
                    color = Ink,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "A little help. A lot of learning.",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (showNewChat) {
            TextButton(onClick = onNewChat) {
                Text("New chat", color = Forest, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Surface(
                shape = RoundedCornerShape(50),
                color = Mint,
            ) {
                Text(
                    "TUTOR",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Forest,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun WelcomeCard(onPrompt: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text("YOUR STUDY SPACE", color = Forest, letterSpacing = 1.2.sp,
                style = MaterialTheme.typography.labelSmall)
            Text(
                "What are we learning today?",
                color = Ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Ask a question or tap Talk. Your tutor will explain the steps, not just the answer.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(1.dp))
            SuggestionCard(
                title = "Math",
                prompt = "Can you explain how to solve a two-step equation, with an example?",
                onClick = onPrompt,
            )
            SuggestionCard(
                title = "Science",
                prompt = "Explain photosynthesis step by step in simple terms.",
                onClick = onPrompt,
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    title: String,
    prompt: String,
    onClick: (String) -> Unit,
) {
    TextButton(
        onClick = { onClick(prompt) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Paper)
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(title, color = Forest, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium)
                Text(prompt, color = Ink, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = Forest, fontSize = 24.sp)
        }
    }
}

@Composable
private fun MessageBubble(
    message: TutorMessage,
    onSpeak: (String) -> Unit,
) {
    val isStudent = message.speaker == Speaker.STUDENT
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isStudent) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 19.dp,
                        topEnd = 19.dp,
                        bottomEnd = if (isStudent) 5.dp else 19.dp,
                        bottomStart = if (isStudent) 19.dp else 5.dp,
                    ),
                )
                .background(if (isStudent) Forest else CardWhite)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (isStudent) "YOU" else "2VI TUTOR",
                color = if (isStudent) Color(0xFFC8E8D6) else Forest,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                message.content,
                color = if (isStudent) Color.White else Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!isStudent) {
                TextButton(
                    onClick = { onSpeak(message.content) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Listen", color = Forest, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TutorThinking() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("2VI", color = Forest, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Thinking through it…", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
