package pl.pickpack.panel.data

import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.QrVariantConfig

/**
 * Configuration export/import with QR payloads — no API secrets.
 * Pure Kotlin (no org.json) so JVM unit tests run without Android mocks.
 */
object ConfigExportImport {
    private const val VERSION = 1

    fun exportJson(
        variants: List<QrVariantConfig>,
        groupNames: Map<GroupId, String>,
    ): String {
        val groups = GroupId.entries.joinToString(",\n") { g ->
            """    {"id":${g.storageKey.json()},"name":${(groupNames[g] ?: g.defaultName).json()},"defaultTemplateId":${g.defaultTemplateId}}"""
        }
        val variantObjs = variants.filter { !it.isDemo }.joinToString(",\n") { v ->
            buildString {
                append("    {")
                append("\"group\":${v.groupId.storageKey.json()}")
                append(",\"mask\":${v.mask}")
                append(",\"payload\":${v.payload.jsonNullable()}")
                append(",\"enabled\":${v.enabled}")
                append(",\"fallbackImagePath\":${v.fallbackImagePath.jsonNullable()}")
                append(",\"sourceLabel\":${v.sourceLabel.jsonNullable()}")
                append(",\"templateIdHint\":${v.templateIdHint ?: "null"}")
                append("}")
            }
        }
        return """
            |{
            |  "version": $VERSION,
            |  "app": "PickPackPanel",
            |  "stage": 1,
            |  "groups": [
            |$groups
            |  ],
            |  "variants": [
            |$variantObjs
            |  ]
            |}
        """.trimMargin()
    }

    fun importJson(raw: String): List<QrVariantConfig> {
        val variantsBlock = raw.substringAfter("\"variants\"", "")
            .substringAfter("[")
            .substringBeforeLast("]")
        if (variantsBlock.isBlank()) return emptyList()
        val objects = splitJsonObjects(variantsBlock)
        return objects.mapNotNull { obj ->
            val group = GroupId.fromStorageKey(stringField(obj, "group") ?: return@mapNotNull null)
                ?: return@mapNotNull null
            val mask = intField(obj, "mask") ?: return@mapNotNull null
            if (mask !in 1..7) return@mapNotNull null
            QrVariantConfig(
                groupId = group,
                mask = mask,
                payload = stringField(obj, "payload"),
                enabled = boolField(obj, "enabled") ?: true,
                isDemo = false,
                fallbackImagePath = stringField(obj, "fallbackImagePath"),
                sourceLabel = stringField(obj, "sourceLabel"),
                templateIdHint = intField(obj, "templateIdHint"),
            )
        }
    }

    private fun splitJsonObjects(block: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in block) {
            when (ch) {
                '{' -> {
                    depth++
                    current.append(ch)
                }
                '}' -> {
                    current.append(ch)
                    depth--
                    if (depth == 0 && current.isNotBlank()) {
                        result += current.toString().trim()
                        current.clear()
                    }
                }
                else -> if (depth > 0) current.append(ch)
            }
        }
        return result
    }

    private fun stringField(obj: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".toRegex()
        val nullPattern = "\"$key\"\\s*:\\s*null".toRegex()
        if (nullPattern.containsMatchIn(obj)) return null
        val match = pattern.find(obj) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
            .ifBlank { null }
    }

    private fun intField(obj: String, key: String): Int? {
        val pattern = "\"$key\"\\s*:\\s*(-?\\d+)".toRegex()
        return pattern.find(obj)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun boolField(obj: String, key: String): Boolean? {
        val pattern = "\"$key\"\\s*:\\s*(true|false)".toRegex()
        return pattern.find(obj)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    private fun String?.jsonNullable(): String =
        if (this == null) "null" else json()

    private fun String.json(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""
}
