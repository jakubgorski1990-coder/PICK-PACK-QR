package pl.pickpack.panel.data.provider

import pl.pickpack.panel.data.dao.QrVariantDao
import pl.pickpack.panel.data.entity.QrVariantEntity
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.QrVariantConfig
import pl.pickpack.panel.domain.QrVariantKey

class LocalQrTemplateProvider(
    private val dao: QrVariantDao,
) : QrTemplateProvider {

    override suspend fun getVariant(key: QrVariantKey): QrVariantConfig? =
        dao.getById(QrVariantEntity.idOf(key.groupId, key.mask))?.toDomain()

    override suspend fun getVariantsForGroup(groupId: GroupId): List<QrVariantConfig> =
        dao.getForGroup(groupId.storageKey).map { it.toDomain() }

    override suspend fun getAllVariants(): List<QrVariantConfig> =
        dao.getAll().map { it.toDomain() }

    override suspend fun upsertVariant(config: QrVariantConfig) {
        dao.upsert(QrVariantEntity.fromDomain(config))
    }

    override suspend fun setEnabled(key: QrVariantKey, enabled: Boolean) {
        dao.setEnabled(QrVariantEntity.idOf(key.groupId, key.mask), enabled)
    }

    override suspend fun deleteVariant(key: QrVariantKey) {
        dao.delete(QrVariantEntity.idOf(key.groupId, key.mask))
    }
}
