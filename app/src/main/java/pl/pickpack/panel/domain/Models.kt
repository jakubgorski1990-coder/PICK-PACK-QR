package pl.pickpack.panel.domain

/**
 * Identifiers for warehouse pick/pack groups.
 * Model is ready for more than two groups in later stages.
 */
enum class GroupId(val storageKey: String, val defaultName: String, val defaultTemplateId: Int) {
    PICK_PACK_1("pick_pack_1", "Pick / Pack 1", 18472),
    PICK_PACK_2("pick_pack_2", "Pick / Pack 2", 18173);

    companion object {
        fun fromStorageKey(key: String): GroupId? = entries.find { it.storageKey == key }
    }
}

data class CourierDef(
    val id: String,
    val groupId: GroupId,
    val defaultName: String,
    /** Bit index 0..2 within the group for mask-based variant keys. */
    val bitIndex: Int,
)

object DefaultCouriers {
    val ALL: List<CourierDef> = listOf(
        CourierDef("orlen", GroupId.PICK_PACK_1, "ORLEN", 0),
        CourierDef("dhl", GroupId.PICK_PACK_1, "DHL", 1),
        CourierDef("olza", GroupId.PICK_PACK_1, "OLZA (PPL/GLS)", 2),
        CourierDef("inpost", GroupId.PICK_PACK_2, "InPost", 0),
        CourierDef("dpd", GroupId.PICK_PACK_2, "DPD", 1),
        CourierDef("one_kurier", GroupId.PICK_PACK_2, "One Kurier", 2),
    )

    fun forGroup(groupId: GroupId): List<CourierDef> = ALL.filter { it.groupId == groupId }
}

/**
 * Runtime courier state — active means still waiting for pickup.
 * Independent from QR-variant enable and group visibility.
 */
data class CourierState(
    val id: String,
    val groupId: GroupId,
    val displayName: String,
    val isActive: Boolean = true,
)

/**
 * One of 7 non-empty courier combinations for a group.
 * Key is a sorted set of courier IDs (or equivalent bitmask 1..7).
 */
data class QrVariantKey(
    val groupId: GroupId,
    /** Bitmask 1..7 where bits match [CourierDef.bitIndex]. */
    val mask: Int,
) {
    init {
        require(mask in 1..7) { "Mask must be 1..7, got $mask" }
    }

    fun courierIds(defs: List<CourierDef> = DefaultCouriers.forGroup(groupId)): Set<String> =
        defs.filter { ((1 shl it.bitIndex) and mask) != 0 }.map { it.id }.toSet()

    fun label(defs: List<CourierDef> = DefaultCouriers.forGroup(groupId)): String =
        defs.filter { ((1 shl it.bitIndex) and mask) != 0 }
            .sortedBy { it.bitIndex }
            .joinToString(" + ") { it.defaultName }

    companion object {
        fun allForGroup(groupId: GroupId): List<QrVariantKey> =
            (1..7).map { QrVariantKey(groupId, it) }

        fun fromActiveCouriers(groupId: GroupId, activeIds: Set<String>): QrVariantKey? {
            if (activeIds.isEmpty()) return null
            val defs = DefaultCouriers.forGroup(groupId)
            var mask = 0
            for (def in defs) {
                if (def.id in activeIds) mask = mask or (1 shl def.bitIndex)
            }
            // Exact match only — ignore unknown IDs; if none of group matched → null
            return if (mask == 0) null else QrVariantKey(groupId, mask)
        }
    }
}

sealed class QrDisplayState {
    data class Ready(
        val variantKey: QrVariantKey,
        val payload: String,
        val isDemo: Boolean,
        /** Original image path when payload QR generation is unavailable; null = render from payload. */
        val fallbackImagePath: String? = null,
        val templateIdHint: Int? = null,
    ) : QrDisplayState()

    data class AllPickedUp(val groupId: GroupId) : QrDisplayState()

    data class Missing(
        val groupId: GroupId,
        val variantKey: QrVariantKey,
        val reason: MissingReason,
    ) : QrDisplayState()
}

enum class MissingReason {
    NOT_CONFIGURED,
    DISABLED,
    CORRUPT,
}

data class QrVariantConfig(
    val groupId: GroupId,
    val mask: Int,
    val payload: String?,
    val enabled: Boolean,
    val isDemo: Boolean,
    val fallbackImagePath: String?,
    val sourceLabel: String?,
    val templateIdHint: Int? = null,
) {
    val key: QrVariantKey get() = QrVariantKey(groupId, mask)
}

data class GroupConfig(
    val id: GroupId,
    val displayName: String,
    val visible: Boolean = true,
    val orderIndex: Int = id.ordinal,
)

/**
 * Settings persisted for Etap 1 (+ stubs for Etap 2–3).
 */
data class AppSettings(
    val visibleCodeCount: Int = 2,
    /** Ordered group IDs to show (subset / order). */
    val groupOrder: List<GroupId> = listOf(GroupId.PICK_PACK_1, GroupId.PICK_PACK_2),
    val keepScreenOn: Boolean = true,
    val pinEnabled: Boolean = false,
    val pinHash: String? = null,
    val demoMode: Boolean = false,
    val dailyResetEnabled: Boolean = false,
    val dailyResetHour: Int = 5,
    val dailyResetMinute: Int = 0,
    val timezoneId: String = "Europe/Warsaw",
    // Etap 2–3 stubs
    val baselinkerTokenPresent: Boolean = false,
    val counterConfigured: Boolean = false,
    val alertThresholdEnabled: Boolean = false,
    val ttsEnabled: Boolean = false,
)

/** Counter display for a group panel (Etap 1: stub / demo simulator). */
sealed class CounterDisplay {
    data object NotConfigured : CounterDisplay()
    data class DemoValue(val waiting: Int, val threshold: Int?) : CounterDisplay()
}
