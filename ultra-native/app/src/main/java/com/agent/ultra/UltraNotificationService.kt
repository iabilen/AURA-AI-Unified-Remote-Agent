package com.agent.ultra

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.agent.ultra.agent.EventTrigger
import com.agent.ultra.aura.AuraEvent
import com.agent.ultra.aura.AuraEventTypes
import com.agent.ultra.aura.AuraRuntime
import java.io.File

/**
 * Notification listener and first external AURA wake source.
 * Android notification access is required; content is normalized into AURA
 * without loading the local model for every notification.
 */
class UltraNotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        Log.i(TAG, "notification listener connected")
        EventTrigger.registerDefaults()
        AuraRuntime.start(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            if (AgentAccessibilityService.isPackageBlocked(sbn.packageName)) {
                Log.i(TAG, "skipped protected app: ${sbn.packageName}")
                return
            }
            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            publishAuraEvent(sbn, title, text)
            val warned = runTriggers(sbn.packageName, title, text)

            if (!com.agent.ultra.ui.UltraPrefs.captureNotifications(this)) return
            val line = buildString {
                append(sbn.postTime).append(" | ").append(sbn.packageName)
                if (warned != null) append(" | [LOOKS LIKE A SCAM: ").append(warned).append("]")
                if (title.isNotBlank()) append(" | ").append(title.take(60))
                if (text.isNotBlank()) append(" | ").append(text.take(120))
                append("\n")
            }
            val f = File(filesDir, "notifications.log")
            f.appendText(line)
            if (f.length() > 64 * 1024) {
                val lines = f.readLines()
                f.writeText(lines.takeLast(200).joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "post capture failed", e)
        }
    }

    private fun publishAuraEvent(sbn: StatusBarNotification, title: String, text: String) {
        val priority = when {
            sbn.isOngoing -> 1
            title.isNotBlank() || text.isNotBlank() -> 5
            else -> 2
        }
        val notification = org.json.JSONObject()
            .put("package", sbn.packageName)
            .put("title", title.take(500))
            .put("text", text.take(2000))
            .put("postTime", sbn.postTime)
            .put("ongoing", sbn.isOngoing)
        AuraRuntime.publishEvent(
            AuraEvent(
                id = "notification:${sbn.key}:${sbn.postTime}",
                type = AuraEventTypes.EVENT,
                source = "android.notification",
                priority = priority,
                timestampMs = sbn.postTime,
                payload = org.json.JSONObject().put("notification", notification),
            )
        )
    }

    private fun runTriggers(pkg: String, title: String, text: String): String? {
        val actions = EventTrigger.evaluate(this, pkg, title, text)
        var warned: String? = null
        for (a in actions) {
            when (a.type) {
                EventTrigger.Action.Type.WARN -> {
                    warned = a.data
                    Log.i(TAG, "SCAM WARN from=${pkg}: ${a.data}")
                    val posted = postWarning(title, a.label)
                    android.os.Handler(mainLooper).post {
                        if (!posted) Toast.makeText(this, "Ultra: " + a.label, Toast.LENGTH_LONG).show()
                        if (com.agent.ultra.ui.UltraPrefs.speakAnswers(this)) {
                            speaker().speak("Careful. " + a.label + " Don't send money, codes or cards to it.")
                        }
                    }
                }
                EventTrigger.Action.Type.CLIPBOARD_COPY -> {
                    try {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("ultra", a.data))
                        android.os.Handler(mainLooper).post { Toast.makeText(this, a.label, Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        Log.w(TAG, "clipboard action failed: ${e.message}")
                    }
                }
                EventTrigger.Action.Type.TOAST -> {
                    android.os.Handler(mainLooper).post { Toast.makeText(this, a.label, Toast.LENGTH_SHORT).show() }
                }
            }
        }
        return warned
    }

    private fun postWarning(from: String, label: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
            val nm = getSystemService(android.app.NotificationManager::class.java) ?: return false
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(android.app.NotificationChannel(
                    SCAM_CHANNEL, "Scam warnings", android.app.NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "A message that looks like a scam just arrived." })
            }
            val sender = from.ifBlank { "a message" }.take(40)
            val n = androidx.core.app.NotificationCompat.Builder(this, SCAM_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Careful — this looks like a scam")
                .setContentText("From $sender")
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(
                    "From $sender. $label Don't send money, codes or gift cards, and don't open its link. " +
                        "If it says it's family or your bank, call them on a number you already have."))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .build()
            nm.notify(SCAM_ID_BASE + (System.currentTimeMillis() % 1000).toInt(), n)
            true
        } catch (e: Exception) {
            Log.w(TAG, "scam warning notification failed: ${e.message}")
            false
        }
    }

    private var speakerRef: com.agent.ultra.ui.Speaker? = null
    private fun speaker() = speakerRef ?: com.agent.ultra.ui.Speaker(this).also { speakerRef = it }

    override fun onDestroy() {
        try { speakerRef?.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UltraNotif"
        private const val SCAM_CHANNEL = "ultra_scam_warnings"
        private const val SCAM_ID_BASE = 7000
    }
}
