package com.personalassetpassport.app.data

import androidx.room.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.serialization.Serializable

const val FREE_LIMIT = 10
val CATEGORIES =
    listOf(
        "Electronics",
        "Appliances",
        "Furniture",
        "Vehicles",
        "Photography",
        "Jewelry",
        "Tools",
        "Sports",
        "Other",
    )
val CURRENCIES = listOf("USD", "EUR", "GBP", "AUD", "CAD", "SGD", "IDR", "JPY")

class PassportException(val key: String) : Exception(key)

@Serializable
@Entity(tableName = "assets", indices = [Index("deletedAt", "createdAt")])
data class Asset(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "Electronics",
    val brand: String = "",
    val model: String = "",
    val serial: String = "",
    val vendor: String = "",
    val priceMinor: Long = 0,
    val currency: String = "USD",
    val notes: String = "",
    val purchaseDate: String? = null,
    val warrantyEnd: String? = null,
    val returnEnd: String? = null,
    val photo: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
) {
    fun validate() {
        if (
            name.isBlank() ||
                name.length > 160 ||
                category !in CATEGORIES ||
                currency !in CURRENCIES ||
                priceMinor !in 0..99_999_999_999_900L ||
                notes.length > 4000 ||
                listOf(brand, model, serial, vendor).any { it.length > 200 }
        )
            throw PassportException("invalid_asset")
        try {
            UUID.fromString(id)
            val dates = listOfNotNull(purchaseDate, warrantyEnd, returnEnd)
            dates.forEach {
                require(it.length == 10)
                val d = LocalDate.parse(it)
                require(d.year in 1900..2200)
            }
            purchaseDate?.let { start ->
                require(listOfNotNull(warrantyEnd, returnEnd).all { it >= start })
            }
        } catch (_: IllegalArgumentException) {
            throw PassportException("invalid_date")
        } catch (_: java.time.DateTimeException) {
            throw PassportException("invalid_date")
        }
    }

    fun matches(query: String) =
        listOf(name, category, brand, model, serial, vendor, notes).any { it.contains(query, true) }
}

@Serializable
@Entity(
    tableName = "documents",
    foreignKeys =
        [
            ForeignKey(
                entity = Asset::class,
                parentColumns = ["id"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("assetId")],
)
data class Document(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val assetId: String,
    val name: String,
    val file: String,
    val mime: String,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "events",
    foreignKeys =
        [
            ForeignKey(
                entity = Asset::class,
                parentColumns = ["id"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("assetId")],
)
data class AssetEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val assetId: String,
    val kind: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "reminders",
    foreignKeys =
        [
            ForeignKey(
                entity = Asset::class,
                parentColumns = ["id"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("assetId")],
)
data class Reminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val assetId: String,
    val title: String,
    val dueAt: Long,
    val delivered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class WarrantyStatus {
    UNKNOWN,
    EXPIRED,
    EXPIRING,
    ACTIVE,
}

fun daysUntil(date: String?, today: LocalDate = LocalDate.now()): Long? = date?.let {
    ChronoUnit.DAYS.between(today, LocalDate.parse(it))
}

fun warrantyStatus(end: String?, today: LocalDate = LocalDate.now()): WarrantyStatus =
    when (val days = daysUntil(end, today)) {
        null -> WarrantyStatus.UNKNOWN
        in Long.MIN_VALUE..-1L -> WarrantyStatus.EXPIRED
        in 0L..30L -> WarrantyStatus.EXPIRING
        else -> WarrantyStatus.ACTIVE
    }

fun parseMinor(text: String, indonesian: Boolean): Long? {
    if (text.isBlank()) return 0
    val pattern =
        if (indonesian) Regex("^(\\d+|\\d{1,3}(\\.\\d{3})+)(,\\d{1,2})?$")
        else Regex("^(\\d+|\\d{1,3}(,\\d{3})+)(\\.\\d{1,2})?$")
    if (!pattern.matches(text.trim())) return null
    return runCatching {
            BigDecimal(
                    if (indonesian) text.trim().replace(".", "").replace(',', '.')
                    else text.trim().replace(",", "")
                )
                .movePointRight(2)
                .longValueExact()
                .takeIf { it in 0..99_999_999_999_900L }
        }
        .getOrNull()
}
