package com.agent.ultra.aura

import android.content.Context
import org.json.JSONObject

/** Small durable state store for the latest device presence snapshot. */
class AuraPresenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("aura_presence", Context.MODE_PRIVATE)

    /**
     * Replace the supplied fields in one persisted snapshot.
     * The state is intentionally tiny, so commit() is used instead of apply():
     * callers can rely on the method returning only after the snapshot has been
     * handed to the persistent SharedPreferences store. This is important for
     * crash/offline recovery and relay status reads.
     */
    @Synchronized
    fun update(values: Map<String, Any?>) {
        val json = snapshot()
        values.forEach { (key, value) ->
            if (value != null) json.put(key, value)
        }
        json.put("updatedAt", System.currentTimeMillis())
        check(
            prefs.edit()
                .putString("snapshot", json.toString())
                .commit()
        ) { "Failed to persist AURA presence snapshot" }
    }

    @Synchronized
    fun snapshot(): JSONObject {
        val raw = prefs.getString("snapshot", null).orEmpty()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }
}
