package com.agent.ultra.aura

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small durable outbound queue for device events. It survives normal process death and is bounded. */
class AuraEventQueue(context: Context) {
    companion object {
        private const val PREFS = "aura_event_queue"
        private const val KEY = "pending"
        private const val MAX_EVENTS = 100
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(event: AuraEvent) {
        val current = load()
        for (i in 0 until current.length()) {
            if (current.optJSONObject(i)?.optString("id") == event.id) return
        }
        current.put(event.toWireJson())
        while (current.length() > MAX_EVENTS) current.remove(0)
        save(current)
    }

    @Synchronized
    fun ack(id: String) {
        if (id.isBlank()) return
        val current = load()
        for (i in current.length() - 1 downTo 0) {
            if (current.optJSONObject(i)?.optString("id") == id) current.remove(i)
        }
        save(current)
    }

    @Synchronized
    fun pending(): List<JSONObject> {
        val current = load()
        return buildList {
            for (i in 0 until current.length()) {
                current.optJSONObject(i)?.let { add(JSONObject(it.toString())) }
            }
        }
    }

    private fun load(): JSONArray {
        val raw = prefs.getString(KEY, null).orEmpty()
        if (raw.isBlank()) return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun save(value: JSONArray) {
        prefs.edit().putString(KEY, value.toString()).apply()
    }

    private fun AuraEvent.toWireJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("id", id)
        .put("source", source)
        .put("timestampMs", timestampMs)
        .put("priority", priority)
        .put("payload", JSONObject(payload.toString()))
}
