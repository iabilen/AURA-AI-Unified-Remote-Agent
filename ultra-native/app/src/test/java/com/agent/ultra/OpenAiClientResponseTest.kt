package com.agent.ultra

import com.agent.ultra.provider.OpenAiClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiClientResponseTest {
    @Test
    fun extractsAllOutputTextParts() {
        val first = JSONObject().put("type", "message").put(
            "content", JSONArray().put(
                JSONObject().put("type", "output_text").put("text", "hello")
            )
        )
        val second = JSONObject().put("type", "message").put(
            "content", JSONArray().put(
                JSONObject().put("type", "output_text").put("text", "world")
            )
        )
        val body = JSONObject().put("output", JSONArray().put(first).put(second))
        assertEquals("hello\nworld", OpenAiClient.extractOutputText(body))
    }

    @Test
    fun emptyOutputIsEmptyAndCannotBecomeAnAction() {
        val body = JSONObject().put("output", JSONArray())
        assertEquals("", OpenAiClient.extractOutputText(body))
    }

    @Test
    fun ignoresNonTextOutputItems() {
        val reasoning = JSONObject().put("type", "reasoning").put("content", JSONArray())
        val body = JSONObject().put("output", JSONArray().put(reasoning))
        assertTrue(OpenAiClient.extractOutputText(body).isBlank())
    }
}
