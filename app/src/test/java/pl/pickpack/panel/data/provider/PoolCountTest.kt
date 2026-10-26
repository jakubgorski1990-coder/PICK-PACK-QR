package pl.pickpack.panel.data.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pl.pickpack.panel.domain.DeliveryMapping
import pl.pickpack.panel.domain.PoolOrder

class PoolCountTest {
    private val orders = listOf(
        PoolOrder(1, 10, 100, "InPost Paczkomat", true),
        PoolOrder(1, 10, 100, "InPost Paczkomat", true), // dup
        PoolOrder(2, 10, 200, "DPD", true),
        PoolOrder(3, 10, 100, "InPost", true),
        PoolOrder(4, 10, 300, "ORLEN", true),
    )
    private val mappings = listOf(
        DeliveryMapping("inpost", deliveryMethodId = 100, nameContains = null),
        DeliveryMapping("dpd", nameContains = "DPD"),
        DeliveryMapping("orlen", deliveryMethodId = 300, null),
    )

    @Test
    fun countsByDeliveryMapping_dedupesOrderId() {
        val n = BaselinkerOrderPoolProvider.countMatching(
            orders, mappings, activeCourierIds = setOf("inpost"), onlyConfirmed = true,
        )
        assertThat(n).isEqualTo(2)
    }

    @Test
    fun emptyActive_returnsZero() {
        val n = BaselinkerOrderPoolProvider.countMatching(
            listOf(PoolOrder(1, 10, 100, "X", true)),
            listOf(DeliveryMapping("inpost", 100, null)),
            activeCourierIds = emptySet(),
            onlyConfirmed = true,
        )
        assertThat(n).isEqualTo(0)
    }

    @Test
    fun startVariant_countInPoolStatuses_ignoresDelivery() {
        // Full-set / start variant: raw status_id pool count (deduped).
        assertThat(BaselinkerOrderPoolProvider.countInPoolStatuses(orders, onlyConfirmed = true))
            .isEqualTo(4)
    }

    @Test
    fun partialVariant_filtersByActiveCourierDelivery() {
        val n = BaselinkerOrderPoolProvider.countMatching(
            orders, mappings, activeCourierIds = setOf("inpost", "dpd"), onlyConfirmed = true,
        )
        assertThat(n).isEqualTo(3) // 2 inpost + 1 dpd; orlen excluded
    }
}
