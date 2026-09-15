package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.database.dao.VaultDao
import com.example.data.database.dao.VaultItemDao
import com.example.data.database.dao.VaultTransferDao
import com.example.data.database.entity.VaultEntity
import com.example.data.database.entity.VaultItemEntity
import com.example.data.database.entity.VaultTransferEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        VaultEntity::class,
        VaultItemEntity::class,
        VaultTransferEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun vaultTransferDao(): VaultTransferDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_items ADD COLUMN originalRelativePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vault_transfers ADD COLUMN originalRelativePath TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_items ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, scope).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, scope: CoroutineScope): VaultDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                "private_gallery_vault.db"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    scope.launch {
                        val database = getInstance(context, scope)
                        val vaultDao = database.vaultDao()
                        if (vaultDao.getVaultCount() == 0) {
                            vaultDao.insertVault(
                                VaultEntity(
                                    displayName = "Personal",
                                    iconName = "person",
                                    isDefault = true
                                )
                            )
                            vaultDao.insertVault(
                                VaultEntity(
                                    displayName = "Documents",
                                    iconName = "description"
                                )
                            )
                            vaultDao.insertVault(
                                VaultEntity(
                                    displayName = "Private",
                                    iconName = "lock"
                                )
                            )
                            vaultDao.insertVault(
                                VaultEntity(
                                    displayName = "Work",
                                    iconName = "work"
                                )
                            )
                        }
                    }
                }
            }).build()
        }
    }
}
