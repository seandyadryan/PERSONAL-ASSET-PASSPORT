package com.personalassetpassport.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personalassetpassport.app.backup.BackupService
import com.personalassetpassport.app.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var context: Context
    private lateinit var db: PassportDatabase
    private lateinit var repo: PassportRepository
    private lateinit var name: String

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        name = "test-${UUID.randomUUID()}.db"
        open()
    }

    private fun open() {
        db = Room.databaseBuilder(context, PassportDatabase::class.java, name).build()
        repo = PassportRepository(context, db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun inventoryAndCopiedReceiptSurviveDatabaseReopen() = runBlocking {
        val asset = Asset(name = "Camera", warrantyEnd = "2027-09-23")
        repo.save(asset)
        val source = File(context.cacheDir, "receipt.txt").apply { writeText("Original receipt") }
        repo.attach(asset.id, Uri.fromFile(source))
        source.delete()
        repo.addReminder(
            Reminder(
                assetId = asset.id,
                title = "Return",
                dueAt = System.currentTimeMillis() + 86400000,
            )
        )
        repo.save(asset.copy(name = "Camera updated"))
        db.close()
        open()
        assertEquals("Camera updated", repo.dao.asset(asset.id)?.name)
        assertEquals(
            "Original receipt",
            repo.file(repo.dao.allDocuments().single().file).readText(),
        )
        assertEquals(4, repo.dao.allEvents().size)
        assertEquals(1, repo.dao.allReminders().size)
        repo.delete(asset.id)
        assertEquals(0, repo.dao.activeCount())
        assertTrue(repo.dao.allReminders().isEmpty())
        assertNotNull(repo.dao.asset(asset.id)?.deletedAt)
    }

    @Test
    fun freeLimitAndPathTraversalAreEnforced() = runBlocking {
        repeat(FREE_LIMIT) { repo.save(Asset(name = "Asset $it")) }
        try {
            repo.save(Asset(name = "Eleventh"))
            fail("Limit not enforced")
        } catch (e: PassportException) {
            assertEquals("limit", e.key)
        }
        assertThrows(PassportException::class.java) { repo.file("../secret.txt") }
        assertThrows(PassportException::class.java) { repo.file("..\\secret.txt") }
        repo.delete(repo.dao.allAssets().first().id)
        repo.save(Asset(name = "Replacement"))
        assertEquals(FREE_LIMIT, repo.dao.activeCount())
    }

    @Test
    fun failedRestorePreservesInventoryAndSuccessfulRestoreIncludesFiles() = runBlocking {
        val asset = Asset(name = "Camera")
        repo.save(asset)
        val doc = File(context.cacheDir, "receipt.txt").apply { writeText("Private receipt") }
        repo.attach(asset.id, Uri.fromFile(doc))
        val file = File(context.cacheDir, "backup.pap")
        val backup = BackupService(repo)
        backup.export(Uri.fromFile(file), "correct password here".toCharArray())
        repo.save(Asset(name = "Keep me"))
        try {
            backup.restore(Uri.fromFile(file), "wrong password here".toCharArray())
            fail("Accepted wrong password")
        } catch (e: PassportException) {
            assertEquals("backup_invalid", e.key)
        }
        assertEquals(2, repo.dao.activeCount())
        backup.restore(Uri.fromFile(file), "correct password here".toCharArray())
        assertEquals(1, repo.dao.activeCount())
        assertEquals("Camera", repo.dao.allAssets().single().name)
        assertEquals("Private receipt", repo.file(repo.dao.allDocuments().single().file).readText())
    }
}
