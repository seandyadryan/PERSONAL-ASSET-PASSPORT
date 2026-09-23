package com.personalassetpassport.app

import com.personalassetpassport.app.backup.BackupCrypto
import com.personalassetpassport.app.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test
    fun calendarDeadlinesIncludeTheFinalDay() {
        val today = LocalDate.of(2026, 9, 23)
        assertEquals(WarrantyStatus.UNKNOWN, warrantyStatus(null, today))
        assertEquals(WarrantyStatus.EXPIRED, warrantyStatus("2026-09-22", today))
        assertEquals(WarrantyStatus.EXPIRING, warrantyStatus("2026-09-23", today))
        assertEquals(WarrantyStatus.EXPIRING, warrantyStatus("2026-10-23", today))
        assertEquals(WarrantyStatus.ACTIVE, warrantyStatus("2026-10-24", today))
        assertEquals(1L, daysUntil("2026-09-24", today))
    }

    @Test
    fun currencyParsingIsExactAndLocaleAware() {
        assertEquals(129999L, parseMinor("1,299.99", false))
        assertEquals(129999L, parseMinor("1.299,99", true))
        assertEquals(10L, parseMinor("0.10", false))
        assertNull(parseMinor("1,2", false))
        assertNull(parseMinor("-5", false))
        assertNull(parseMinor("1.001", false))
    }

    @Test
    fun invalidFieldsAndDatesAreRejected() {
        assertThrows(PassportException::class.java) { Asset(name = "").validate() }
        assertThrows(PassportException::class.java) {
            Asset(name = "Camera", purchaseDate = "2026-02-30").validate()
        }
        assertThrows(PassportException::class.java) {
            Asset(name = "Camera", purchaseDate = "2026-09-23", returnEnd = "2026-09-22").validate()
        }
        assertThrows(PassportException::class.java) {
            Asset(name = "Camera", priceMinor = -1).validate()
        }
        Asset(name = "Camera", purchaseDate = "2028-02-29").validate()
    }

    @Test
    fun backupUsesAuthenticatedEncryption() {
        val plain = "private receipt".toByteArray()
        val password = "twelve plus characters".toCharArray()
        val encrypted = BackupCrypto.encrypt(plain, password)
        assertFalse(encrypted.decodeToString().contains("private receipt"))
        assertArrayEquals(plain, BackupCrypto.decrypt(encrypted, password))
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypt(encrypted, "incorrect password".toCharArray())
        }
        val tampered =
            encrypted.clone().apply { this[size - 4] = (this[size - 4].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(tampered, password) }
        assertThrows(PassportException::class.java) {
            BackupCrypto.encrypt(plain, "short".toCharArray())
        }
    }

    @Test
    fun mimeValidationRejectsBinaryAndMalformedUtf8() {
        assertEquals("text/plain", detectMime("Receipt Rp 1.000".toByteArray()))
        assertEquals("application/pdf", detectMime("%PDF-1.7".toByteArray()))
        assertNull(detectMime(byteArrayOf(0, 1, 2)))
        assertNull(detectMime(byteArrayOf(0xff.toByte(), 0xfe.toByte())))
    }
}
