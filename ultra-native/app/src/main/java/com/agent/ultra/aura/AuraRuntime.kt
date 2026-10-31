package com.agent.ultra.aura

import android.content.Context
import com.agent.ultra.agent.Brain
import com.agent.ultra.aura.v2.task.DurableTaskInbox
import org.json.JSONObject

/** Process-level AURA runtime. A54 is only the first device body; this layer stays device-neutral. */
object AuraRuntime {
    private var initialized = false
    private var brain: Brain? = null
    private lateinit var core: AuraCore
    private lateinit var bridge: AuraBridgeClient
    private lateinit var taskInbox: DurableTaskInbox

    @Synchronized
    fun start(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        taskInbox = DurableTaskInbox(app)
        // Remote tasks are durably received only. They must not enter the local
        // Brain/model path: execution waits for an authenticated ChatGPT decision
        // gateway and a device-side action authorization handshake.
        core = AuraCore { id, task ->
            val outcome = runCatching { taskInbox.accept(id, task) }
            val payload = JSONObject()
            if (outcome.isSuccess) {
                val accepted = outcome.getOrThrow()
                payload.put("status", "stored")
                    .put("taskState", accepted.record.state.name.lowercase())
                    .put("duplicate", accepted.acceptance == DurableTaskInbox.Acceptance.DUPLICATE)
                    .put("execution", "blocked_pending_authenticated_chatgpt_decision")
            } else {
                payload.put("status", "rejected")
                    .put("reason", outcome.exceptionOrNull()?.message ?: "task_inbox_error")
            }
            if (::bridge.isInitialized) {
                bridge.sendEvent(
                    AuraEvent(
                        id = id,
                        type = AuraEventTypes.RESULT,
                        source = "aura",
                        payload = payload,
                    )
                )
            }
        }
        bridge = AuraBridgeClient(app, core)
        bridge.connect()
        initialized = true
    }

    /** Publish a device-side event without forcing the local model to load. */
    fun publishEvent(event: AuraEvent): Boolean {
        if (!::core.isInitialized) return false
        val accepted = core.publish(event)
        if (::bridge.isInitialized) bridge.sendEvent(event)
        return accepted
    }

    /** Attach the local interactive Brain; it is not used for Relay task decisions. */
    fun attachBrain(instance: Brain) {
        brain = instance
    }

    fun configureBridge(endpoint: String, token: String) {
        check(::bridge.isInitialized) { "AuraRuntime.start() must run first" }
        bridge.configure(endpoint, token)
    }

    /** Read-only task status for the local UI/diagnostics layer. */
    fun taskStatus(taskId: String): DurableTaskInbox.Record? =
        if (::taskInbox.isInitialized) taskInbox.get(taskId) else null
}
