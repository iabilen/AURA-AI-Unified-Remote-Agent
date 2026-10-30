package com.agent.ultra.aura.v2.contract

/**
 * Versioned boundary between the ChatGPT orchestrator and the Android device agent.
 *
 * ChatGPT decides WHAT should happen. Android validates permissions and policy,
 * obtains approval when required, executes through a local adapter, and verifies
 * the observed result. A device-side local model must not silently re-plan a
 * ChatGPT-issued action.
 */
enum class DecisionDisposition {
    IGNORE,
    INFORM,
    ASK,
    ACT,
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class ApprovalRequirement {
    NONE,
    USER_CONFIRMATION,
    ALWAYS_CONFIRM,
}

enum class DeviceActionKind {
    OPEN_APP,
    READ_SCREEN,
    TAP,
    TYPE_TEXT,
    SWIPE,
    SCROLL,
    PRESS_BACK,
    CREATE_NOTIFICATION,
    SET_ALARM,
    CHANGE_DEVICE_SETTING,
    SEND_COMMUNICATION,
}

enum class DeviceActionStatus {
    SUCCEEDED,
    FAILED,
    NEEDS_APPROVAL,
    PARTIAL,
    CANCELLED,
    EXPIRED,
}

enum class VerificationStatus {
    VERIFIED,
    UNVERIFIED,
    FAILED,
    NOT_APPLICABLE,
}

/** A single requested operation. Parameters are data, never executable code. */
data class DeviceAction(
    val actionId: String,
    val kind: DeviceActionKind,
    val capability: String,
    val parameters: Map<String, String> = emptyMap(),
    val risk: RiskLevel = RiskLevel.LOW,
    val approval: ApprovalRequirement = ApprovalRequirement.NONE,
    val idempotencyKey: String,
)

/** Capabilities reported by the Android device, not assumed by ChatGPT. */
data class DeviceCapability(
    val name: String,
    val available: Boolean,
    val permissionGranted: Boolean,
    val requiresForeground: Boolean = false,
)

/**
 * Authenticated, expiring instruction envelope from the sole external
 * orchestrator. Transport authentication/signature validation is performed
 * before this object is accepted by the runtime.
 */
data class ActionPlan(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val requestId: String,
    val deviceId: String,
    val disposition: DecisionDisposition,
    val actions: List<DeviceAction> = emptyList(),
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val explanation: String? = null,
) {
    fun validate(nowEpochMs: Long): List<String> {
        val errors = mutableListOf<String>()

        if (schemaVersion != CURRENT_SCHEMA_VERSION) errors += "Unsupported schema version"
        if (requestId.isBlank()) errors += "Missing request id"
        if (deviceId.isBlank()) errors += "Missing device id"
        if (issuedAtEpochMs <= 0L || expiresAtEpochMs <= issuedAtEpochMs) {
            errors += "Invalid plan time window"
        }
        if (nowEpochMs < issuedAtEpochMs) errors += "Plan is not yet valid"
        if (nowEpochMs >= expiresAtEpochMs) errors += "Plan has expired"

        if (disposition == DecisionDisposition.ACT && actions.isEmpty()) {
            errors += "ACT requires at least one action"
        }
        if (disposition != DecisionDisposition.ACT && actions.isNotEmpty()) {
            errors += "Only ACT may contain executable actions"
        }

        val ids = actions.map { it.actionId }
        if (ids.any { it.isBlank() } || ids.size != ids.toSet().size) {
            errors += "Action ids must be non-empty and unique"
        }
        if (actions.any { it.capability.isBlank() || it.idempotencyKey.isBlank() }) {
            errors += "Each action requires a capability and idempotency key"
        }
        if (actions.any {
                it.risk == RiskLevel.HIGH && it.approval == ApprovalRequirement.NONE
            }) {
            errors += "HIGH risk actions require user confirmation"
        }
        if (actions.any {
                it.risk == RiskLevel.CRITICAL &&
                    it.approval != ApprovalRequirement.ALWAYS_CONFIRM
            }) {
            errors += "CRITICAL risk actions require explicit confirmation every time"
        }

        return errors
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/** Result returned by the device executor after the action and verification. */
data class ActionResult(
    val requestId: String,
    val actionId: String,
    val status: DeviceActionStatus,
    val verification: VerificationStatus,
    val observedAtEpochMs: Long,
    val summary: String,
    val errorCode: String? = null,
)
