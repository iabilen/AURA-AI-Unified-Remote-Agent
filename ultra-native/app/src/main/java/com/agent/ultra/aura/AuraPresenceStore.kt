package com.agent.ultra.aura

import android.content.Context
import org.json.JSONObject

/** Small durable state store for the latest device presence snapshot. */
class AuraPresenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("aura_presence", Context.MODE_PRIVATE)

    @Synchronized
    fun update(values: Map<String, Any?>) {
        val json = snapshot()
        values.forEach { (key, value) -> if (value != null) json.put(key, value) }
        prefs.edit().putString("snapshot", json.toString()).apply()
    }

    @Synchronized
    fun snapshot(): JSONObject {
        val raw = prefs.getString("snapshot", null).orEmpty()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }
}
