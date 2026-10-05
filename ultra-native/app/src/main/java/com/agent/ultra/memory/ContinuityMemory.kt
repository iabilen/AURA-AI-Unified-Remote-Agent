package com.agent.ultra.memory

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline, device-local continuity layer for AURA.
 *
 * Runtime state lives only in app-private Android storage. Git-tracked continuity
 * documents are engineering/source material used to seed a fresh local store;
 * they are not a live runtime dependency. AURA must never contact GitHub, Hjarni,
 * Cortex, Dropbox, or another external memory service from the runtime memory layer.
 * ChatGPT may receive bounded continuity context through the normal AURA path and
 * may independently back up relevant state externally. External backups are not
 * synchronization inputs to AURA.
 */
class ContinuityMemory(context: Context) {
    private val root = File(context.filesDir, "aura-continuity")
    private val shortFile = File(root, "AURA_CONTEXT_SHORT.md")
    private val longFile = File(root, "AURA_MEMORY.md")
    private val journalFile = File(root, "AURA_EVENT_JOURNAL.md")

    init { ensureSeeded() }

    /** Small, bounded context suitable for the model system prompt. */
    @Synchronized
    fun promptContext(maxChars: Int = 9000): String {
        ensureSeeded()
        val short = shortFile.readText().take(3500)
        val long = longFile.readText().take(3000)
        val journal = journalFile.readLines().takeLast(35).joinToString("\n").take(2500)
        return ("SHORT\n$short\n\nLONG\n$long\n\nRECENT JOURNAL\n$journal").take(maxChars)
    }

    @Synchronized
    fun recordRunStarted(request: String) {
        append("RUN_STARTED", "requestHash=${hash(request)} requestLength=${request.length}")
    }

    @Synchronized
    fun recordRunReturned(credited: Boolean, toolCount: Int) {
        append("RUN_RETURNED", "credited=$credited toolCount=$toolCount")
    }

    private fun append(kind: String, detail: String) {
        ensureSeeded()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        journalFile.appendText("\n### $now — $kind\n- $detail\n")
        trimJournal()
    }

    private fun trimJournal(maxChars: Int = 24000) {
        val text = journalFile.readText()
        if (text.length <= maxChars) return
        val keep = text.takeLast(maxChars)
        journalFile.writeText("# AURA Event Journal\n\n$keep")
    }

    private fun ensureSeeded() {
        root.mkdirs()
        if (!shortFile.exists()) shortFile.writeText(SHORT_SEED)
        if (!longFile.exists()) longFile.writeText(LONG_SEED)
        if (!journalFile.exists()) journalFile.writeText(JOURNAL_SEED)
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(16)

    companion object {
        private const val SHORT_SEED = """# AURA Context — SHORT

- AURA = AI Unified Remote Agent.
- ChatGPT is the primary AI/orchestrator; AURA is the Android body/agent layer.
- OpenAI is the single external AI intelligence path; AURA runtime memory remains local.
- Device layer: Android events → AURA Event Bus → authenticated outbound relay/device channel → ChatGPT MCP → Brain.run().
- Remote control remains authenticated and subject to the existing safety gate.
- Runtime continuity is offline and device-local; this local store is AURA's only runtime memory source.
- AURA does not contact GitHub, Hjarni, Cortex, Dropbox, or other external memory/backup services.
- ChatGPT may consume bounded local continuity context through the normal AURA path and may back it up externally; external backup is not synced back into AURA automatically.
- Next priorities: pairing/revocation/device identity, persistent bridge lifecycle, event adapters/privacy filtering, camera/screen tools, durable relay storage/multi-device routing.
"""
        private const val LONG_SEED = """# AURA Memory — LONG

## Core principles
- Preserve the working Ultra Agent core until equivalence is proven by tests.
- Keep AURA device-independent; the A54 is an endpoint, not a permanent hard-coded target.
- Do not introduce NAS/F8/server dependencies into the core architecture.
- Consequential remote actions stay behind the existing Android safety confirmation/gate.

## Runtime continuity
- SHORT holds current state and next action.
- LONG holds durable architecture decisions.
- EVENT JOURNAL holds concise implementation/runtime milestones.
- Never persist bearer tokens, secrets, notification payloads, or raw user requests here.
"""
        private const val JOURNAL_SEED = """# AURA Event Journal

Chronological, append-only device continuity log. Entries are intentionally concise and privacy-preserving.

### 2026-10-05 — Runtime continuity core
- Added device-local continuity storage connected to Brain system context.
- User requests are represented by a short SHA-256 fingerprint, not raw text.
"""
    }
}
