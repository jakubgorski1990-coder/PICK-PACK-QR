package pl.pickpack.panel.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.QrVariantConfig

class ConfigExportImportTest {
    @Test
    fun roundTrip_excludesDemoAndSecrets() {
        val variants = listOf(
            QrVariantConfig(
                groupId = GroupId.PICK_PACK_1,
                mask = 7,
                payload = "BL-CONTENT-18472",
                enabled = true,
                isDemo = false,
                fallbackImagePath = null,
                sourceLabel = "import",
                templateIdHint = 18472,
            ),
            QrVariantConfig(
                groupId = GroupId.PICK_PACK_2,
                mask = 1,
                payload = "DEMO-IGNORE",
                enabled = true,
                isDemo = true,
                fallbackImagePath = null,
                sourceLabel = DemoContent.BANNER,
                templateIdHint = null,
            ),
        )
        val json = ConfigExportImport.exportJson(
            variants,
            mapOf(GroupId.PICK_PACK_1 to "PP1", GroupId.PICK_PACK_2 to "PP2"),
        )
        assertThat(json).doesNotContain("api")
        assertThat(json).doesNotContain("token")
        assertThat(json).doesNotContain("DEMO-IGNORE")
        val imported = ConfigExportImport.importJson(json)
        assertThat(imported).hasSize(1)
        assertThat(imported[0].payload).isEqualTo("BL-CONTENT-18472")
        assertThat(imported[0].templateIdHint).isEqualTo(18472)
    }
}
