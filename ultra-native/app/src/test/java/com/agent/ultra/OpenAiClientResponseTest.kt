package com.agent.ultra

import com.agent.ultra.provider.OpenAiClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiClientResponseTest {
    @Test
    fun extractsAllOutputTextParts() {
        val body = JSONObject("""{
          \"output\": [
            {\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"hello\"}]},
            {\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"world\"}]}
          ]
        }""")
        assertEquals("hello\nworld", OpenAiClient.extractOutputText(body))
    }

    @Test
    fun emptyOutputIsEmptyAndCannotBecomeAnAction() {
        assertEquals("", OpenAiClient.extractOutputText(JSONObject("{\"output\":[]}")))
    }

    @Test
    fun ignoresNonTextOutputItems() {
        val body = JSONObject("""{
          \"output\": [{\"type\":\"reasoning\",\"content\":[]}],
          \"id\":\"resp_test\"
        }""")
        assertTrue(OpenAiClient.extractOutputText(body).isBlank())
    }
}
