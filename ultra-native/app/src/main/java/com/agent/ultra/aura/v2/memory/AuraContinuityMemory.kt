package com.agent.ultra.aura.v2.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class MemoryKind {
    PROFILE,
    PREFERENCE,
    FACT,
    DECISION,
    TASK,
    EPISODE,
    SUMMARY,
}

enum class MemoryLifecycle {
    ACTIVE,
    SUPERSEDED,
    ARCHIVED,
}

/**
 * A durable, source-aware memory owned by this AURA installation.
 * Keep personal facts separate from transient conversation turns.
 */
data class MemoryRecord(
    val id: String,
    val kind: MemoryKind,
    val title: String,
    val content: String,
    val source: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val confidence: Double = 1.0,
    val tags: List<String> = emptyList(),
    val lifecycle: MemoryLifecycle = MemoryLifecycle.ACTIVE,
    val supersedesId: String? = null,
)

/**
 * Encrypted, offline continuity store for one Android app installation.
 *
 * Data is stored in noBackupFilesDir and encrypted with a non-exportable
 * Android Keystore AES-GCM key. There is deliberately no plaintext fallback.
 * A missing/lost key or corrupt ciphertext raises an error instead of silently
 * returning an empty memory.
 *
 * Search is currently deterministic lexical retrieval, not semantic/vector
 * retrieval. The storage contract is kept independent from the ChatGPT bridge.
 */
class AuraContinuityMemory(context: Context) {
    private val appContext = context.applicationContext
    private val storeFile = File(appContext.noBackupFilesDir, STORE_FILE)
    private val tempFile = File(appContext.noBackupFilesDir, "$STORE_FILE.tmp")
    private val lock = Any()

    /** Insert or update one memory without duplicating its stable id. */
    fun remember(record: MemoryRecord): MemoryRecord = synchronized(lock) {
        validate(record)
        val current = readAll()
        val existing = current.firstOrNull { it.id == record.id }
        if (existing == null && current.size >= MAX_RECORDS) {
            throw IllegalStateException("AURA memory capacity reached; archive or remove records first")
        }

        val now = System.currentTimeMillis()
        val stored = record.copy(
            createdAtEpochMs = existing?.createdAtEpochMs ?: record.createdAtEpochMs,
            updatedAtEpochMs = now,
        )
        val next = if (existing == null) current + stored
        else current.map { if (it.id == stored.id) stored else it }
        writeAll(next)
        stored
    }

    /** Return recent active memories; archived/superseded items are excluded by default. */
    fun recent(limit: Int = 50, includeInactive: Boolean = false): List<MemoryRecord> =
        synchronized(lock) {
            require(limit in 1..MAX_RESULTS) { "limit must be between 1 and $MAX_RESULTS" }
            readAll()
                .asSequence()
                .filter { includeInactive || it.lifecycle == MemoryLifecycle.ACTIVE }
                .sortedByDescending { it.updatedAtEpochMs }
                .take(limit)
                .toList()
        }

    /**
     * Offline lexical recall. Results are ranked by title, tag and content
     * matches; no data is sent to a remote service.
     */
    fun recall(query: String, limit: Int = 20): List<MemoryRecord> = synchronized(lock) {
        require(limit in 1..MAX_RESULTS) { "limit must be between 1 and $MAX_RESULTS" }
        val terms = query.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 }
            .distinct()
        if (terms.isEmpty()) return@synchronized emptyList()

        readAll()
            .asSequence()
            .filter { it.lifecycle == MemoryLifecycle.ACTIVE }
            .mapNotNull { record ->
                val title = record.title.lowercase(Locale.ROOT)
                val body = record.content.lowercase(Locale.ROOT)
                val tags = record.tags.joinToString(" ").lowercase(Locale.ROOT)
                val score = terms.sumOf { term ->
                    (if (title.contains(term)) 4 else 0) +
                        (if (tags.contains(term)) 3 else 0) +
                        (if (body.contains(term)) 1 else 0)
                }
                if (score > 0) record to score else null
            }
            .sortedWith(
                compareByDescending<Pair<MemoryRecord, Int>> { it.second }
                    .thenByDescending { it.first.updatedAtEpochMs }
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun setLifecycle(id: String, lifecycle: MemoryLifecycle): Boolean = synchronized(lock) {
        val current = readAll()
        if (current.none { it.id == id }) return@synchronized false
        writeAll(current.map {
            if (it.id == id) it.copy(
                lifecycle = lifecycle,
                updatedAtEpochMs = System.currentTimeMillis(),
            ) else it
        })
        true
    }

    /** Explicit single-record deletion. There is intentionally no bulk-clear API. */
    fun forget(id: String): Boolean = synchronized(lock) {
        val current = readAll()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return@synchronized false
        writeAll(next)
        true
    }

    private fun validate(record: MemoryRecord) {
        require(record.id.isNotBlank()) { "Memory id is required" }
        require(record.title.isNotBlank()) { "Memory title is required" }
        require(record.content.isNotBlank()) { "Memory content is required" }
        require(record.source.isNotBlank()) { "Memory source/provenance is required" }
        require(record.title.length <= MAX_TITLE_CHARS) { "Memory title is too long" }
        require(record.content.length <= MAX_CONTENT_CHARS) { "Memory content is too long" }
        require(record.confidence in 0.0..1.0) { "Confidence must be between 0 and 1" }
        require(record.tags.size <= MAX_TAGS) { "Too many memory tags" }
    }

    private fun readAll(): List<MemoryRecord> {
        if (!storeFile.exists()) return emptyList()
        val encrypted = storeFile.readBytes()
        if (encrypted.size > MAX_ENCRYPTED_BYTES) {
            throw IllegalStateException("AURA memory file exceeds the safety limit")
        }
        val root = JSONObject(String(decrypt(encrypted), Charsets.UTF_8))
        val schema = root.optInt("schemaVersion", -1)
        if (schema != SCHEMA_VERSION) {
            throw IllegalStateException("Unsupported AURA memory schema: $schema")
        }
        val array = root.optJSONArray("records") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                add(fromJson(array.getJSONObject(index)))
            }
        }
    }

    private fun writeAll(records: List<MemoryRecord>) {
        require(records.size <= MAX_RECORDS) { "Too many AURA memory records" }
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("records", JSONArray().apply {
                records.forEach { put(toJson(it)) }
            })
        val plaintext = root.toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "AURA memory store exceeds the safety limit" }
        val encrypted = encrypt(plaintext)

        tempFile.parentFile?.mkdirs()
        FileOutputStream(tempFile).use { output ->
            output.write(encrypted)
            output.fd.sync()
        }
        try {
            Files.move(
                tempFile.toPath(),
                storeFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), storeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(payload)
            if (buffer.remaining() < Int.SIZE_BYTES) {
                throw IllegalStateException("Invalid AURA memory envelope")
            }
            val ivSize = buffer.int
            if (ivSize !in MIN_GCM_IV_BYTES..MAX_GCM_IV_BYTES ||
                buffer.remaining() <= ivSize
            ) {
                throw IllegalStateException("Invalid AURA memory IV")
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
                "AURA local memory could not be decrypted; data was not discarded",
                error,
            )
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

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

    private fun toJson(record: MemoryRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("kind", record.kind.name)
        .put("title", record.title)
        .put("content", record.content)
        .put("source", record.source)
        .put("createdAtEpochMs", record.createdAtEpochMs)
        .put("updatedAtEpochMs", record.updatedAtEpochMs)
        .put("confidence", record.confidence)
        .put("tags", JSONArray(record.tags))
        .put("lifecycle", record.lifecycle.name)
        .put("supersedesId", record.supersedesId)

    private fun fromJson(json: JSONObject): MemoryRecord {
        val tagsJson = json.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (index in 0 until tagsJson.length()) add(tagsJson.getString(index))
        }
        return MemoryRecord(
            id = json.getString("id"),
            kind = MemoryKind.valueOf(json.getString("kind")),
            title = json.getString("title"),
            content = json.getString("content"),
            source = json.getString("source"),
            createdAtEpochMs = json.getLong("createdAtEpochMs"),
            updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
            confidence = json.getDouble("confidence"),
            tags = tags,
            lifecycle = MemoryLifecycle.valueOf(json.getString("lifecycle")),
            supersedesId = json.optString("supersedesId").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    companion object {
        private const val STORE_FILE = "aura-continuity-memory.enc"
        private const val KEY_ALIAS = "aura.continuity.memory.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SCHEMA_VERSION = 1
        private const val GCM_TAG_BITS = 128
        private const val MIN_GCM_IV_BYTES = 12
        private const val MAX_GCM_IV_BYTES = 16
        private const val MAX_RECORDS = 10_000
        private const val MAX_RESULTS = 200
        private const val MAX_TITLE_CHARS = 300
        private const val MAX_CONTENT_CHARS = 20_000
        private const val MAX_TAGS = 32
        private const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
        private const val MAX_ENCRYPTED_BYTES = 9 * 1024 * 1024
    }
}
