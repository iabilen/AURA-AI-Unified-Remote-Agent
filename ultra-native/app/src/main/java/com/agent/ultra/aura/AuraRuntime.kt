package com.agent.ultra.aura

import android.content.Context
import com.agent.ultra.agent.Brain
import com.agent.ultra.local.LocalModelEngine

/** Process-level AURA runtime. A54 is only the first device body; this layer stays device-neutral. */
object AuraRuntime {
    private var initialized = false
    private var brain: Brain? = null
    private var pendingId: String? = null
    private lateinit var core: AuraCore
    private lateinit var bridge: AuraBridgeClient

    @Synchronized
    fun start(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        core = AuraCore { id, task ->
            pendingId = id
            brain(app).run(task)
        }
        bridge = AuraBridgeClient(app, core)
        bridge.connect()
    }

    /** Publish a device-side event without forcing the local model to load. */
    fun publishEvent(event: AuraEvent): Boolean {
        if (!::core.isInitialized) return false
        val accepted = core.publish(event)
        if (::bridge.isInitialized) bridge.sendEvent(event)
        return accepted
    }

    fun attachBrain(instance: Brain) {
        brain = instance
        instance.addAnswerListener { text ->
            if (::bridge.isInitialized) {
                bridge.sendEvent(
                    AuraEvent(
                        id = pendingId ?: "bridge",
                        type = AuraEventTypes.RESULT,
                        source = "aura",
                        payload = org.json.JSONObject().put("answer", text),
                    )
                )
            }
        }
    }

    fun configureBridge(endpoint: String, token: String) {
        check(::bridge.isInitialized) { "AuraRuntime.start() must run first" }
        bridge.configure(endpoint, token)
    }

    private fun brain(context: Context): Brain = brain ?: Brain(
        context,
        LocalModelEngine.shared(context),
    ).also { attachBrain(it) }
}
