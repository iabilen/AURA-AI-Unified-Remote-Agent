package com.agent.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NotificationLogPrivacyTest {
    @Test
    fun persistentNotificationLogs_containMetadataOnly() {
        val source = File("src/main/java/com/agent/ultra/UltraNotificationService.kt").readText()
        val notificationLine = NotificationLogSanitizer.formatNotificationLog(123L, "com.example.bank", true)
        val scamLine = NotificationLogSanitizer.formatScamWarningLog("com.example.bank", true)

        assertEquals("123 | com.example.bank | [LOOKS LIKE A SCAM]\n", notificationLine)
        assertEquals("SCAM WARN from=com.example.bank: warned=true", scamLine)
        assertTrue(source.contains("NotificationLogSanitizer.formatNotificationLog"))
        assertTrue(source.contains("NotificationLogSanitizer.formatScamWarningLog"))
        assertFalse(source.contains("append(title.take(60))"))
        assertFalse(source.contains("append(text.take(120))"))
        assertFalse(source.contains("SCAM WARN from=${pkg}: ${a.data}"))
    }
}
