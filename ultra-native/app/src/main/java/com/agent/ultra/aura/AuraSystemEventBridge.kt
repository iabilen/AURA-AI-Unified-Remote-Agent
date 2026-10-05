package com.agent.ultra.aura

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import org.json.JSONObject

/**
 * Converts low-risk Android system state changes into normalized AURA events.
 * No message bodies, notification text, contact data, or credentials are forwarded.
 */
class AuraSystemEventBridge(
    private val context: Context,
    private val publish: (AuraEvent) -> Unit
) {
    companion object {
        private const val TAG = "AuraSystemEvents"
    }

    private var receiver: BroadcastReceiver? = null

    @Synchronized
    fun start() {
        if (receiver != null) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                runCatching { publishIntent(intent) }
                    .onFailure { Log.w(TAG, "system event ignored", it) }
            }
        }

        context.registerReceiver(receiver, filter)
    }

    @Synchronized
    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun publishIntent(intent: Intent) {
        val action = intent.action ?: return
        val now = System.currentTimeMillis()
        val payload = JSONObject()

        when (action) {
            Intent.ACTION_BATTERY_LOW -> payload.put("state", "low")
            Intent.ACTION_BATTERY_OKAY -> payload.put("state", "ok")
            Intent.ACTION_POWER_CONNECTED -> payload.put("state", "connected")
            Intent.ACTION_POWER_DISCONNECTED -> payload.put("state", "disconnected")
            Intent.ACTION_SCREEN_ON -> payload.put("state", "on")
            Intent.ACTION_SCREEN_OFF -> payload.put("state", "off")
            Intent.ACTION_HEADSET_PLUG -> {
                payload.put("state", if (intent.getIntExtra("state", 0) == 1) "connected" else "disconnected")
            }
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> {
                // Package identity is operational metadata, not message/contact content.
                payload.put("state", if (action == Intent.ACTION_PACKAGE_ADDED) "added" else "removed")
                intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() }?.let { payload.put("package", it) }
            }
            else -> return
        }

        val type = when (action) {
            Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY -> "battery"
            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> "power"
            Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> "screen"
            Intent.ACTION_HEADSET_PLUG -> "headset"
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> "package"
            else -> return
        }

        publish(
            AuraEvent(
                id = "system:$type:$now",
                type = type,
                source = "android.system",
                timestampMs = now,
                priority = if (type == "battery") 1 else 0,
                payload = payload
            )
        )
    }
}
