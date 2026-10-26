package pl.pickpack.panel.data

import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.QrVariantConfig
import pl.pickpack.panel.domain.QrVariantKey

/**
 * Explicit DEMO payloads — never used as silent fallback for production gaps.
 */
object DemoContent {
    const val BANNER = "DEMO — nie jest kodem BaseLinkera"

    fun allVariants(): List<QrVariantConfig> =
        QrVariantKey.allForGroup(GroupId.PICK_PACK_1).map { key ->
            QrVariantConfig(
                groupId = key.groupId,
                mask = key.mask,
                payload = demoPayload(key),
                enabled = true,
                isDemo = true,
                fallbackImagePath = null,
                sourceLabel = BANNER,
                templateIdHint = if (key.mask == 7) GroupId.PICK_PACK_1.defaultTemplateId else null,
            )
        } + QrVariantKey.allForGroup(GroupId.PICK_PACK_2).map { key ->
            QrVariantConfig(
                groupId = key.groupId,
                mask = key.mask,
                payload = demoPayload(key),
                enabled = true,
                isDemo = true,
                fallbackImagePath = null,
                sourceLabel = BANNER,
                templateIdHint = if (key.mask == 7) GroupId.PICK_PACK_2.defaultTemplateId else null,
            )
        }

    fun demoPayload(key: QrVariantKey): String =
        "DEMO|${key.groupId.storageKey}|mask=${key.mask}|${key.label()}|NOT_BASELINKER"
}
