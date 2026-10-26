package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImienneGridLayoutTest {
    @Test
    fun fitsTwelveOnLandscape() {
        val (cols, rows) = ImienneGridLayout.dimensions(12, 16f / 9f)
        assertThat(cols * rows).isAtLeast(12)
        assertThat(cols).isAtLeast(1)
        assertThat(rows).isAtLeast(1)
    }

    @Test
    fun singleCell() {
        val (cols, rows) = ImienneGridLayout.dimensions(1)
        assertThat(cols to rows).isEqualTo(1 to 1)
    }

    @Test
    fun namedResolvesBlppt() {
        val e = NamedQrEntry("1", "Ada", 18472)
        assertThat(e.resolvedPayload()).isEqualTo("BLPPT_018472")
        assertThat(BlpptPayload.parseFlexibleTemplateId("18472")).isEqualTo(18472)
        assertThat(BlpptPayload.parseFlexibleTemplateId("BLPPT_018173")).isEqualTo(18173)
    }
}
