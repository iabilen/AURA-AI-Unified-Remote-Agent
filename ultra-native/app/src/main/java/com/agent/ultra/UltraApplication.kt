package com.agent.ultra

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.agent.ultra.aura.AuraConnectivityRecovery
import com.agent.ultra.aura.AuraEvent
import com.agent.ultra.aura.AuraLocationPresence
import com.agent.ultra.aura.AuraPresenceStore
import com.agent.ultra.aura.AuraRuntime

class UltraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            AuraRuntime.start(this)
            val store = AuraPresenceStore(this)
            AuraLocationPresence(this, AuraRuntime::publishEvent, store).start()
            AuraConnectivityRecovery(this, AuraRuntime::publishEvent, { AuraRuntime.start(this) }, store).start()
        } catch (e: Exception) {
            Log.w("UltraApp", "AURA runtime start failed", e)
        }
        try {
            ContextCompat.startForegroundService(this, Intent(this, AgentBackgroundService::class.java))
        } catch (e: Exception) {
            Log.w("UltraApp", "background service start failed", e)
        }
    }

    companion object {
        lateinit var instance: UltraApplication
            private set
    }
}
