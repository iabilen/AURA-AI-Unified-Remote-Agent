package com.agent.ultra.aura

import android.content.Context
import com.agent.ultra.agent.Brain
import com.agent.ultra.local.LocalModelEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-level AURA runtime. A54 is only the first device body; this layer stays device-neutral. */
object AuraRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
        core = AuraCore { task ->
            pendingId = "bridge"
            brain(app).run(task)
        }
        bridge = AuraBridgeClient(app, core) { answer ->
            // The first MVP returns the answer through the event channel; correlation is
            // added by the relay protocol once the server-side session is implemented.
            bridge.sendEvent(
                AuraEvent(
                    id = pendingId ?: "bridge",
                    type = AuraEventTypes.RESULT,
                    source = "aura",
                    payload = org.json.JSONObject().put("answer", answer),
                )
            )
        }
        bridge.connect()
    }

    fun attachBrain(instance: Brain) {
        brain = instance
        instance.addAnswerListener { text ->
            if (::bridge.isInitialized && bridge.isConfigured()) {
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
