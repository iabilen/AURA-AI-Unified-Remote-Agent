package com.agent.ultra.aura.v2.policy

/**
 * Privacy defaults for notification-derived events.
 *
 * The default is metadata-only. Notification title/body and conversation content
 * are never eligible unless the owner explicitly enables content capture and
 * allowlists both the source app and event category.
 *
 * This policy is a pure decision model; runtime adapters must call it before
 * constructing, persisting, logging, or transmitting any notification event.
 */
data class NotificationPrivacyPolicy(
    val captureMetadata: Boolean = true,
    val captureContent: Boolean = false,
    val contentAllowedPackages: Set<String> = emptySet(),
    val contentAllowedCategories: Set<String> = emptySet(),
    val rejectSensitiveContent: Boolean = true,
) {
    fun shouldCaptureMetadata(packageName: String): Boolean {
        return captureMetadata && packageName.isNotBlank()
    }

    fun shouldCaptureContent(
        packageName: String,
        category: String,
        containsSensitiveContent: Boolean,
    ): Boolean {
        if (!captureContent || packageName.isBlank() || category.isBlank()) return false
        if (packageName !in contentAllowedPackages) return false
        if (category !in contentAllowedCategories) return false
        if (rejectSensitiveContent && containsSensitiveContent) return false
        return true
    }

    companion object {
        /** Safe baseline: minimal metadata only; no notification text. */
        val DEFAULT = NotificationPrivacyPolicy()
    }
}

/**
 * Deliberately content-free metadata. Do not add title, body, sender, or
 * conversation text to this model.
 */
data class NotificationMetadata(
    val packageName: String,
    val category: String?,
    val postedAtEpochMs: Long,
)
