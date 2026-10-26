package pl.pickpack.panel.domain

/**
 * Pure selection of which QR to show for a group.
 *
 * Rules:
 * - Active courier set → exact mask match among 7 variants (never a “similar” one).
 * - Empty active set → [QrDisplayState.AllPickedUp] (hide QR).
 * - Missing / disabled / corrupt configured variant → [QrDisplayState.Missing] immediately
 *   (previous QR must not remain).
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

        // Demo never substitutes for missing production codes.
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
        val payload = config.payload?.trim().orEmpty()
        if (payload.isEmpty() && config.fallbackImagePath.isNullOrBlank()) {
            return QrDisplayState.Missing(key.groupId, key, MissingReason.CORRUPT)
        }
        return QrDisplayState.Ready(
            variantKey = key,
            payload = payload.ifEmpty { "IMAGE_FALLBACK" },
            isDemo = config.isDemo,
            fallbackImagePath = config.fallbackImagePath,
            templateIdHint = config.templateIdHint,
        )
    }

    /** All 14 variant keys (7 × 2 groups). */
    fun allVariantKeys(): List<QrVariantKey> =
        GroupId.entries.flatMap { QrVariantKey.allForGroup(it) }
}
