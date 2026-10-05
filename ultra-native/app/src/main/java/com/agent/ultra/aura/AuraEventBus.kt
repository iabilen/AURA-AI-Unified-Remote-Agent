package com.agent.ultra.aura

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** In-process event bus. External transports publish into this bus; Android code never talks to the transport directly. */
class AuraEventBus {
    private val _events = MutableSharedFlow<AuraEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AuraEvent> = _events.asSharedFlow()

    fun publish(event: AuraEvent): Boolean = _events.tryEmit(event)
}
