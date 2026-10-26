package pl.pickpack.panel.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.QrVariantConfig

@Entity(tableName = "qr_variants")
data class QrVariantEntity(
    @PrimaryKey val id: String,
    val groupKey: String,
    val mask: Int,
    val payload: String?,
    val enabled: Boolean,
    val isDemo: Boolean,
    val fallbackImagePath: String?,
    val sourceLabel: String?,
    val templateIdHint: Int?,
) {
    fun toDomain(): QrVariantConfig {
        val groupId = GroupId.fromStorageKey(groupKey)
            ?: error("Unknown group: $groupKey")
        return QrVariantConfig(
            groupId = groupId,
            mask = mask,
            payload = payload,
            enabled = enabled,
            isDemo = isDemo,
            fallbackImagePath = fallbackImagePath,
            sourceLabel = sourceLabel,
            templateIdHint = templateIdHint,
        )
    }

    companion object {
        fun idOf(groupId: GroupId, mask: Int) = "${groupId.storageKey}:$mask"

        fun fromDomain(config: QrVariantConfig) = QrVariantEntity(
            id = idOf(config.groupId, config.mask),
            groupKey = config.groupId.storageKey,
            mask = config.mask,
            payload = config.payload,
            enabled = config.enabled,
            isDemo = config.isDemo,
            fallbackImagePath = config.fallbackImagePath,
            sourceLabel = config.sourceLabel,
            templateIdHint = config.templateIdHint,
        )
    }
}

@Entity(tableName = "group_meta")
data class GroupMetaEntity(
    @PrimaryKey val groupKey: String,
    val displayName: String,
    val visible: Boolean,
    val orderIndex: Int,
)

@Entity(tableName = "named_qr")
data class NamedQrEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val templateId: Int?,
    val payloadOverride: String?,
    val orderIndex: Int,
    val isDemo: Boolean,
) {
    fun toDomain() = pl.pickpack.panel.domain.NamedQrEntry(
        id = id,
        displayName = displayName,
        templateId = templateId,
        payloadOverride = payloadOverride,
        orderIndex = orderIndex,
        isDemo = isDemo,
    )

    companion object {
        fun fromDomain(e: pl.pickpack.panel.domain.NamedQrEntry) = NamedQrEntity(
            id = e.id,
            displayName = e.displayName,
            templateId = e.templateId,
            payloadOverride = e.payloadOverride,
            orderIndex = e.orderIndex,
            isDemo = e.isDemo,
        )
    }
}
