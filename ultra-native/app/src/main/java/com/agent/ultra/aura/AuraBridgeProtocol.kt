package com.agent.ultra.aura

/** Wire-level constants shared by the AURA device bridge and relay. */
object AuraBridgeProtocol {
    const val VERSION = 1

    const val TYPE_HELLO = "hello"
    const val TYPE_TASK = "task"
    const val TYPE_RESULT = "result"
    const val TYPE_EVENT = "event"
    const val TYPE_ERROR = "error"
    const val TYPE_PING = "ping"
    const val TYPE_PONG = "pong"
    const val TYPE_STATUS = "status"
    const val TYPE_STATUS_REQUEST = "status_request"

    const val CAPABILITY_TASK = "task"
    const val CAPABILITY_EVENT = "event"
    const val CAPABILITY_STATUS = "status"
}