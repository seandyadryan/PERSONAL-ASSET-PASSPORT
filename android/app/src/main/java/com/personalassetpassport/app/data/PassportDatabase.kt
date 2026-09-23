package com.personalassetpassport.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PassportDao {
    @Query("SELECT * FROM assets WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAssets(): Flow<List<Asset>>

    @Query("SELECT * FROM reminders ORDER BY dueAt") fun observeReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM assets") suspend fun allAssets(): List<Asset>

    @Query("SELECT * FROM documents") suspend fun allDocuments(): List<Document>

    @Query("SELECT * FROM events") suspend fun allEvents(): List<AssetEvent>

    @Query("SELECT * FROM reminders") suspend fun allReminders(): List<Reminder>

    @Query("SELECT * FROM assets WHERE id = :id") suspend fun asset(id: String): Asset?

    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: String): Reminder?

    @Query("SELECT * FROM documents WHERE assetId = :id ORDER BY createdAt DESC")
    fun documents(id: String): Flow<List<Document>>

    @Query("SELECT * FROM events WHERE assetId = :id ORDER BY createdAt DESC")
    fun events(id: String): Flow<List<AssetEvent>>

    @Query("SELECT count(*) FROM assets WHERE deletedAt IS NULL") suspend fun activeCount(): Int

    @Upsert suspend fun save(asset: Asset)

    @Insert suspend fun document(document: Document)

    @Insert suspend fun event(event: AssetEvent)

    @Upsert suspend fun reminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id") suspend fun deleteReminder(id: String)

    @Query("DELETE FROM reminders WHERE assetId = :id") suspend fun deleteAssetReminders(id: String)

    @Query("DELETE FROM assets") suspend fun clearAssets()
}

@Database(
    entities = [Asset::class, Document::class, AssetEvent::class, Reminder::class],
    version = 1,
    exportSchema = true,
)
abstract class PassportDatabase : RoomDatabase() {
    abstract fun dao(): PassportDao

    companion object {
        fun open(context: Context) =
            Room.databaseBuilder(context, PassportDatabase::class.java, "passport-native.db")
                .build()
    }
}
