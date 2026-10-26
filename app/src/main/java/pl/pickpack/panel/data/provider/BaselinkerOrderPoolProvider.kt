package pl.pickpack.panel.data.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.pickpack.panel.data.baselinker.BaseLinkerClient
import pl.pickpack.panel.domain.DefaultCouriers
import pl.pickpack.panel.domain.DeliveryMapping
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.PoolFilterConfig
import pl.pickpack.panel.domain.PoolOrder
import pl.pickpack.panel.domain.SyncState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Read-only order pool backed by BaseLinker getOrders / getOrderStatusList.
 * Counts are **estimated** — API cannot filter by pick/pack template reservation.
 */
class BaselinkerOrderPoolProvider(
    private val client: BaseLinkerClient,
    private val configProvider: () -> PoolFilterConfig,
    private val tokenPresent: () -> Boolean,
) : OrderPoolProvider {

    private val mutex = Mutex()
    private val syncing = AtomicBoolean(false)
    private val orders = MutableStateFlow<List<PoolOrder>>(emptyList())
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var backoffMs: Long = 0
    private var nextAllowedAt: Long = 0

    override suspend fun isConfigured(): Boolean {
        val cfg = configProvider()
        return tokenPresent() && cfg.isCounterRulesReady()
    }

    override fun connectionLabel(): String {
        val state = _syncState.value
        return when {
            !tokenPresent() -> "BL: brak tokenu"
            state.inProgress -> "BL: synchronizacja…"
            state.lastError != null -> "BL: błąd sync"
            state.lastSuccessEpochMs != null -> "BL: OK (szacunek)"
            else -> "BL: oczekiwanie na sync"
        }
    }

    override suspend fun getWaitingCount(groupId: GroupId, activeCourierIds: Set<String>): Int? {
        if (!isConfigured()) return null
        if (activeCourierIds.isEmpty()) return 0
        val cfg = configProvider()
        val groupCourierIds = DefaultCouriers.forGroup(groupId).map { it.id }.toSet()
        val active = activeCourierIds.intersect(groupCourierIds)
        if (active.isEmpty()) return 0
        return countMatching(orders.value, cfg.deliveryMappings, active, cfg.onlyConfirmed)
    }

    suspend fun syncNow(force: Boolean = false): Result<Int> {
        if (!tokenPresent()) {
            _syncState.value = SyncState(lastError = "Brak tokenu")
            return Result.failure(IllegalStateException("Brak tokenu"))
        }
        val cfg = configProvider()
        if (cfg.statusIds.isEmpty()) {
            _syncState.value = SyncState(lastError = "Brak statusów puli")
            return Result.failure(IllegalStateException("Brak statusów"))
        }
        val now = System.currentTimeMillis()
        if (!force && now < nextAllowedAt) {
            return Result.failure(IllegalStateException("Backoff aktywny"))
        }
        if (!syncing.compareAndSet(false, true)) {
            return Result.failure(IllegalStateException("Sync już trwa"))
        }
        _syncState.value = _syncState.value.copy(inProgress = true, lastError = null)
        return try {
            mutex.withLock {
                val collected = linkedMapOf<Long, PoolOrder>()
                for (statusId in cfg.statusIds.distinct()) {
                    var idFrom: Long? = null
                    var guard = 0
                    while (guard++ < 50) {
                        val page = client.getOrders(
                            statusId = statusId,
                            idFrom = idFrom,
                            getUnconfirmed = !cfg.onlyConfirmed,
                        )
                        if (page.isEmpty()) break
                        for (o in page) {
                            if (cfg.onlyConfirmed && !o.confirmed) continue
                            collected[o.orderId] = o
                        }
                        if (page.size < 100) break
                        val maxId = page.maxOf { it.orderId }
                        idFrom = maxId
                    }
                }
                orders.value = collected.values.toList()
                backoffMs = 0
                nextAllowedAt = 0
                _syncState.value = SyncState(
                    lastSuccessEpochMs = System.currentTimeMillis(),
                    lastError = null,
                    inProgress = false,
                    orderCountCached = collected.size,
                )
                Result.success(collected.size)
            }
        } catch (e: Exception) {
            backoffMs = (if (backoffMs == 0L) 5_000L else (backoffMs * 2)).coerceAtMost(300_000L)
            nextAllowedAt = System.currentTimeMillis() + backoffMs
            _syncState.value = SyncState(
                lastSuccessEpochMs = _syncState.value.lastSuccessEpochMs,
                lastError = e.message ?: "Błąd sync",
                inProgress = false,
                orderCountCached = orders.value.size,
            )
            Result.failure(e)
        } finally {
            syncing.set(false)
        }
    }

    fun cachedOrders(): List<PoolOrder> = orders.value

    companion object {
        /**
         * Start / full-set variant: count all cached pool orders (already fetched for
         * configured status_id list) — no delivery filter.
         */
        fun countInPoolStatuses(
            orders: List<PoolOrder>,
            onlyConfirmed: Boolean,
        ): Int {
            val seen = HashSet<Long>()
            var count = 0
            for (order in orders) {
                if (onlyConfirmed && !order.confirmed) continue
                if (!seen.add(order.orderId)) continue
                count++
            }
            return count
        }

        /**
         * Partial courier variants: intersect pool cache with delivery methods of
         * currently active couriers (client-side — getOrders has no delivery input filter).
         */
        fun countMatching(
            orders: List<PoolOrder>,
            mappings: List<DeliveryMapping>,
            activeCourierIds: Set<String>,
            onlyConfirmed: Boolean,
        ): Int {
            if (activeCourierIds.isEmpty()) return 0
            val seen = HashSet<Long>()
            var count = 0
            for (order in orders) {
                if (onlyConfirmed && !order.confirmed) continue
                if (!seen.add(order.orderId)) continue
                val courier = mappings.firstOrNull {
                    it.isValid() && it.matches(order.deliveryMethodId, order.deliveryMethodName)
                }?.courierId ?: continue
                if (courier in activeCourierIds) count++
            }
            return count
        }
    }
}
