package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QrSelectionLogicTest {

    private fun variant(
        group: GroupId,
        mask: Int,
        payload: String? = "payload-$mask",
        enabled: Boolean = true,
        fallback: String? = null,
    ) = QrVariantConfig(
        groupId = group,
        mask = mask,
        payload = payload,
        enabled = enabled,
        isDemo = false,
        fallbackImagePath = fallback,
        sourceLabel = "test",
    )

    @Test
    fun allPickedUp_whenNoActiveCouriers() {
        val state = QrSelectionLogic.select(
            groupId = GroupId.PICK_PACK_1,
            activeCourierIds = emptySet(),
            variants = (1..7).associateWith { variant(GroupId.PICK_PACK_1, it) },
            demoMode = false,
        )
        assertThat(state).isInstanceOf(QrDisplayState.AllPickedUp::class.java)
    }

    @Test
    fun exactMask_allSevenCombinations_group1() {
        val variants = (1..7).associateWith { variant(GroupId.PICK_PACK_1, it, "p$it") }
        val defs = DefaultCouriers.forGroup(GroupId.PICK_PACK_1)
        for (mask in 1..7) {
            val active = defs.filter { ((1 shl it.bitIndex) and mask) != 0 }.map { it.id }.toSet()
            val state = QrSelectionLogic.select(
                GroupId.PICK_PACK_1, active, variants, demoMode = false,
            )
            assertThat(state).isInstanceOf(QrDisplayState.Ready::class.java)
            val ready = state as QrDisplayState.Ready
            assertThat(ready.variantKey.mask).isEqualTo(mask)
            assertThat(ready.payload).isEqualTo("p$mask")
        }
    }

    @Test
    fun exactMask_allSevenCombinations_group2() {
        val variants = (1..7).associateWith { variant(GroupId.PICK_PACK_2, it, "g2-$it") }
        val defs = DefaultCouriers.forGroup(GroupId.PICK_PACK_2)
        for (mask in 1..7) {
            val active = defs.filter { ((1 shl it.bitIndex) and mask) != 0 }.map { it.id }.toSet()
            val state = QrSelectionLogic.select(
                GroupId.PICK_PACK_2, active, variants, demoMode = false,
            ) as QrDisplayState.Ready
            assertThat(state.variantKey.mask).isEqualTo(mask)
        }
    }

    @Test
    fun neverPicksSimilarVariant_whenExactMissing() {
        // Only mask 7 configured; active = ORLEN only (mask 1) → Missing, not 7
        val variants = mapOf(7 to variant(GroupId.PICK_PACK_1, 7, "ALL"))
        val state = QrSelectionLogic.select(
            GroupId.PICK_PACK_1,
            activeCourierIds = setOf("orlen"),
            variants = variants,
            demoMode = false,
        )
        assertThat(state).isInstanceOf(QrDisplayState.Missing::class.java)
        val missing = state as QrDisplayState.Missing
        assertThat(missing.variantKey.mask).isEqualTo(1)
        assertThat(missing.reason).isEqualTo(MissingReason.NOT_CONFIGURED)
    }

    @Test
    fun disabledVariant_clearsImmediately() {
        val variants = mapOf(3 to variant(GroupId.PICK_PACK_1, 3, enabled = false))
        val state = QrSelectionLogic.select(
            GroupId.PICK_PACK_1,
            activeCourierIds = setOf("orlen", "dhl"), // bits 0+1 = mask 3
            variants = variants,
            demoMode = false,
        ) as QrDisplayState.Missing
        assertThat(state.reason).isEqualTo(MissingReason.DISABLED)
    }

    @Test
    fun blankPayloadWithoutTemplateId_isNotConfigured() {
        val variants = mapOf(
            1 to variant(GroupId.PICK_PACK_1, 1, payload = "  ", fallback = null),
        )
        val state = QrSelectionLogic.select(
            GroupId.PICK_PACK_1,
            setOf("orlen"),
            variants,
            demoMode = false,
        ) as QrDisplayState.Missing
        assertThat(state.reason).isEqualTo(MissingReason.NOT_CONFIGURED)
    }

    @Test
    fun demoDoesNotSubstituteForMissingProduction() {
        val demo = mapOf(1 to variant(GroupId.PICK_PACK_1, 1, "DEMO").copy(isDemo = true))
        // demoMode false → missing
        val off = QrSelectionLogic.select(
            GroupId.PICK_PACK_1, setOf("orlen"), emptyMap(), demoMode = false, demoVariants = demo,
        )
        assertThat(off).isInstanceOf(QrDisplayState.Missing::class.java)

        // demoMode true + only demo map used when production missing — OK for demo panels
        val on = QrSelectionLogic.select(
            GroupId.PICK_PACK_1, setOf("orlen"), emptyMap(), demoMode = true, demoVariants = demo,
        )
        assertThat(on).isInstanceOf(QrDisplayState.Ready::class.java)
        assertThat((on as QrDisplayState.Ready).isDemo).isTrue()
    }

    @Test
    fun demoNeverOverridesProductionVariant() {
        val prod = mapOf(7 to variant(GroupId.PICK_PACK_1, 7, "PROD"))
        val demo = mapOf(7 to variant(GroupId.PICK_PACK_1, 7, "DEMO").copy(isDemo = true))
        val state = QrSelectionLogic.select(
            GroupId.PICK_PACK_1,
            setOf("orlen", "dhl", "olza"),
            prod,
            demoMode = true,
            demoVariants = demo,
        ) as QrDisplayState.Ready
        assertThat(state.payload).isEqualTo("PROD")
        assertThat(state.isDemo).isFalse()
    }

    @Test
    fun fourteenVariantKeys() {
        assertThat(QrSelectionLogic.allVariantKeys()).hasSize(14)
    }

    @Test
    fun startTemplateIds() {
        assertThat(GroupId.PICK_PACK_1.defaultTemplateId).isEqualTo(18472)
        assertThat(GroupId.PICK_PACK_2.defaultTemplateId).isEqualTo(18173)
    }

    @Test
    fun templateIdOnly_generatesBlpptPayload() {
        val variants = mapOf(
            7 to QrVariantConfig(
                groupId = GroupId.PICK_PACK_1,
                mask = 7,
                payload = null,
                enabled = true,
                isDemo = false,
                fallbackImagePath = null,
                sourceLabel = "auto",
                templateIdHint = 18472,
            )
        )
        val state = QrSelectionLogic.select(
            GroupId.PICK_PACK_1,
            setOf("orlen", "dhl", "olza"),
            variants,
            demoMode = false,
        ) as QrDisplayState.Ready
        assertThat(state.payload).isEqualTo("BLPPT_018472")
        assertThat(state.isDemo).isFalse()
    }

    @Test
    fun missingTemplateId_notConfigured_neverDemoSubstitute() {
        val state = QrSelectionLogic.select(
            GroupId.PICK_PACK_1,
            setOf("orlen"),
            emptyMap(),
            demoMode = false,
            demoVariants = mapOf(
                1 to variant(GroupId.PICK_PACK_1, 1, "DEMO").copy(isDemo = true),
            ),
        )
        assertThat(state).isInstanceOf(QrDisplayState.Missing::class.java)
    }
}
