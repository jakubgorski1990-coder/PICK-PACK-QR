package pl.pickpack.panel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.pickpack.panel.data.dao.GroupMetaDao
import pl.pickpack.panel.data.dao.NamedQrDao
import pl.pickpack.panel.data.dao.QrVariantDao
import pl.pickpack.panel.data.entity.GroupMetaEntity
import pl.pickpack.panel.data.entity.NamedQrEntity
import pl.pickpack.panel.data.entity.QrVariantEntity
import pl.pickpack.panel.domain.GroupId

@Database(
    entities = [
        QrVariantEntity::class,
        GroupMetaEntity::class,
        NamedQrEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun qrVariantDao(): QrVariantDao
    abstract fun groupMetaDao(): GroupMetaDao
    abstract fun namedQrDao(): NamedQrDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS named_qr (
                        id TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        templateId INTEGER,
                        payloadOverride TEXT,
                        orderIndex INTEGER NOT NULL,
                        isDemo INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pick_pack.db",
                ).addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                        }
                    }).build().also { built ->
                        instance = built
                        CoroutineScope(Dispatchers.IO).launch {
                            seedGroupsIfNeeded(built)
                        }
                    }
            }

        private suspend fun seedGroupsIfNeeded(db: AppDatabase) {
            if (db.groupMetaDao().getAll().isNotEmpty()) return
            db.groupMetaDao().upsertAll(
                GroupId.entries.map {
                    GroupMetaEntity(
                        groupKey = it.storageKey,
                        displayName = it.defaultName,
                        visible = true,
                        orderIndex = it.ordinal,
                    )
                }
            )
        }
    }
}
