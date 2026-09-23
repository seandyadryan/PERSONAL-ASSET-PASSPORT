package com.personalassetpassport.app.backup

import android.net.Uri
import androidx.room.withTransaction
import com.personalassetpassport.app.data.*
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val assets: List<Asset>,
    val documents: List<Document>,
    val events: List<AssetEvent>,
    val reminders: List<Reminder>,
    val files: Map<String, String>,
)

@Serializable
data class BackupEnvelope(
    val format: String = "PAP-KOTLIN",
    val version: Int = 1,
    val salt: String,
    val nonce: String,
    val data: String,
)

object BackupCrypto {
    const val ITERATIONS = 600_000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES",
            )
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        if (password.size < 12) throw PassportException("password_short")
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD("PAP-KOTLIN:1".toByteArray())
        val b64 = Base64.getEncoder()
        return Json.encodeToString(
                BackupEnvelope(
                    salt = b64.encodeToString(salt),
                    nonce = b64.encodeToString(nonce),
                    data = b64.encodeToString(cipher.doFinal(plain)),
                )
            )
            .toByteArray()
    }

    fun decrypt(encrypted: ByteArray, password: CharArray): ByteArray {
        val envelope = Json.decodeFromString<BackupEnvelope>(encrypted.decodeToString())
        require(envelope.format == "PAP-KOTLIN" && envelope.version == 1)
        val b64 = Base64.getDecoder()
        val salt = b64.decode(envelope.salt)
        val nonce = b64.decode(envelope.nonce)
        require(salt.size == 16 && nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD("PAP-KOTLIN:1".toByteArray())
        return cipher.doFinal(b64.decode(envelope.data))
    }
}

class BackupService(private val repo: PassportRepository) {
    companion object {
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
        const val MAX_ATTACHMENT_BYTES = 32 * 1024 * 1024
    }

    suspend fun export(uri: Uri, password: CharArray) =
        withContext(Dispatchers.IO) {
            try {
                val bytes =
                    repo.mutex.withLock {
                        val snapshot =
                            repo.db.withTransaction {
                                BackupPayload(
                                    assets = repo.dao.allAssets(),
                                    documents = repo.dao.allDocuments(),
                                    events = repo.dao.allEvents(),
                                    reminders = repo.dao.allReminders(),
                                    files = emptyMap(),
                                )
                            }
                        val names =
                            (snapshot.assets.mapNotNull { it.photo } +
                                    snapshot.documents.map { it.file })
                                .toSet()
                        var total = 0L
                        val files = names.associateWith { name ->
                            val file = repo.file(name)
                            total += file.length()
                            if (total > MAX_ATTACHMENT_BYTES) throw PassportException("backup_size")
                            Base64.getEncoder().encodeToString(file.readBytes())
                        }
                        val payload =
                            Json.encodeToString(snapshot.copy(files = files)).toByteArray()
                        if (payload.size > MAX_BACKUP_BYTES) throw PassportException("backup_size")
                        BackupCrypto.encrypt(payload, password)
                    }
                repo.context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: throw PassportException("invalid_file")
            } finally {
                password.fill('\u0000')
            }
        }

    suspend fun restore(uri: Uri, password: CharArray) =
        withContext(Dispatchers.IO) {
            val newFiles = mutableListOf<String>()
            try {
                repo.mutex.withLock {
                    val bytes =
                        repo.context.contentResolver.openInputStream(uri)?.use {
                            it.readBounded(MAX_BACKUP_BYTES * 2)
                        } ?: throw PassportException("invalid_file")
                    val plain = BackupCrypto.decrypt(bytes, password)
                    require(plain.size <= MAX_BACKUP_BYTES)
                    val backup = Json.decodeFromString<BackupPayload>(plain.decodeToString())
                    require(
                        backup.version == 1 &&
                            backup.assets.size <= 10_000 &&
                            backup.events.size <= 100_000 &&
                            backup.documents.size <= 10_000 &&
                            backup.reminders.size <= 10_000
                    )
                    if (backup.assets.count { it.deletedAt == null } > FREE_LIMIT)
                        throw PassportException("limit")
                    backup.assets.forEach(Asset::validate)
                    val ids = backup.assets.map { it.id }.toSet()
                    require(ids.size == backup.assets.size)
                    listOf(
                            backup.documents.map { it.id },
                            backup.events.map { it.id },
                            backup.reminders.map { it.id },
                        )
                        .forEach { keys ->
                            require(keys.toSet().size == keys.size)
                            keys.forEach(UUID::fromString)
                        }
                    require(backup.documents.all { it.assetId in ids && it.name.length <= 160 })
                    require(
                        backup.events.all {
                            it.assetId in ids &&
                                it.kind in
                                    listOf("created", "updated", "deleted", "document", "reminder")
                        }
                    )
                    require(
                        backup.reminders.all {
                            it.assetId in ids &&
                                it.title.isNotBlank() &&
                                it.title.length <= 160 &&
                                it.dueAt > 0
                        }
                    )
                    val mapping =
                        backup.files.mapValues { (old, b64) ->
                            repo.file(old)
                            val content = Base64.getDecoder().decode(b64)
                            val expectedMime =
                                mapOf(
                                        "jpg" to "image/jpeg",
                                        "png" to "image/png",
                                        "pdf" to "application/pdf",
                                        "txt" to "text/plain",
                                    )
                                    .getValue(old.substringAfterLast('.'))
                            require(
                                content.size <= MAX_FILE_BYTES &&
                                    detectMime(content) == expectedMime
                            )
                            val name = "${UUID.randomUUID()}.${old.substringAfterLast('.')}"
                            newFiles += name
                            repo.file(name).writeBytes(content)
                            name
                        }
                    require(backup.assets.mapNotNull { it.photo }.all { mapping.containsKey(it) })
                    require(
                        backup.documents.all {
                            mapping.containsKey(it.file) &&
                                repo.file(mapping.getValue(it.file)).length() == it.size &&
                                detectMime(repo.file(mapping.getValue(it.file)).readBytes()) ==
                                    it.mime
                        }
                    )
                    repo.db.withTransaction {
                        repo.dao.clearAssets()
                        backup.assets.forEach {
                            repo.dao.save(it.copy(photo = it.photo?.let(mapping::getValue)))
                        }
                        backup.documents.forEach {
                            repo.dao.document(it.copy(file = mapping.getValue(it.file)))
                        }
                        backup.events.forEach { repo.dao.event(it) }
                        backup.reminders.forEach { repo.dao.reminder(it.copy(delivered = false)) }
                    }
                }
            } catch (e: Exception) {
                newFiles.forEach { repo.file(it).delete() }
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is PassportException && e.key == "limit") throw e
                throw PassportException("backup_invalid")
            } finally {
                password.fill('\u0000')
            }
        }
}
