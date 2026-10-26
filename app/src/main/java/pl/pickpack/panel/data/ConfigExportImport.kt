package pl.pickpack.panel.data

import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.NamedQrEntry
import pl.pickpack.panel.domain.QrVariantConfig
import java.util.UUID

/**
 * Configuration export/import with QR payloads — no API secrets.
 */
object ConfigExportImport {
    private const val VERSION = 2

    data class Bundle(
        val variants: List<QrVariantConfig>,
        val namedEntries: List<NamedQrEntry>,
    )

    fun exportJson(
        variants: List<QrVariantConfig>,
        groupNames: Map<GroupId, String>,
        namedEntries: List<NamedQrEntry> = emptyList(),
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
        val namedObjs = namedEntries.filter { !it.isDemo }.joinToString(",\n") { e ->
            buildString {
                append("    {")
                append("\"id\":${e.id.json()}")
                append(",\"displayName\":${e.displayName.json()}")
                append(",\"templateId\":${e.templateId ?: "null"}")
                append(",\"payloadOverride\":${e.payloadOverride.jsonNullable()}")
                append(",\"orderIndex\":${e.orderIndex}")
                append("}")
            }
        }
        return """
            |{
            |  "version": $VERSION,
            |  "app": "PickPackPanel",
            |  "stage": 2,
            |  "groups": [
            |$groups
            |  ],
            |  "variants": [
            |$variantObjs
            |  ],
            |  "namedEntries": [
            |$namedObjs
            |  ]
            |}
        """.trimMargin()
    }

    fun importJson(raw: String): Bundle {
        val variants = importVariants(raw)
        val named = importNamed(raw)
        return Bundle(variants, named)
    }

    /** Backward-compatible: variants only. */
    fun importVariantsOnly(raw: String): List<QrVariantConfig> = importVariants(raw)

    private fun importVariants(raw: String): List<QrVariantConfig> {
        val variantsBlock = extractArrayBlock(raw, "variants") ?: return emptyList()
        return splitJsonObjects(variantsBlock).mapNotNull { obj ->
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

    private fun importNamed(raw: String): List<NamedQrEntry> {
        val block = extractArrayBlock(raw, "namedEntries") ?: return emptyList()
        return splitJsonObjects(block).mapIndexedNotNull { index, obj ->
            val name = stringField(obj, "displayName") ?: return@mapIndexedNotNull null
            NamedQrEntry(
                id = stringField(obj, "id") ?: UUID.randomUUID().toString(),
                displayName = name,
                templateId = intField(obj, "templateId"),
                payloadOverride = stringField(obj, "payloadOverride"),
                orderIndex = intField(obj, "orderIndex") ?: index,
                isDemo = false,
            )
        }
    }

    private fun extractArrayBlock(raw: String, key: String): String? {
        val marker = "\"$key\""
        if (!raw.contains(marker)) return null
        val after = raw.substringAfter(marker).substringAfter("[")
        // Take until matching closing bracket at depth 0 for the array — reuse object splitter on content
        var depth = 1
        val sb = StringBuilder()
        for (ch in after) {
            when (ch) {
                '[' -> {
                    depth++
                    sb.append(ch)
                }
                ']' -> {
                    depth--
                    if (depth == 0) break
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
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
