package pl.pickpack.panel.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.NamedQrEntry
import pl.pickpack.panel.domain.QrVariantConfig

class ConfigExportImportTest {
    @Test
    fun roundTrip_excludesDemoAndSecrets_includesNamed() {
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
        val named = listOf(
            NamedQrEntry("n1", "Anna", 18472, orderIndex = 0),
            NamedQrEntry("n2", "DemoPerson", 1, orderIndex = 1, isDemo = true),
        )
        val json = ConfigExportImport.exportJson(
            variants,
            mapOf(GroupId.PICK_PACK_1 to "PP1", GroupId.PICK_PACK_2 to "PP2"),
            named,
        )
        assertThat(json).doesNotContain("api")
        assertThat(json).doesNotContain("token")
        assertThat(json).doesNotContain("DEMO-IGNORE")
        assertThat(json).doesNotContain("DemoPerson")
        assertThat(json).contains("Anna")
        val imported = ConfigExportImport.importJson(json)
        assertThat(imported.variants).hasSize(1)
        assertThat(imported.variants[0].payload).isEqualTo("BL-CONTENT-18472")
        assertThat(imported.namedEntries).hasSize(1)
        assertThat(imported.namedEntries[0].displayName).isEqualTo("Anna")
        assertThat(imported.namedEntries[0].templateId).isEqualTo(18472)
    }
}
