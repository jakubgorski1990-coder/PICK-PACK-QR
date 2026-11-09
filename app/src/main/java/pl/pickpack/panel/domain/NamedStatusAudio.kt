package pl.pickpack.panel.domain

import pl.pickpack.panel.R

/**
 * Per-person imienny alert audio.
 *
 * Resolution order for a [NamedStatusRef]:
 * 1. Explicit [NamedStatusRef.audioKey] (settings / seed)
 * 2. Known BaseLinker status_id map
 * 3. Display-name heuristic (e.g. "ANIA SZ" → ania_sz, "ANIA" → ania)
 * 4. Fallback → shared [R.raw.uwaga_imienny_status]
 *
 * Add new people by dropping `uwaga_<key>.mp3` into res/raw and registering
 * the key in [rawResForKey] (+ optionally seed audioKey / STATUS_ID_KEYS).
 */
object NamedStatusAudio {

    const val FALLBACK_KEY = "generic"

    /** Built-in status_id → audio key (survives installs that predate audioKey persistence). */
    private val STATUS_ID_KEYS: Map<Int, String> = mapOf(
        356748 to "ania",
        356749 to "ania_sz",
        356750 to "bartek",
        356751 to "jakub",
        356752 to "kamil",
        356753 to "klaudia",
        356754 to "michal",
        // future: 356755 to "ola", …
    )

    /**
     * Registry of custom clips. Unknown / null key → generic shared MP3.
     * Keys must be Android-raw-safe (lowercase snake).
     */
    fun rawResForKey(audioKey: String?): Int = when (normalizeKey(audioKey)) {
        "ania" -> R.raw.uwaga_ania
        "ania_sz" -> R.raw.uwaga_ania_sz
        "bartek" -> R.raw.uwaga_bartek
        "jakub" -> R.raw.uwaga_jakub
        "kamil" -> R.raw.uwaga_kamil
        "klaudia" -> R.raw.uwaga_klaudia
        "michal" -> R.raw.uwaga_michal
        // future: "ola" -> R.raw.uwaga_ola, …
        else -> R.raw.uwaga_imienny_status
    }

    fun isCustomKey(audioKey: String?): Boolean {
        val key = normalizeKey(audioKey) ?: return false
        return key != FALLBACK_KEY && rawResForKey(key) != R.raw.uwaga_imienny_status
    }

    fun resolveAudioKey(ref: NamedStatusRef): String {
        ref.audioKey?.let { normalizeKey(it) }?.let { return it }
        STATUS_ID_KEYS[ref.statusId]?.let { return it }
        inferKeyFromName(ref.displayName)?.let { return it }
        return FALLBACK_KEY
    }

    fun rawResFor(ref: NamedStatusRef): Int = rawResForKey(resolveAudioKey(ref))

    fun hasCustomAudio(ref: NamedStatusRef): Boolean = isCustomKey(resolveAudioKey(ref))

    /** Distinct raw resources for overdue named statuses (stable order by first appearance). */
    fun rawResIdsForOverdue(
        overdueStatusIds: Collection<Int>,
        statuses: List<NamedStatusRef>,
    ): List<Int> {
        if (overdueStatusIds.isEmpty() || statuses.isEmpty()) {
            return listOf(R.raw.uwaga_imienny_status)
        }
        val byId = statuses.associateBy { it.statusId }
        val seen = LinkedHashSet<Int>()
        for (id in overdueStatusIds) {
            val ref = byId[id] ?: continue
            seen.add(rawResFor(ref))
        }
        if (seen.isEmpty()) seen.add(R.raw.uwaga_imienny_status)
        return seen.toList()
    }

    fun inferKeyFromName(displayName: String): String? {
        val n = normalizeName(displayName)
        if (n.isEmpty()) return null
        // More specific first (ANIA SZ before ANIA).
        if (n == "ania_sz" || n.startsWith("ania_sz") || n == "aniasz") return "ania_sz"
        if (n == "ania" || n.startsWith("ania_")) return "ania"
        if (n == "bartek" || n.startsWith("bartek")) return "bartek"
        if (n == "jakub" || n.startsWith("jakub")) return "jakub"
        if (n == "kamil" || n.startsWith("kamil")) return "kamil"
        if (n == "klaudia" || n.startsWith("klaudia")) return "klaudia"
        if (n == "michal" || n.startsWith("michal")) return "michal"
        // future name heuristics (ola, …) go here
        return null
    }

    fun normalizeKey(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val k = raw.trim().lowercase()
            .replace(' ', '_')
            .replace('-', '_')
        return k.takeIf { it.isNotEmpty() }
    }

    private fun normalizeName(displayName: String): String =
        displayName.trim().lowercase()
            .replace('ł', 'l')
            .replace('ś', 's')
            .replace('ć', 'c')
            .replace('ń', 'n')
            .replace('ó', 'o')
            .replace('ż', 'z')
            .replace('ź', 'z')
            .replace('ą', 'a')
            .replace('ę', 'e')
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
}
