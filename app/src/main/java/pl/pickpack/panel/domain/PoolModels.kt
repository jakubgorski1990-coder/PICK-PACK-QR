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

/** Minimal order fields for pool counting — never store customer PII. */
data class PoolOrder(
    val orderId: Long,
    val statusId: Int,
    val deliveryMethodId: Int?,
    val deliveryMethodName: String?,
    val confirmed: Boolean,
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
