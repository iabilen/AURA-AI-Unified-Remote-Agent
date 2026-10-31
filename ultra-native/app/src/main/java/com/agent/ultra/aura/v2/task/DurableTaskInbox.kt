package com.agent.ultra.aura.v2.task

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted, bounded device-side inbox for Relay-originated tasks.
 *
 * A task is persisted before it can be acknowledged as locally received.
 * RUNNING records found after process restart become UNKNOWN_AFTER_CRASH and
 * are never replayed automatically. This class deliberately does not execute
 * tasks; an authenticated decision/dispatch handshake must own that transition.
 */
class DurableTaskInbox(context: Context) {
    enum class State { RECEIVED, RUNNING, COMPLETED, FAILED, UNKNOWN_AFTER_CRASH }

    enum class Acceptance { ACCEPTED, DUPLICATE }

    data class Record(
        val taskId: String,
        val payload: String,
        val payloadSha256: String,
        val state: State,
        val createdAtEpochMs: Long,
        val updatedAtEpochMs: Long,
    )

    data class AcceptResult(val acceptance: Acceptance, val record: Record)

    private val lock = Any()
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, FILE_NAME))
    private val key: SecretKey = getOrCreateKey()

    init {
        synchronized(lock) {
            val records = readRecords()
            var changed = false
            val recovered = records.map { record ->
                if (record.state == State.RUNNING) {
                    changed = true
                    record.copy(state = State.UNKNOWN_AFTER_CRASH, updatedAtEpochMs = System.currentTimeMillis())
                } else record
            }
            if (changed) writeRecords(recovered)
        }
    }

    @Throws(Exception::class)
    fun accept(taskId: String, payload: String, nowEpochMs: Long = System.currentTimeMillis()): AcceptResult {
        require(taskId.isNotBlank() && taskId.length <= MAX_TASK_ID_LENGTH) { "Invalid task id" }
        require(payload.isNotBlank() && payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            "Invalid or oversized task payload"
        }
        val hash = sha256(payload)
        synchronized(lock) {
            val records = readRecords().toMutableList()
            val existing = records.firstOrNull { it.taskId == taskId }
            if (existing != null) {
                require(existing.payloadSha256 == hash) { "Task id reused with different payload" }
                return AcceptResult(Acceptance.DUPLICATE, existing)
            }
            require(records.size < MAX_RECORDS) { "Task inbox is full" }
            val record = Record(taskId, payload, hash, State.RECEIVED, nowEpochMs, nowEpochMs)
            records += record
            writeRecords(records)
            return AcceptResult(Acceptance.ACCEPTED, record)
        }
    }

    /** Claims a received task once. There is intentionally no automatic retry. */
    fun claim(taskId: String, nowEpochMs: Long = System.currentTimeMillis()): Record? =
        transition(taskId, setOf(State.RECEIVED), State.RUNNING, nowEpochMs)

    fun complete(taskId: String, success: Boolean, nowEpochMs: Long = System.currentTimeMillis()): Record? =
        transition(taskId, setOf(State.RUNNING), if (success) State.COMPLETED else State.FAILED, nowEpochMs)

    fun get(taskId: String): Record? = synchronized(lock) {
        readRecords().firstOrNull { it.taskId == taskId }
    }

    fun snapshot(): List<Record> = synchronized(lock) { readRecords().toList() }

    private fun transition(
        taskId: String,
        allowed: Set<State>,
        next: State,
        nowEpochMs: Long,
    ): Record? = synchronized(lock) {
        val records = readRecords().toMutableList()
        val index = records.indexOfFirst { it.taskId == taskId }
        if (index < 0 || records[index].state !in allowed) return@synchronized null
        val updated = records[index].copy(state = next, updatedAtEpochMs = nowEpochMs)
        records[index] = updated
        writeRecords(records)
        updated
    }

    private fun readRecords(): List<Record> {
        if (!file.baseFile.exists()) return emptyList()
        val raw = file.openRead().use { it.readBytes() }
        val envelope = JSONObject(String(raw, Charsets.UTF_8))
        require(envelope.getInt("version") == STORAGE_VERSION) { "Unsupported task inbox version" }
        val nonce = Base64.decode(envelope.getString("nonce"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val clear = cipher.doFinal(ciphertext)
        val array = JSONArray(String(clear, Charsets.UTF_8))
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val payload = item.getString("payload")
            val record = Record(
                taskId = item.getString("taskId"),
                payload = payload,
                payloadSha256 = item.getString("payloadSha256"),
                state = State.valueOf(item.getString("state")),
                createdAtEpochMs = item.getLong("createdAtEpochMs"),
                updatedAtEpochMs = item.getLong("updatedAtEpochMs"),
            )
            require(record.payloadSha256 == sha256(record.payload)) { "Task inbox payload integrity mismatch" }
            record
        }
    }

    private fun writeRecords(records: List<Record>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject()
                .put("taskId", record.taskId)
                .put("payload", record.payload)
                .put("payloadSha256", record.payloadSha256)
                .put("state", record.state.name)
                .put("createdAtEpochMs", record.createdAtEpochMs)
                .put("updatedAtEpochMs", record.updatedAtEpochMs))
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("version", STORAGE_VERSION)
            .put("nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
            .toByteArray(Charsets.UTF_8)

        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(envelope)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            if (stream != null) file.failWrite(stream)
            throw error
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val FILE_NAME = "aura-v2-task-inbox.enc"
        private const val KEY_ALIAS = "aura.v2.task-inbox.aes"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val STORAGE_VERSION = 1
        private const val MAX_RECORDS = 500
        private const val MAX_TASK_ID_LENGTH = 160
        private const val MAX_PAYLOAD_BYTES = 64 * 1024
    }
}
