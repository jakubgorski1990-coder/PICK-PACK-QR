package pl.pickpack.panel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.pickpack.panel.data.dao.GroupMetaDao
import pl.pickpack.panel.data.dao.QrVariantDao
import pl.pickpack.panel.data.entity.GroupMetaEntity
import pl.pickpack.panel.domain.GroupId

@Database(
    entities = [
        pl.pickpack.panel.data.entity.QrVariantEntity::class,
        GroupMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun qrVariantDao(): QrVariantDao
    abstract fun groupMetaDao(): GroupMetaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pick_pack.db",
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed group meta asynchronously after open
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
