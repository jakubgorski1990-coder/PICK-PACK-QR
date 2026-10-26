package pl.pickpack.panel.data.provider

import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.QrVariantConfig
import pl.pickpack.panel.domain.QrVariantKey

/**
 * Abstraction for QR template content (Etap 1: local Room-backed impl).
 * Etap 2 may add BaseLinker-aware sources without changing UI contracts.
 */
interface QrTemplateProvider {
    suspend fun getVariant(key: QrVariantKey): QrVariantConfig?
    suspend fun getVariantsForGroup(groupId: GroupId): List<QrVariantConfig>
    suspend fun getAllVariants(): List<QrVariantConfig>
    suspend fun upsertVariant(config: QrVariantConfig)
    suspend fun setEnabled(key: QrVariantKey, enabled: Boolean)
    suspend fun deleteVariant(key: QrVariantKey)
}
