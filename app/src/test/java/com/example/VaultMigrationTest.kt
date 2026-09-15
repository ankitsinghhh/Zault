package com.example

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.VaultDatabase
import com.example.data.database.entity.VaultEntity
import com.example.data.database.entity.VaultItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VaultMigrationTest {
    @Test fun `old databases upgrade without losing vault records`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (version in 1..2) {
            val name = "migration-$version.db"
            var db = Room.databaseBuilder(context, VaultDatabase::class.java, name).build()
            val folder = db.vaultDao().insertVault(VaultEntity(displayName = "Keep me"))
            val id = db.vaultItemDao().insertItem(VaultItemEntity(vaultId = folder,
                encryptedFileIdentifier = "encrypted-keep", originalFileName = "keep.jpg",
                mimeType = "image/jpeg", mediaType = 1, dateTaken = 123, originalSize = 456))
            db.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                fun removeColumns(table: String, removed: Set<String>) {
                    val create = old.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
                        it.moveToFirst(); it.getString(0)
                    }
                    val indexes = mutableListOf<String>()
                    old.rawQuery("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name=? AND sql IS NOT NULL", arrayOf(table)).use {
                        while (it.moveToNext()) indexes.add(it.getString(0))
                    }
                    val columns = mutableListOf<String>()
                    old.rawQuery("PRAGMA table_info(`$table`)", null).use {
                        while (it.moveToNext()) {
                            val column = it.getString(1)
                            if (column !in removed) columns.add("`$column`")
                        }
                    }
                    var replacement = create.replaceFirst(Regex("CREATE TABLE.*?\\("), "CREATE TABLE `${table}_old` (")
                    for (column in removed) {
                        replacement = replacement.replace(Regex("`$column` (?:TEXT|INTEGER) NOT NULL,\\s*"), "")
                    }
                    old.execSQL(replacement)
                    val names = columns.joinToString(",")
                    old.execSQL("INSERT INTO `${table}_old` ($names) SELECT $names FROM `$table`")
                    old.execSQL("DROP TABLE `$table`")
                    old.execSQL("ALTER TABLE `${table}_old` RENAME TO `$table`")
                    indexes.forEach { old.execSQL(it) }
                }
                removeColumns("vault_items", setOf("isDeleted", "deletedAt") +
                    if (version == 1) setOf("originalRelativePath") else emptySet())
                if (version == 1) removeColumns("vault_transfers", setOf("originalRelativePath"))
                old.execSQL("DELETE FROM room_master_table")
                old.version = version
            }
            db = Room.databaseBuilder(context, VaultDatabase::class.java, name)
                .addMigrations(VaultDatabase.MIGRATION_1_2, VaultDatabase.MIGRATION_2_3).build()
            try {
                val item = db.vaultItemDao().getItemById(id)!!
                assertEquals("encrypted-keep", item.encryptedFileIdentifier)
                assertEquals(456L, item.originalSize)
                assertFalse(item.isDeleted)
                assertEquals(0L, item.deletedAt)
                assertEquals("", item.originalRelativePath)
            } finally { db.close() }
        }
    }
}
