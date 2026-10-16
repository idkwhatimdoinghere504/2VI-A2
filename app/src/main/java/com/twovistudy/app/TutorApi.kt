package com.twovistudy.app

import java.io.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object TutorApi {
    private const val ENDPOINT = "https://text.pollinations.ai/openai"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 90_000
    private const val MAX_RESPONSE_BYTES = 64 * 1024
    private const val MAX_REQUEST_BYTES = 1024 * 1024

    @Throws(IOException::class)
    fun answer(subject: String, history: List<TutorMessage>): String {
        val request = createRequest(subject, history).toByteArray(Charsets.UTF_8)
        if (request.size > MAX_REQUEST_BYTES) {
            throw IOException("This chat context is too large to send. Start a new chat and try again.")
        }
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("User-Agent", "2VI-Study/1.0 Android")

        try {
            connection.outputStream.use { output ->
                output.write(request)
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val retryAfter = connection.getHeaderField("Retry-After")
                throw IOException(
                    when (status) {
                        402, 429 -> buildString {
                            append("The free AI service is rate-limited right now.")
                            if (!retryAfter.isNullOrBlank()) {
                                append(" Retry-After: ")
                                append(retryAfter)
                                append(".")
                            } else {
                                append(" Please wait and try again.")
                            }
                        }
                        else -> "The AI service returned HTTP $status. Please try again later."
                    },
                )
            }

            val responseBody = readResponse(connection.inputStream)
            return parseAnswer(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        input.use { stream ->
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw IOException("The AI service response was too large to display.")
                }
                output.write(buffer, 0, count)
            }
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }

    internal fun createRequest(subject: String, history: List<TutorMessage>): String {
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", TutorPrompt.systemMessage(subject)),
            )

        for (message in TutorPrompt.recentHistory(history)) {
            messages.put(
                JSONObject()
                    .put("role", message.speaker.apiRole)
                    .put("content", message.content),
            )
        }

        return JSONObject()
            .put("model", "openai")
            .put("messages", messages)
            .toString()
    }

    private fun parseAnswer(responseBody: String): String {
        val response = try {
            JSONObject(responseBody)
        } catch (cause: JSONException) {
            throw IOException("The AI service returned an unreadable response.", cause)
        }
        val content = response
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()

        if (content.isBlank()) {
            throw IOException("The AI service returned an empty answer. Please try again.")
        }
        return content
    }
}
