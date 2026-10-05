package com.agent.ultra.aura

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates transport events and the existing Brain without replacing Brain's safety model. */
class AuraCore(
    private val bus: AuraEventBus = AuraEventBus(),
    private val taskHandler: suspend (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val taskMutex = Mutex()

    init {
        scope.launch {
            bus.events
                .filter { it.type == AuraEventTypes.TASK }
                .collect { event ->
                    val task = event.payload.optString("task").trim()
                    if (task.isEmpty()) return@collect
                    // One remote task at a time. This prevents two external callers from
                    // interleaving Brain tool loops and preserves the existing task semantics.
                    taskMutex.withLock { taskHandler(task) }
                }
        }
    }

    fun publish(event: AuraEvent): Boolean = bus.publish(event)
}

object AuraEventTypes {
    const val TASK = "task"
    const val RESULT = "result"
    const val ERROR = "error"
    const val HELLO = "hello"
    const val EVENT = "event"
}
