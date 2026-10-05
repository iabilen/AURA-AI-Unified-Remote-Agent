package com.agent.ultra.aura

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small durable outbound queue for device events.
 *
 * The queue is bounded, ACK-driven, priority-aware, and coalesces only noisy
 * state events. An event remains durable until the relay explicitly ACKs its id.
 */
class AuraEventQueue(context: Context) {
    companion object {
        private const val PREFS = "aura_event_queue"
        private const val KEY = "pending"
        private const val MAX_EVENTS = 100
        private const val IN_FLIGHT = "inFlight"
        private val COALESCE_TYPES = setOf("battery", "power", "screen", "connectivity")
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(event: AuraEvent) {
        val current = load()
        for (i in 0 until current.length()) {
            val existing = current.optJSONObject(i) ?: continue
            if (existing.optString("id") == event.id) return
            if (!existing.optBoolean(IN_FLIGHT, false) && sameCoalesceKey(existing, event)) {
                current.put(i, event.toWireJson())
                save(current)
                return
            }
        }

        current.put(event.toWireJson())
        trimToBound(current)
        save(current)
    }

    @Synchronized
    fun markInFlight(id: String) {
        if (id.isBlank()) return
        val current = load()
        for (i in 0 until current.length()) {
            val item = current.optJSONObject(i) ?: continue
            if (item.optString("id") == id) {
                item.put(IN_FLIGHT, true)
                current.put(i, item)
                save(current)
                return
            }
        }
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
        }.sortedWith(compareByDescending<JSONObject> { it.optInt("priority", 0) }
            .thenBy { it.optLong("timestampMs", Long.MAX_VALUE) })
    }

    private fun sameCoalesceKey(existing: JSONObject, event: AuraEvent): Boolean {
        if (event.type !in COALESCE_TYPES) return false
        if (existing.optString("type") != event.type) return false
        if (existing.optString("source") != event.source) return false
        return when (event.type) {
            "battery" -> true
            "power" -> true
            "screen" -> true
            "connectivity" -> existing.optJSONObject("payload")?.optString("transport") ==
                event.payload.optString("transport")
            else -> false
        }
    }

    private fun trimToBound(current: JSONArray) {
        while (current.length() > MAX_EVENTS) {
            var removeIndex = 0
            var lowestPriority = Int.MAX_VALUE
            var oldestTimestamp = Long.MAX_VALUE
            for (i in 0 until current.length()) {
                val item = current.optJSONObject(i) ?: continue
                if (item.optBoolean(IN_FLIGHT, false)) continue
                val priority = item.optInt("priority", 0)
                val timestamp = item.optLong("timestampMs", Long.MAX_VALUE)
                if (priority < lowestPriority ||
                    (priority == lowestPriority && timestamp < oldestTimestamp)
                ) {
                    lowestPriority = priority
                    oldestTimestamp = timestamp
                    removeIndex = i
                }
            }
            current.remove(removeIndex)
        }
    }

    private fun load(): JSONArray {
        val raw = prefs.getString(KEY, null).orEmpty()
        if (raw.isBlank()) return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun save(value: JSONArray) {
        // commit() makes enqueue/ack durable before the caller can proceed. This
        // queue is tiny and bounded, so the stronger persistence guarantee wins
        // over SharedPreferences.apply()'s deferred write semantics.
        check(prefs.edit().putString(KEY, value.toString()).commit()) {
            "AURA event queue persistence failed"
        }
    }

    private fun AuraEvent.toWireJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("id", id)
        .put("source", source)
        .put("timestampMs", timestampMs)
        .put("priority", priority)
        .put("payload", JSONObject(payload.toString()))
        .put("inFlight", false)
}
