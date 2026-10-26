package pl.pickpack.panel.domain

/**
 * Read-only pool filter rules using only filters available in BaseLinker API
 * (status_id, confirmed flag, delivery_method / delivery_method_id).
 * Cannot recreate full pick/pack template reservation → counts are estimated.
 */
data class PoolFilterConfig(
    val syncIntervalSec: Int = 60,
    val onlyConfirmed: Boolean = true,
    /** Status IDs treated as “waiting in pool”. Empty → counter not configured. */
    val statusIds: List<Int> = emptyList(),
    val deliveryMappings: List<DeliveryMapping> = emptyList(),
    /** Estimated-count alerts stay off by default (Etap 3). */
    val estimatedAlertsEnabled: Boolean = false,
) {
    fun isCounterRulesReady(): Boolean =
        statusIds.isNotEmpty() && deliveryMappings.any { it.isValid() }
}

data class DeliveryMapping(
    val courierId: String,
    val deliveryMethodId: Int? = null,
    /** Case-insensitive substring match against delivery_method name. */
    val nameContains: String? = null,
) {
    fun isValid(): Boolean =
        courierId.isNotBlank() && (deliveryMethodId != null || !nameContains.isNullOrBlank())

    fun matches(deliveryMethodId: Int?, deliveryMethodName: String?): Boolean {
        if (this.deliveryMethodId != null && deliveryMethodId != null &&
            this.deliveryMethodId == deliveryMethodId
        ) {
            return true
        }
        val needle = nameContains?.trim()?.lowercase().orEmpty()
        if (needle.isNotEmpty()) {
            val hay = deliveryMethodName?.lowercase().orEmpty()
            if (hay.contains(needle)) return true
        }
        return false
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
)

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
