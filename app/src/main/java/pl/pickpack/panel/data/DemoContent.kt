package pl.pickpack.panel.data

import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.NamedQrEntry
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

    /** Only when DEMO mode is on — never written as production named entries. */
    fun namedEntries(): List<NamedQrEntry> = listOf(
        NamedQrEntry("demo_n1", "Anna", 18472, orderIndex = 0, isDemo = true),
        NamedQrEntry("demo_n2", "Bartek", 18173, orderIndex = 1, isDemo = true),
        NamedQrEntry("demo_n3", "Celina", null, payloadOverride = "DEMO|named|celina", orderIndex = 2, isDemo = true),
    )
}
