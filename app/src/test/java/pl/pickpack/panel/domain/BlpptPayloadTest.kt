package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlpptPayloadTest {
    @Test
    fun padsToSixDigits() {
        assertThat(BlpptPayload.fromTemplateId(18472)).isEqualTo("BLPPT_018472")
        assertThat(BlpptPayload.fromTemplateId(18173)).isEqualTo("BLPPT_018173")
        assertThat(BlpptPayload.fromTemplateId(1)).isEqualTo("BLPPT_000001")
    }

    @Test
    fun resolvePrefersManualOverride() {
        val cfg = QrVariantConfig(
            groupId = GroupId.PICK_PACK_1,
            mask = 7,
            payload = "CUSTOM",
            enabled = true,
            isDemo = false,
            fallbackImagePath = null,
            sourceLabel = null,
            templateIdHint = 18472,
        )
        assertThat(BlpptPayload.resolve(cfg)).isEqualTo("CUSTOM")
    }

    @Test
    fun resolveUsesTemplateIdWhenNoOverride() {
        val cfg = QrVariantConfig(
            groupId = GroupId.PICK_PACK_1,
            mask = 7,
            payload = null,
            enabled = true,
            isDemo = false,
            fallbackImagePath = null,
            sourceLabel = null,
            templateIdHint = 18472,
        )
        assertThat(BlpptPayload.resolve(cfg)).isEqualTo("BLPPT_018472")
    }
}
