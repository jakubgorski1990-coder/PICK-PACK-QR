package pl.pickpack.panel.domain

/**
 * Pure selection of which QR to show for a group.
 *
 * Rules:
 * - Active courier set → exact mask match among 7 variants (never a “similar” one).
 * - Empty active set → [QrDisplayState.AllPickedUp] (hide QR).
 * - Missing / disabled / corrupt configured variant → [QrDisplayState.Missing] immediately.
 * - Production payload = manual override OR BLPPT_ from template ID; never DEMO for missing ID.
 */
object QrSelectionLogic {

    fun select(
        groupId: GroupId,
        activeCourierIds: Set<String>,
        variants: Map<Int, QrVariantConfig>,
        demoMode: Boolean,
        demoVariants: Map<Int, QrVariantConfig> = emptyMap(),
    ): QrDisplayState {
        val key = QrVariantKey.fromActiveCouriers(groupId, activeCourierIds)
            ?: return QrDisplayState.AllPickedUp(groupId)

        val configured = variants[key.mask]
        if (configured != null) {
            return resolveConfigured(key, configured)
        }

        // Demo only when explicitly enabled — never silent substitute for missing production ID.
        if (demoMode) {
            val demo = demoVariants[key.mask]
            if (demo != null) {
                return resolveConfigured(key, demo.copy(isDemo = true))
            }
        }

        return QrDisplayState.Missing(groupId, key, MissingReason.NOT_CONFIGURED)
    }

    private fun resolveConfigured(key: QrVariantKey, config: QrVariantConfig): QrDisplayState {
        if (!config.enabled) {
            return QrDisplayState.Missing(key.groupId, key, MissingReason.DISABLED)
        }
        val payload = BlpptPayload.resolve(config)
        if (payload.isNullOrBlank() && config.fallbackImagePath.isNullOrBlank()) {
            val reason = if (config.templateIdHint == null && config.payload.isNullOrBlank()) {
                MissingReason.NOT_CONFIGURED
            } else {
                MissingReason.CORRUPT
            }
            return QrDisplayState.Missing(key.groupId, key, reason)
        }
        return QrDisplayState.Ready(
            variantKey = key,
            payload = payload?.ifBlank { null } ?: "IMAGE_FALLBACK",
            isDemo = config.isDemo,
            fallbackImagePath = config.fallbackImagePath,
            templateIdHint = config.templateIdHint,
        )
    }

    fun allVariantKeys(): List<QrVariantKey> =
        GroupId.entries.flatMap { QrVariantKey.allForGroup(it) }
}
