package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NamedStatusCountLogicTest {
    @Test
    fun countsByStatusId_dedupesAndIgnoresOtherStatuses() {
        val orders = listOf(
            PoolOrder(1, 100, null, null, true),
            PoolOrder(2, 100, null, null, true),
            PoolOrder(2, 100, null, null, true), // dup order_id
            PoolOrder(3, 200, null, null, true),
            PoolOrder(4, 999, null, null, true),
        )
        val counts = NamedStatusCountLogic.countsByStatusId(orders, listOf(100, 200, 300))
        assertThat(counts[100]).isEqualTo(2)
        assertThat(counts[200]).isEqualTo(1)
        assertThat(counts[300]).isEqualTo(0)
        assertThat(counts.containsKey(999)).isFalse()
    }

    @Test
    fun countForDisplayName_matchesCaseInsensitive() {
        val statuses = listOf(NamedStatusRef(1, "ANIA"), NamedStatusRef(2, "Bartek"))
        val counts = mapOf(1 to 4, 2 to 1)
        assertThat(NamedStatusCountLogic.countForDisplayName("ania", statuses, counts)).isEqualTo(4)
        assertThat(NamedStatusCountLogic.countForDisplayName("X", statuses, counts)).isNull()
    }
}
