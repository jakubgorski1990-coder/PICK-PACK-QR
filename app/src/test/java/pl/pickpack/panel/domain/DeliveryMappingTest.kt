package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeliveryMappingTest {
    @Test
    fun matchesAnyNamePattern_caseInsensitiveContains() {
        val m = DeliveryMapping(
            courierId = "dhl",
            namePatterns = listOf("DHL", "STD_2do"),
        )
        assertThat(m.matches(null, "Przesyłka dhl Express")).isTrue()
        assertThat(m.matches(null, "Kurier STD_2DO nocny")).isTrue()
        assertThat(m.matches(null, "InPost Paczkomat")).isFalse()
    }

    @Test
    fun matchesDeliveryMethodId() {
        val m = DeliveryMapping(courierId = "inpost", deliveryMethodId = 100, namePatterns = listOf("InPost"))
        assertThat(m.matches(100, "Something else")).isTrue()
        assertThat(m.matches(99, "nope")).isFalse()
    }

    @Test
    fun addRemovePatterns_andLegacyNameContainsMerged() {
        var m = DeliveryMapping(courierId = "dhl", nameContains = "DHL")
        assertThat(m.allNamePatterns()).containsExactly("DHL")
        m = m.withPatternAdded("STD_2do").withPatternAdded("dhl") // distinct by value as added
        assertThat(m.allNamePatterns()).containsAtLeast("DHL", "STD_2do")
        m = m.withPatternRemoved("DHL")
        assertThat(m.allNamePatterns()).doesNotContain("DHL")
        assertThat(m.allNamePatterns()).contains("STD_2do")
    }

    @Test
    fun mergeForCourier_combinesRows() {
        val merged = DeliveryMapping.mergeForCourier(
            "dhl",
            listOf(
                DeliveryMapping("dhl", nameContains = "DHL"),
                DeliveryMapping("dhl", deliveryMethodId = 55, namePatterns = listOf("STD_2do")),
            ),
        )
        assertThat(merged.deliveryMethodId).isEqualTo(55)
        assertThat(merged.allNamePatterns()).containsExactly("DHL", "STD_2do")
    }
}
