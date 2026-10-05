package com.agent.ultra

/** Formats persistent operational logs without accepting notification payload content. */
internal object NotificationLogSanitizer {
    fun formatNotificationLog(postTime: Long, packageName: String, warned: Boolean): String = buildString {
        append(postTime).append(" | ").append(packageName)
        if (warned) append(" | [LOOKS LIKE A SCAM]")
        append("\n")
    }

    fun formatScamWarningLog(packageName: String, warned: Boolean): String =
        "SCAM WARN from=$packageName: warned=$warned"
}
