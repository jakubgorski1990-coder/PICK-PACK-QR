package pl.pickpack.panel.data.provider

import pl.pickpack.panel.domain.GroupId

/**
 * Order-pool / counter abstraction.
 * Etap 1: stub + DEMO simulator.
 * Etap 2: [BaselinkerOrderPoolProvider] (read-only API).
 */
interface OrderPoolProvider {
    suspend fun getWaitingCount(groupId: GroupId, activeCourierIds: Set<String>): Int?
    suspend fun isConfigured(): Boolean
    fun connectionLabel(): String
}

class StubOrderPoolProvider : OrderPoolProvider {
    override suspend fun getWaitingCount(groupId: GroupId, activeCourierIds: Set<String>): Int? = null
    override suspend fun isConfigured(): Boolean = false
    override fun connectionLabel(): String = "Offline"
}

/**
 * DEMO-only counter simulator. Must never be used as a production substitute.
 */
class DemoOrderPoolProvider(
    private var seed: Int = 42,
) : OrderPoolProvider {
    override suspend fun getWaitingCount(groupId: GroupId, activeCourierIds: Set<String>): Int {
        if (activeCourierIds.isEmpty()) return 0
        val base = when (groupId) {
            GroupId.PICK_PACK_1 -> 18
            GroupId.PICK_PACK_2 -> 12
        }
        return (base + activeCourierIds.size * 3 + (seed % 7)).coerceAtLeast(0)
    }

    override suspend fun isConfigured(): Boolean = true
    override fun connectionLabel(): String = "DEMO — symulator"
    fun bump() {
        seed += 1
    }
}
