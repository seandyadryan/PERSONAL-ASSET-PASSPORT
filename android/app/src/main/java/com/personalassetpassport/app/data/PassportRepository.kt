package com.personalassetpassport.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val MAX_FILE_BYTES = 10 * 1024 * 1024

fun InputStream.readBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        if (output.size() + count > limit) throw PassportException("file_size")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

fun detectMime(bytes: ByteArray): String? {
    if (
        bytes.size >= 3 &&
            bytes[0] == 0xff.toByte() &&
            bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte()
    )
        return "image/jpeg"
    if (
        bytes.size >= 8 &&
            bytes
                .take(8)
                .toByteArray()
                .contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
    )
        return "image/png"
    if (bytes.size >= 5 && bytes.take(5).toByteArray().decodeToString() == "%PDF-")
        return "application/pdf"
    if (bytes.isEmpty() || bytes.any { it in 0..8 || it in 14..31 }) return null
    return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            "text/plain"
        }
        .getOrNull()
}

class PassportRepository(val context: Context, val db: PassportDatabase) {
    val dao = db.dao()
    val mutex = Mutex()
    val files = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(name: String): File {
        if (!Regex("[a-fA-F0-9-]{36}\\.(jpg|png|pdf|txt)").matches(name))
            throw PassportException("invalid_file")
        return File(files, name)
    }

    suspend fun save(asset: Asset) = mutex.withLock {
        asset.validate()
        db.withTransaction {
            val old = dao.asset(asset.id)
            if (old?.deletedAt != null) throw PassportException("invalid_asset")
            if (old == null && dao.activeCount() >= FREE_LIMIT) throw PassportException("limit")
            dao.save(
                asset.copy(
                    createdAt = old?.createdAt ?: asset.createdAt,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            dao.event(
                AssetEvent(assetId = asset.id, kind = if (old == null) "created" else "updated")
            )
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        db.withTransaction {
            val asset = dao.asset(id) ?: throw PassportException("invalid_asset")
            dao.save(
                asset.copy(
                    deletedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            dao.deleteAssetReminders(id)
            dao.event(AssetEvent(assetId = id, kind = "deleted"))
        }
    }

    suspend fun addReminder(reminder: Reminder) = mutex.withLock {
        if (
            reminder.title.isBlank() ||
                reminder.title.length > 160 ||
                reminder.dueAt <= System.currentTimeMillis()
        )
            throw PassportException("future_time")
        db.withTransaction {
            if (
                dao.asset(reminder.assetId)?.deletedAt != null ||
                    dao.asset(reminder.assetId) == null
            )
                throw PassportException("invalid_asset")
            dao.reminder(reminder)
            dao.event(AssetEvent(assetId = reminder.assetId, kind = "reminder"))
        }
    }

    suspend fun importPhoto(uri: Uri): String =
        withContext(Dispatchers.IO) {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_FILE_BYTES) }
                    ?: throw PassportException("invalid_file")
            if (detectMime(bytes)?.startsWith("image/") != true)
                throw PassportException("invalid_file")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                throw PassportException("invalid_file")
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
            val bitmap =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: throw PassportException("invalid_file")
            val name = "${UUID.randomUUID()}.jpg"
            try {
                file(name).outputStream().use {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it))
                        throw PassportException("invalid_file")
                }
            } finally {
                bitmap.recycle()
            }
            name
        }

    suspend fun attach(assetId: String, uri: Uri) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_FILE_BYTES) }
                    ?: throw PassportException("invalid_file")
            val mime = detectMime(bytes) ?: throw PassportException("invalid_file")
            val ext =
                mapOf(
                        "image/jpeg" to "jpg",
                        "image/png" to "png",
                        "application/pdf" to "pdf",
                        "text/plain" to "txt",
                    )
                    .getValue(mime)
            val name = "${UUID.randomUUID()}.$ext"
            val displayName =
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                    ?.take(160) ?: "Document.$ext"
            file(name).writeBytes(bytes)
            try {
                db.withTransaction {
                    if (dao.asset(assetId)?.deletedAt != null || dao.asset(assetId) == null)
                        throw PassportException("invalid_asset")
                    dao.document(
                        Document(
                            assetId = assetId,
                            name = displayName,
                            file = name,
                            mime = mime,
                            size = bytes.size.toLong(),
                        )
                    )
                    dao.event(AssetEvent(assetId = assetId, kind = "document"))
                }
            } catch (e: Exception) {
                file(name).delete()
                throw e
            }
        }
    }
}
