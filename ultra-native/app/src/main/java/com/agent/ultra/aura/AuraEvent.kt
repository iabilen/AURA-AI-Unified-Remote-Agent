package com.agent.ultra.aura

import org.json.JSONObject

/** A normalized event entering or leaving AURA. Payloads are intentionally JSON so transports stay replaceable. */
data class AuraEvent(
    val id: String,
    val type: String,
    val source: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val priority: Int = 0,
    val payload: JSONObject = JSONObject(),
)
