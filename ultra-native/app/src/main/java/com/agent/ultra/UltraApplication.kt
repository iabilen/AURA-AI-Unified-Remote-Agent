package com.agent.ultra

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.agent.ultra.aura.AuraRuntime

class UltraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Start AURA at process creation so notification/event sources can wake the bridge
        // even when the chat UI has never been opened.
        try { AuraRuntime.start(this) } catch (e: Exception) {
            android.util.Log.w("UltraApp", "AURA runtime start failed", e)
        }
        try { com.agent.ultra.ui.ProtectedApps.seedIfUnset(this) } catch (_: Exception) {}
        try {
            ContextCompat.startForegroundService(this, Intent(this, AgentBackgroundService::class.java))
        } catch (e: Exception) {
            android.util.Log.w("UltraApp", "background service start failed", e)
        }
    }

    companion object {
        lateinit var instance: UltraApplication
            private set
    }
}
