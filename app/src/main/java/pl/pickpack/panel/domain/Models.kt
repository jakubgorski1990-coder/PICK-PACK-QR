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
 * Settings persisted for Etap 1–2 (+ stubs for Etap 3).
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
    val baselinkerTokenPresent: Boolean = false,
    val counterConfigured: Boolean = false,
    val alertThresholdEnabled: Boolean = false,
    val ttsEnabled: Boolean = false,
    val poolFilter: PoolFilterConfig = PoolFilterConfig(),
    /** Idle seconds on IMIENNE before auto-return to Pick/Pack (default 15). */
    val imienneTimeoutSec: Int = 15,
)

/**
 * Named (IMIENNE) QR entry — one person / one template.
 * Payload = optional override, else BLPPT_ + padded templateId.
 */
data class NamedQrEntry(
    val id: String,
    val displayName: String,
    val templateId: Int?,
    val payloadOverride: String? = null,
    val orderIndex: Int = 0,
    val isDemo: Boolean = false,
) {
    fun resolvedPayload(): String? {
        payloadOverride?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val idNum = templateId ?: return null
        return BlpptPayload.fromTemplateId(idNum)
    }
}

/** Landscape grid dims that fit [count] cells and maximize QR size. */
object ImienneGridLayout {
    fun dimensions(count: Int, viewportAspect: Float = 16f / 9f): Pair<Int, Int> {
        val n = count.coerceAtLeast(1)
        var bestCols = n
        var bestRows = 1
        var bestScore = -1.0
        for (cols in 1..n) {
            val rows = (n + cols - 1) / cols
            // Approximate cell aspect under landscape: wider viewport → prefer more cols
            val cellAspect = (viewportAspect * rows) / cols
            // Prefer near-square cells (QR likes square); score = how close to 1
            val squareScore = 1.0 / (1.0 + kotlin.math.abs(cellAspect - 1.0))
            val areaScore = 1.0 / (cols * rows) // fewer empty slots slightly better via tighter pack
            val score = squareScore * 10 + areaScore
            if (score > bestScore) {
                bestScore = score
                bestCols = cols
                bestRows = rows
            }
        }
        return bestCols to bestRows
    }
}

/** Counter display for a group panel. */
sealed class CounterDisplay {
    data object NotConfigured : CounterDisplay()
    data class DemoValue(val waiting: Int, val threshold: Int?) : CounterDisplay()
    data class Live(
        val waiting: Int,
        val estimated: Boolean,
        val lastSyncEpochMs: Long?,
        val error: String? = null,
    ) : CounterDisplay()
}
