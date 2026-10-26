package pl.pickpack.panel.domain

/**
 * Deterministic BaseLinker pick/pack template QR payload.
 * Format: `BLPPT_` + template ID zero-padded to 6 digits.
 * Example: 18472 → BLPPT_018472
 */
object BlpptPayload {
    private const val PREFIX = "BLPPT_"
    private const val WIDTH = 6

    fun fromTemplateId(templateId: Int): String {
        require(templateId > 0) { "templateId must be positive" }
        return PREFIX + templateId.toString().padStart(WIDTH, '0')
    }

    fun tryParseTemplateId(payload: String): Int? {
        val trimmed = payload.trim()
        if (!trimmed.startsWith(PREFIX, ignoreCase = true)) return null
        val digits = trimmed.substring(PREFIX.length)
        return digits.toIntOrNull()?.takeIf { it > 0 }
    }

    /** Effective scan payload: manual override wins, else BLPPT from template ID. */
    fun resolve(config: QrVariantConfig): String? {
        config.payload?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val id = config.templateIdHint ?: return null
        return fromTemplateId(id)
    }
}
