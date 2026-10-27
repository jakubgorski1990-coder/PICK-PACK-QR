package pl.pickpack.panel.domain

/**
 * Pick/Pack pool filter using only BaseLinker getOrders capabilities.
 *
 * PP1 i PP2 mają **osobne** status_id BL:
 * - Start variant grupy = count getOrders(status_id tej grupy)
 * - Partial = te zamówienia ∩ delivery aktywnych kurierów grupy
 */
data class PoolFilterConfig(
    val syncIntervalSec: Int = 60,
    val onlyConfirmed: Boolean = true,
    /** BaseLinker status_id for Pick/Pack 1 queue. */
    val pickPack1StatusId: Int? = null,
    /** BaseLinker status_id for Pick/Pack 2 queue. */
    val pickPack2StatusId: Int? = null,
    /**
     * Legacy shared list (older builds). Migrated into [pickPack1StatusId]/[pickPack2StatusId]
     * when those are null.
     */
    val statusIds: List<Int> = emptyList(),
    val deliveryMappings: List<DeliveryMapping> = emptyList(),
    /** Estimated-count alerts stay off by default (Etap 3). */
    val estimatedAlertsEnabled: Boolean = false,
) {
    fun statusIdFor(groupId: GroupId): Int? = when (groupId) {
        GroupId.PICK_PACK_1 -> pickPack1StatusId ?: statusIds.getOrNull(0)
        // Legacy: only map second list entry to PP2 — never reuse PP1's status.
        GroupId.PICK_PACK_2 -> pickPack2StatusId ?: statusIds.getOrNull(1)
    }

    fun withStatusId(groupId: GroupId, statusId: Int?): PoolFilterConfig = when (groupId) {
        GroupId.PICK_PACK_1 -> copy(pickPack1StatusId = statusId, statusIds = emptyList())
        GroupId.PICK_PACK_2 -> copy(pickPack2StatusId = statusId, statusIds = emptyList())
    }

    /** All distinct status IDs used by any group (for sync). */
    fun allConfiguredStatusIds(): List<Int> =
        listOfNotNull(statusIdFor(GroupId.PICK_PACK_1), statusIdFor(GroupId.PICK_PACK_2)).distinct()

    fun isGroupConfigured(groupId: GroupId): Boolean = statusIdFor(groupId) != null

    /** At least one group has a pool status_id. */
    fun isPoolConfigured(): Boolean = allConfiguredStatusIds().isNotEmpty()

    fun hasDeliveryMappings(): Boolean = deliveryMappings.any { it.isValid() }

    fun isCounterRulesReady(): Boolean = isPoolConfigured()
}

/**
 * Maps BaseLinker delivery_method (response) → courier for partial-variant counters.
 * Match: optional exact [deliveryMethodId], and/or any [namePatterns] via case-insensitive **contains**
 * (e.g. DHL: „DHL”, „STD_2do”). Legacy [nameContains] is merged into patterns.
 */
data class DeliveryMapping(
    val courierId: String,
    val deliveryMethodId: Int? = null,
    /** @deprecated Prefer [namePatterns]; still read for older configs. */
    val nameContains: String? = null,
    /** Case-insensitive substring patterns against delivery_method name. */
    val namePatterns: List<String> = emptyList(),
) {
    fun allNamePatterns(): List<String> {
        val out = linkedSetOf<String>()
        nameContains?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        namePatterns.map { it.trim() }.filter { it.isNotEmpty() }.forEach { out.add(it) }
        return out.toList()
    }

    fun isValid(): Boolean =
        courierId.isNotBlank() && (deliveryMethodId != null || allNamePatterns().isNotEmpty())

    fun matches(deliveryMethodId: Int?, deliveryMethodName: String?): Boolean {
        if (this.deliveryMethodId != null && deliveryMethodId != null &&
            this.deliveryMethodId == deliveryMethodId
        ) {
            return true
        }
        val hay = deliveryMethodName?.lowercase().orEmpty()
        if (hay.isEmpty()) return false
        return allNamePatterns().any { pattern ->
            hay.contains(pattern.lowercase())
        }
    }

    fun withPatternAdded(pattern: String): DeliveryMapping {
        val p = pattern.trim()
        if (p.isEmpty()) return normalized()
        return copy(nameContains = null, namePatterns = (allNamePatterns() + p).distinct())
    }

    fun withPatternRemoved(pattern: String): DeliveryMapping {
        val needle = pattern.trim().lowercase()
        return copy(
            nameContains = null,
            namePatterns = allNamePatterns().filter { it.lowercase() != needle },
        )
    }

    /** Collapse legacy nameContains into namePatterns for persistence. */
    fun normalized(): DeliveryMapping =
        copy(nameContains = null, namePatterns = allNamePatterns())

    companion object {
        /** Merge several rows for the same courier (older multi-row storage). */
        fun mergeForCourier(courierId: String, rows: List<DeliveryMapping>): DeliveryMapping {
            val id = rows.mapNotNull { it.deliveryMethodId }.firstOrNull()
            val patterns = rows.flatMap { it.allNamePatterns() }.distinct()
            return DeliveryMapping(
                courierId = courierId,
                deliveryMethodId = id,
                namePatterns = patterns,
            )
        }
    }
}

/** Minimal order fields for pool counting / alerts — never store customer PII. */
data class PoolOrder(
    val orderId: Long,
    val statusId: Int,
    val deliveryMethodId: Int?,
    val deliveryMethodName: String?,
    val confirmed: Boolean,
    /**
     * Unix seconds when the order entered the current status (`date_in_status` from getOrders).
     * Fallback chain in client: date_in_status → date_confirmed → date_add.
     */
    val dateInStatusEpochSec: Long? = null,
)

/** BaseLinker status mapped to a person name for the imienny voice alert. */
data class NamedStatusRef(
    val statusId: Int,
    val displayName: String,
)

data class NamedStatusAlertConfig(
    val enabled: Boolean = true,
    /** Alert when any order has been in a named status longer than this (default 30). */
    val thresholdMinutes: Int = 30,
    /** Minimum gap between MP3 plays while condition stays true (default 10). */
    val cooldownMinutes: Int = 10,
    val statuses: List<NamedStatusRef> = emptyList(),
) {
    companion object {
        fun defaultSeed(): List<NamedStatusRef> = listOf(
            NamedStatusRef(356748, "ANIA"),
            NamedStatusRef(356749, "ANIA SZ"),
            NamedStatusRef(356750, "Bartek"),
            NamedStatusRef(356751, "Jakub"),
            NamedStatusRef(356752, "Kamil"),
            NamedStatusRef(356753, "Klaudia"),
            NamedStatusRef(356754, "Michał"),
            NamedStatusRef(356755, "Ola"),
            NamedStatusRef(356756, "PBTO"),
            NamedStatusRef(356757, "Łukasz"),
        )
    }
}

data class NamedAlertState(
    val active: Boolean = false,
    val overdueCount: Int = 0,
    val lastPlayedEpochMs: Long? = null,
    val lastCheckEpochMs: Long? = null,
    val lastError: String? = null,
    /**
     * Orders currently in each named status_id (from getOrders).
     * Empty map when never synced / error — do not treat as all zeros on error.
     */
    val countsByStatusId: Map<Int, Int> = emptyMap(),
    val countsReliable: Boolean = false,
)

/** Pure helpers for named-status order counts (status_id). */
object NamedStatusCountLogic {
    fun countsByStatusId(orders: List<PoolOrder>, statusIds: Collection<Int>): Map<Int, Int> {
        val wanted = statusIds.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val seen = HashSet<Long>()
        val counts = wanted.associateWith { 0 }.toMutableMap()
        for (o in orders) {
            if (o.statusId !in wanted) continue
            if (!seen.add(o.orderId)) continue
            counts[o.statusId] = (counts[o.statusId] ?: 0) + 1
        }
        return counts
    }

    /** Match imienne QR display name → status count when names align. */
    fun countForDisplayName(
        displayName: String,
        statuses: List<NamedStatusRef>,
        countsByStatusId: Map<Int, Int>,
    ): Int? {
        val needle = displayName.trim().lowercase()
        if (needle.isEmpty()) return null
        val ref = statuses.firstOrNull { it.displayName.trim().lowercase() == needle } ?: return null
        return countsByStatusId[ref.statusId]
    }
}

data class BlStatus(
    val id: Int,
    val name: String,
    val color: String? = null,
)

data class SyncState(
    val lastSuccessEpochMs: Long? = null,
    val lastError: String? = null,
    val inProgress: Boolean = false,
    val orderCountCached: Int = 0,
)
