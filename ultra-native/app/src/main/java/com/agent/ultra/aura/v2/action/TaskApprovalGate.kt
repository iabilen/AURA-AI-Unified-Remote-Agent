package com.agent.ultra.aura.v2.action

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Task- and action-scoped approval primitive for the V2 executor.
 * Approval binds one task, action and idempotency key to a short expiry.
 * Missing, late, mismatched and replayed approvals fail closed.
 * Pending approvals are process-local and must be requested again after restart.
 */
class TaskApprovalGate(
    private val onRequest: (TaskApprovalRequest) -> Unit = {},
) {
    data class TaskApprovalRequest(
        val taskId: String,
        val actionId: String,
        val idempotencyKey: String,
        val expiresAtEpochMs: Long,
        val approvalToken: String,
    )

    private data class Pending(
        val request: TaskApprovalRequest,
        val result: CompletableDeferred<Boolean>,
    )

    private data class Key(val taskId: String, val actionId: String)

    private val pending = ConcurrentHashMap<Key, Pending>()
    private val random = SecureRandom()

    suspend fun requestApproval(
        taskId: String,
        actionId: String,
        idempotencyKey: String,
        expiresAtEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        require(taskId.isNotBlank() && actionId.isNotBlank() && idempotencyKey.isNotBlank())
        val remainingMs = expiresAtEpochMs - nowEpochMs
        if (remainingMs <= 0L || remainingMs > MAX_APPROVAL_WINDOW_MS) return false

        val tokenBytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val request = TaskApprovalRequest(
            taskId, actionId, idempotencyKey, expiresAtEpochMs,
            Base64.encodeToString(tokenBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        )
        val key = Key(taskId, actionId)
        val entry = Pending(request, CompletableDeferred())
        if (pending.putIfAbsent(key, entry) != null) return false

        try {
            onRequest(request)
            return withTimeoutOrNull(remainingMs) { entry.result.await() } == true
        } finally {
            pending.remove(key, entry)
            tokenBytes.fill(0)
        }
    }

    fun resolve(
        taskId: String,
        actionId: String,
        idempotencyKey: String,
        approvalToken: String,
        approved: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val entry = pending[Key(taskId, actionId)] ?: return false
        val request = entry.request
        if (nowEpochMs >= request.expiresAtEpochMs) {
            entry.result.complete(false)
            return false
        }
        if (request.idempotencyKey != idempotencyKey) return false
        if (!MessageDigest.isEqual(
                request.approvalToken.toByteArray(Charsets.UTF_8),
                approvalToken.toByteArray(Charsets.UTF_8),
            )) return false
        return entry.result.complete(approved)
    }

    fun cancelTask(taskId: String): Int {
        val matches = pending.entries.filter { it.key.taskId == taskId }
        matches.forEach { it.value.result.complete(false) }
        return matches.size
    }

    fun expire(nowEpochMs: Long = System.currentTimeMillis()): Int {
        val expired = pending.entries.filter { nowEpochMs >= it.value.request.expiresAtEpochMs }
        expired.forEach { it.value.result.complete(false) }
        return expired.size
    }

    fun pendingRequests(): List<TaskApprovalRequest> = pending.values.map { it.request }

    companion object {
        private const val TOKEN_BYTES = 32
        private const val MAX_APPROVAL_WINDOW_MS = 120_000L
    }
}
