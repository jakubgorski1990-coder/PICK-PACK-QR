package pl.pickpack.panel.data.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pl.pickpack.panel.domain.DeliveryMapping
import pl.pickpack.panel.domain.PoolOrder

class PoolCountTest {
    @Test
    fun countsByDeliveryMapping_dedupesOrderId() {
        val orders = listOf(
            PoolOrder(1, 10, 100, "InPost Paczkomat", true),
            PoolOrder(1, 10, 100, "InPost Paczkomat", true), // dup
            PoolOrder(2, 10, 200, "DPD", true),
            PoolOrder(3, 10, 100, "InPost", true),
        )
        val mappings = listOf(
            DeliveryMapping("inpost", deliveryMethodId = 100, nameContains = null),
            DeliveryMapping("dpd", nameContains = "DPD"),
        )
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
}
