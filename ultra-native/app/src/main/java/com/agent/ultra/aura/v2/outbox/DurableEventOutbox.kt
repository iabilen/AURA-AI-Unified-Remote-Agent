package com.agent.ultra.aura.v2.outbox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.agent.ultra.aura.AuraEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted, device-local at-least-once event outbox.
 *
 * Contract:
 * 1. enqueue() durably stores an event before any transport send.
 * 2. claimBatch() persists a lease before returning events to the sender.
 * 3. A transport ACK removes only the matching event ID.
 * 4. A crash after send but before ACK causes a retry after lease expiry.
 *
 * This class is deliberately not wired into the V1 bridge. V2 integration must
 * replace the legacy send path only after the bridge protocol supports ACK IDs.
 */
data class OutboxRecord(
    val event: AuraEvent,
    val enqueuedAtEpochMs: Long,
    val attemptCount: Int,
    val leaseUntilEpochMs: Long,
    val nextAttemptAtEpochMs: Long,
)

class DurableEventOutbox(context: Context) {
    private val appContext = context.applicationContext
    private val atomicFile = AtomicFile(File(appContext.noBackupFilesDir, STORE_FILE))
    private val lock = Any()

    /** Stores an event before transport. Returns false when its ID already exists. */
    fun enqueue(event: AuraEvent, nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            validateEvent(event)
            val current = readRecords()
            if (current.any { it.event.id == event.id }) return@synchronized false
            if (current.size >= MAX_RECORDS) {
                throw IllegalStateException("AURA outbox is full; events were not discarded")
            }

            val wirePayload = eventToJson(event).toString()
            require(wirePayload.toByteArray(Charsets.UTF_8).size <= MAX_EVENT_BYTES) {
                "AURA event exceeds the outbox event size limit"
            }

            writeRecords(
                current + OutboxRecord(
                    event = eventFromJson(JSONObject(wirePayload)),
                    enqueuedAtEpochMs = nowEpochMs,
                    attemptCount = 0,
                    leaseUntilEpochMs = 0L,
                    nextAttemptAtEpochMs = nowEpochMs,
                )
            )
            true
        }

    /**
     * Claims due events and persists their lease before returning them.
     * Expired leases are eligible again, providing crash recovery.
     */
    fun claimBatch(
        nowEpochMs: Long = System.currentTimeMillis(),
        leaseDurationMs: Long = DEFAULT_LEASE_MS,
        limit: Int = DEFAULT_BATCH_SIZE,
    ): List<OutboxRecord> = synchronized(lock) {
        require(leaseDurationMs in MIN_LEASE_MS..MAX_LEASE_MS) { "Invalid outbox lease duration" }
        require(limit in 1..MAX_BATCH_SIZE) { "Invalid outbox batch size" }

        val current = readRecords()
        val due = current.asSequence()
            .filter {
                it.nextAttemptAtEpochMs <= nowEpochMs &&
                    it.leaseUntilEpochMs <= nowEpochMs
            }
            .sortedWith(
                compareByDescending<OutboxRecord> { it.event.priority }
                    .thenBy { it.enqueuedAtEpochMs }
            )
            .take(limit)
            .toList()

        if (due.isEmpty()) return@synchronized emptyList()

        val claimedIds = due.map { it.event.id }.toSet()
        val leaseUntil = nowEpochMs + leaseDurationMs
        val updated = current.map {
            if (it.event.id in claimedIds) {
                it.copy(
                    attemptCount = it.attemptCount + 1,
                    leaseUntilEpochMs = leaseUntil,
                )
            } else it
        }
        writeRecords(updated)
        updated.filter { it.event.id in claimedIds }
            .sortedWith(
                compareByDescending<OutboxRecord> { it.event.priority }
                    .thenBy { it.enqueuedAtEpochMs }
            )
    }

    /** Removes an event only after the relay ACKs that exact event ID. */
    fun acknowledge(eventId: String): Boolean = synchronized(lock) {
        if (eventId.isBlank()) return@synchronized false
        val current = readRecords()
        val updated = current.filterNot { it.event.id == eventId }
        if (updated.size == current.size) return@synchronized false
        writeRecords(updated)
        true
    }

    /** Releases a failed send for retry; the event remains durably stored. */
    fun release(
        eventId: String,
        nextAttemptAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        if (eventId.isBlank()) return@synchronized false
        val current = readRecords()
        val index = current.indexOfFirst { it.event.id == eventId }
        if (index < 0) return@synchronized false
        val updated = current.toMutableList()
        updated[index] = updated[index].copy(
            leaseUntilEpochMs = 0L,
            nextAttemptAtEpochMs = nextAttemptAtEpochMs,
        )
        writeRecords(updated)
        true
    }

    fun pendingCount(): Int = synchronized(lock) { readRecords().size }

    private fun validateEvent(event: AuraEvent) {
        require(event.id.isNotBlank()) { "Event ID is required" }
        require(event.type.isNotBlank()) { "Event type is required" }
        require(event.source.isNotBlank()) { "Event source is required" }
        require(event.timestampMs > 0L) { "Event timestamp must be positive" }
        require(event.priority >= 0) { "Event priority cannot be negative" }
    }

    private fun readRecords(): List<OutboxRecord> {
        val encrypted = try {
            // AtomicFile.openRead() restores its backup after an interrupted write.
            atomicFile.openRead().use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        require(encrypted.size <= MAX_ENCRYPTED_BYTES) { "AURA outbox exceeds the safety limit" }

        val root = JSONObject(String(decrypt(encrypted), Charsets.UTF_8))
        val version = root.optInt("schemaVersion", -1)
        if (version != SCHEMA_VERSION) {
            throw IllegalStateException("Unsupported AURA outbox schema: $version")
        }
        val records = root.optJSONArray("records")
            ?: throw IllegalStateException("AURA outbox records are missing")
        return buildList {
            for (index in 0 until records.length()) {
                add(recordFromJson(records.getJSONObject(index)))
            }
        }
    }

    private fun writeRecords(records: List<OutboxRecord>) {
        require(records.size <= MAX_RECORDS) { "AURA outbox capacity exceeded" }
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("records", JSONArray().apply {
                records.forEach { put(recordToJson(it)) }
            })
        val plaintext = root.toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "AURA outbox storage limit exceeded" }
        val encrypted = encrypt(plaintext)

        atomicFile.baseFile.parentFile?.mkdirs()
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(encrypted)
            output.flush()
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            if (output != null) atomicFile.failWrite(output)
            throw IllegalStateException("AURA outbox could not be committed; existing data was retained", error)
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(payload)
            require(buffer.remaining() >= Int.SIZE_BYTES) { "Invalid AURA outbox envelope" }
            val ivSize = buffer.int
            require(ivSize in MIN_GCM_IV_BYTES..MAX_GCM_IV_BYTES && buffer.remaining() > ivSize) {
                "Invalid AURA outbox IV"
            }
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw IllegalStateException(
                "AURA outbox could not be decrypted; queued events were not discarded",
                error,
            )
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun eventToJson(event: AuraEvent): JSONObject = JSONObject()
        .put("id", event.id)
        .put("type", event.type)
        .put("source", event.source)
        .put("timestampMs", event.timestampMs)
        .put("priority", event.priority)
        .put("payload", JSONObject(event.payload.toString()))

    private fun eventFromJson(json: JSONObject): AuraEvent = AuraEvent(
        id = json.getString("id"),
        type = json.getString("type"),
        source = json.getString("source"),
        timestampMs = json.getLong("timestampMs"),
        priority = json.getInt("priority"),
        payload = JSONObject(json.getJSONObject("payload").toString()),
    )

    private fun recordToJson(record: OutboxRecord): JSONObject = JSONObject()
        .put("event", eventToJson(record.event))
        .put("enqueuedAtEpochMs", record.enqueuedAtEpochMs)
        .put("attemptCount", record.attemptCount)
        .put("leaseUntilEpochMs", record.leaseUntilEpochMs)
        .put("nextAttemptAtEpochMs", record.nextAttemptAtEpochMs)

    private fun recordFromJson(json: JSONObject): OutboxRecord = OutboxRecord(
        event = eventFromJson(json.getJSONObject("event")),
        enqueuedAtEpochMs = json.getLong("enqueuedAtEpochMs"),
        attemptCount = json.getInt("attemptCount"),
        leaseUntilEpochMs = json.getLong("leaseUntilEpochMs"),
        nextAttemptAtEpochMs = json.getLong("nextAttemptAtEpochMs"),
    )

    companion object {
        private const val STORE_FILE = "aura-v2-event-outbox.enc"
        private const val KEY_ALIAS = "aura.v2.event.outbox"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SCHEMA_VERSION = 1
        private const val GCM_TAG_BITS = 128
        private const val MIN_GCM_IV_BYTES = 12
        private const val MAX_GCM_IV_BYTES = 16
        private const val MAX_RECORDS = 1_000
        private const val MAX_BATCH_SIZE = 100
        private const val DEFAULT_BATCH_SIZE = 25
        private const val MAX_EVENT_BYTES = 64 * 1024
        private const val MAX_PLAINTEXT_BYTES = 12 * 1024 * 1024
        private const val MAX_ENCRYPTED_BYTES = 13 * 1024 * 1024
        private const val DEFAULT_LEASE_MS = 60_000L
        private const val MIN_LEASE_MS = 5_000L
        private const val MAX_LEASE_MS = 15 * 60_000L
    }
}
