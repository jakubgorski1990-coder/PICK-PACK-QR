package pl.pickpack.panel.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.pickpack.panel.data.entity.GroupMetaEntity
import pl.pickpack.panel.data.entity.QrVariantEntity

@Dao
interface QrVariantDao {
    @Query("SELECT * FROM qr_variants")
    fun observeAll(): Flow<List<QrVariantEntity>>

    @Query("SELECT * FROM qr_variants")
    suspend fun getAll(): List<QrVariantEntity>

    @Query("SELECT * FROM qr_variants WHERE groupKey = :groupKey")
    suspend fun getForGroup(groupKey: String): List<QrVariantEntity>

    @Query("SELECT * FROM qr_variants WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): QrVariantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QrVariantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<QrVariantEntity>)

    @Query("UPDATE qr_variants SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM qr_variants WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM qr_variants WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface GroupMetaDao {
    @Query("SELECT * FROM group_meta ORDER BY orderIndex ASC")
    fun observeAll(): Flow<List<GroupMetaEntity>>

    @Query("SELECT * FROM group_meta ORDER BY orderIndex ASC")
    suspend fun getAll(): List<GroupMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GroupMetaEntity>)
}
