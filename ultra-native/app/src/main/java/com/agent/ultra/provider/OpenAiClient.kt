package com.agent.ultra.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal direct OpenAI Responses API client used by the Brain.
 *
 * The Brain owns conversation history and sends a bounded message list on each
 * turn. We deliberately do not use server-side conversations here: AURA's
 * runtime memory remains device-local and OpenAI only receives the context
 * needed for the current turn.
 */
class OpenAiClient(private val config: ProviderConfig) {

    val modelName: String get() = config.model

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ChatMessage(val role: String, val content: String)

    suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
        @Suppress("UNUSED_PARAMETER") temperature: Double,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val input = JSONArray()
            val instructions = StringBuilder()
            for (m in messages) {
                if (m.role == "system") {
                    if (instructions.isNotEmpty()) instructions.append("\n\n")
                    instructions.append(m.content)
                } else {
                    input.put(
                        JSONObject()
                            .put("role", if (m.role == "assistant") "assistant" else "user")
                            .put("content", m.content)
                    )
                }
            }

            val bodyJson = JSONObject()
                .put("model", config.model)
                .put("input", input)
                .put("max_output_tokens", maxTokens)
                .put("store", false)
            if (instructions.isNotEmpty()) bodyJson.put("instructions", instructions.toString())

            val req = Request.Builder()
                .url(config.baseUrl.trimEnd('/') + "/responses")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("OpenAI HTTP ${resp.code}")
                    )
                }
                val body = resp.body?.string().orEmpty()
                val text = extractOutputText(JSONObject(body))
                if (text.isBlank()) Result.failure(Exception("OpenAI returned no text"))
                else Result.success(text)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("OpenAI request failed: ${e.message ?: e::class.simpleName}"))
        }
    }

    companion object {
        /** Extracts assistant text from a Responses API JSON object. */
        internal fun extractOutputText(body: JSONObject): String {
            val output = body.optJSONArray("output") ?: return ""
            val text = StringBuilder()
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    if (part.optString("type") == "output_text") {
                        val value = part.optString("text")
                        if (value.isNotEmpty()) {
                            if (text.isNotEmpty()) text.append('\n')
                            text.append(value)
                        }
                    }
                }
            }
            return text.toString()
        }
    }
}
