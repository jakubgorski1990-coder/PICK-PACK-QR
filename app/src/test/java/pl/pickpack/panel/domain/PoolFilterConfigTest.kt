package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PoolFilterConfigTest {
    @Test
    fun separateStatusIdsPerGroup() {
        val cfg = PoolFilterConfig(pickPack1StatusId = 100, pickPack2StatusId = 200)
        assertThat(cfg.statusIdFor(GroupId.PICK_PACK_1)).isEqualTo(100)
        assertThat(cfg.statusIdFor(GroupId.PICK_PACK_2)).isEqualTo(200)
        assertThat(cfg.allConfiguredStatusIds()).containsExactly(100, 200)
    }

    @Test
    fun legacyStatusIdsMigratesFirstToPp1SecondToPp2() {
        val cfg = PoolFilterConfig(statusIds = listOf(11, 22))
        assertThat(cfg.statusIdFor(GroupId.PICK_PACK_1)).isEqualTo(11)
        assertThat(cfg.statusIdFor(GroupId.PICK_PACK_2)).isEqualTo(22)
    }

    @Test
    fun legacySingleStatusDoesNotAssignPp2() {
        val cfg = PoolFilterConfig(statusIds = listOf(11))
        assertThat(cfg.statusIdFor(GroupId.PICK_PACK_1)).isEqualTo(11)
        assertThat(cfg.statusIdFor(GroupId.PICK_PACK_2)).isNull()
    }

    @Test
    fun withStatusIdClearsLegacyList() {
        val cfg = PoolFilterConfig(statusIds = listOf(1, 2))
            .withStatusId(GroupId.PICK_PACK_1, 99)
        assertThat(cfg.pickPack1StatusId).isEqualTo(99)
        assertThat(cfg.statusIds).isEmpty()
    }
}
