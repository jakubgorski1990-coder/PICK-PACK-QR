package pl.pickpack.panel.domain

/**
 * Pure rules for the imienny-status voice alert.
 * Age is measured from [PoolOrder.dateInStatusEpochSec] (seconds).
 */
object NamedStatusAlertLogic {

    fun overdueOrders(
        orders: List<PoolOrder>,
        namedStatusIds: Set<Int>,
        nowEpochSec: Long,
        thresholdMinutes: Int,
    ): List<PoolOrder> {
        if (namedStatusIds.isEmpty()) return emptyList()
        val thresholdSec = thresholdMinutes.coerceAtLeast(1) * 60L
        return orders.filter { order ->
            if (order.statusId !in namedStatusIds) return@filter false
            val entered = order.dateInStatusEpochSec ?: return@filter false
            (nowEpochSec - entered) > thresholdSec
        }
    }

    /**
     * Whether to play now given overdue set + latch/cooldown.
     * - First breach → play
     * - Still breached → play only after [cooldownMinutes]
     * - Cleared → latch resets (next breach plays immediately)
     */
    fun shouldPlay(
        overdueCount: Int,
        wasActive: Boolean,
        lastPlayedEpochMs: Long?,
        nowEpochMs: Long,
        cooldownMinutes: Int,
    ): Boolean {
        if (overdueCount <= 0) return false
        if (!wasActive) return true
        val last = lastPlayedEpochMs ?: return true
        val coolMs = cooldownMinutes.coerceAtLeast(1) * 60_000L
        return nowEpochMs - last >= coolMs
    }
}
