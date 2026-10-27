package pl.pickpack.panel.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import pl.pickpack.panel.domain.AppSettings
import pl.pickpack.panel.domain.CourierState
import pl.pickpack.panel.domain.DefaultCouriers
import pl.pickpack.panel.domain.DeliveryMapping
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.CourierDeadline
import pl.pickpack.panel.domain.CourierDeadlineConfig
import pl.pickpack.panel.domain.NamedStatusAlertConfig
import pl.pickpack.panel.domain.NamedStatusRef
import pl.pickpack.panel.domain.PoolFilterConfig
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pick_pack_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val visibleCodeCount = intPreferencesKey("visible_code_count")
        val groupOrder = stringPreferencesKey("group_order")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val pinEnabled = booleanPreferencesKey("pin_enabled")
        val pinHash = stringPreferencesKey("pin_hash")
        val demoMode = booleanPreferencesKey("demo_mode")
        val dailyResetEnabled = booleanPreferencesKey("daily_reset_enabled")
        val dailyResetHour = intPreferencesKey("daily_reset_hour")
        val dailyResetMinute = intPreferencesKey("daily_reset_minute")
        val timezoneId = stringPreferencesKey("timezone_id")
        val courierStates = stringPreferencesKey("courier_states")
        val lastResetDay = stringPreferencesKey("last_reset_day")
        val undoSnapshot = stringPreferencesKey("undo_snapshot")
        val alertThreshold = booleanPreferencesKey("alert_threshold")
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val poolFilterJson = stringPreferencesKey("pool_filter_json")
        val imienneTimeoutSec = intPreferencesKey("imienne_timeout_sec")
        val namedStatusAlertJson = stringPreferencesKey("named_status_alert_json")
        val namedStatusSeeded = booleanPreferencesKey("named_status_seeded")
        val courierDeadlinesJson = stringPreferencesKey("courier_deadlines_json")
        val courierDeadlinePlayedJson = stringPreferencesKey("courier_deadline_played_json")
        val courierToggleSound = booleanPreferencesKey("courier_toggle_sound")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs.toAppSettings()
    }

    val courierStatesFlow: Flow<List<CourierState>> = context.dataStore.data.map { prefs ->
        parseCourierStates(prefs[Keys.courierStates])
    }

    val poolFilterFlow: Flow<PoolFilterConfig> = context.dataStore.data.map { prefs ->
        parsePoolFilter(prefs[Keys.poolFilterJson])
    }

    val namedStatusAlertFlow: Flow<NamedStatusAlertConfig> = context.dataStore.data.map { prefs ->
        parseNamedStatusAlert(prefs[Keys.namedStatusAlertJson])
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs.toAppSettings()
            val next = transform(current)
            prefs[Keys.visibleCodeCount] = next.visibleCodeCount.coerceIn(1, 2)
            prefs[Keys.groupOrder] = next.groupOrder.joinToString(",") { it.storageKey }
            prefs[Keys.keepScreenOn] = next.keepScreenOn
            prefs[Keys.pinEnabled] = next.pinEnabled
            if (next.pinHash != null) prefs[Keys.pinHash] = next.pinHash else prefs.remove(Keys.pinHash)
            prefs[Keys.demoMode] = next.demoMode
            prefs[Keys.dailyResetEnabled] = next.dailyResetEnabled
            prefs[Keys.dailyResetHour] = next.dailyResetHour
            prefs[Keys.dailyResetMinute] = next.dailyResetMinute
            prefs[Keys.timezoneId] = next.timezoneId
            prefs[Keys.alertThreshold] = next.alertThresholdEnabled
            prefs[Keys.ttsEnabled] = next.ttsEnabled
            prefs[Keys.poolFilterJson] = encodePoolFilter(next.poolFilter)
            prefs[Keys.imienneTimeoutSec] = next.imienneTimeoutSec.coerceIn(5, 300)
            prefs[Keys.namedStatusAlertJson] = encodeNamedStatusAlert(next.namedStatusAlert)
            prefs[Keys.courierDeadlinesJson] = encodeCourierDeadlines(next.courierDeadlines)
            prefs[Keys.courierToggleSound] = next.courierToggleSoundEnabled
        }
    }

    suspend fun updatePoolFilter(transform: (PoolFilterConfig) -> PoolFilterConfig) {
        context.dataStore.edit { prefs ->
            val current = parsePoolFilter(prefs[Keys.poolFilterJson])
            prefs[Keys.poolFilterJson] = encodePoolFilter(transform(current))
        }
    }

    suspend fun updateNamedStatusAlert(transform: (NamedStatusAlertConfig) -> NamedStatusAlertConfig) {
        context.dataStore.edit { prefs ->
            val current = parseNamedStatusAlert(prefs[Keys.namedStatusAlertJson])
            prefs[Keys.namedStatusAlertJson] = encodeNamedStatusAlert(transform(current))
        }
    }

    /** Seeds the 10 default imienny statuses once. */
    suspend fun seedNamedStatusesIfNeeded() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.namedStatusSeeded] == true) return@edit
            val current = parseNamedStatusAlert(prefs[Keys.namedStatusAlertJson])
            val seeded = if (current.statuses.isEmpty()) {
                current.copy(statuses = NamedStatusAlertConfig.defaultSeed())
            } else {
                current
            }
            prefs[Keys.namedStatusAlertJson] = encodeNamedStatusAlert(seeded)
            prefs[Keys.namedStatusSeeded] = true
        }
    }

    suspend fun updateCourierDeadlines(transform: (CourierDeadlineConfig) -> CourierDeadlineConfig) {
        context.dataStore.edit { prefs ->
            val current = parseCourierDeadlines(prefs[Keys.courierDeadlinesJson])
            prefs[Keys.courierDeadlinesJson] = encodeCourierDeadlines(transform(current))
        }
    }

    val courierDeadlinePlayedFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parsePlayedKeys(prefs[Keys.courierDeadlinePlayedJson])
    }

    suspend fun markCourierDeadlinePlayed(keys: Collection<String>) {
        if (keys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = parsePlayedKeys(prefs[Keys.courierDeadlinePlayedJson]).toMutableSet()
            current.addAll(keys)
            prefs[Keys.courierDeadlinePlayedJson] = JSONArray(current.toList()).toString()
        }
    }

    suspend fun clearCourierDeadlinePlayed() {
        context.dataStore.edit { prefs ->
            prefs[Keys.courierDeadlinePlayedJson] = "[]"
        }
    }

    suspend fun setCourierStates(states: List<CourierState>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.courierStates] = encodeCourierStates(states)
        }
    }

    suspend fun saveUndoSnapshot(states: List<CourierState>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.undoSnapshot] = encodeCourierStates(states)
        }
    }

    suspend fun consumeUndoSnapshot(): List<CourierState>? {
        var snapshot: List<CourierState>? = null
        context.dataStore.edit { prefs ->
            val raw = prefs[Keys.undoSnapshot]
            if (raw != null) {
                snapshot = parseCourierStates(raw)
                prefs.remove(Keys.undoSnapshot)
            }
        }
        return snapshot
    }

    suspend fun verifyPin(pin: String): Boolean {
        var hash: String? = null
        var enabled = false
        context.dataStore.edit { prefs ->
            enabled = prefs[Keys.pinEnabled] ?: false
            hash = prefs[Keys.pinHash]
        }
        if (!enabled) return true
        return hash != null && hash == hashPin(pin)
    }

    suspend fun setPin(pin: String?) {
        context.dataStore.edit { prefs ->
            if (pin.isNullOrBlank()) {
                prefs[Keys.pinEnabled] = false
                prefs.remove(Keys.pinHash)
            } else {
                prefs[Keys.pinEnabled] = true
                prefs[Keys.pinHash] = hashPin(pin)
            }
        }
    }

    suspend fun markResetDay(dayKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.lastResetDay] = dayKey
        }
    }

    suspend fun lastResetDay(): String? {
        var value: String? = null
        context.dataStore.edit { prefs -> value = prefs[Keys.lastResetDay] }
        return value
    }

    companion object {
        fun hashPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun defaultCourierStates(): List<CourierState> =
            DefaultCouriers.ALL.map {
                CourierState(
                    id = it.id,
                    groupId = it.groupId,
                    displayName = it.defaultName,
                    isActive = true,
                )
            }

        private fun Preferences.toAppSettings(): AppSettings {
            val pool = parsePoolFilter(this[Keys.poolFilterJson])
            return AppSettings(
                visibleCodeCount = this[Keys.visibleCodeCount] ?: 2,
                groupOrder = parseGroupOrder(this[Keys.groupOrder]),
                keepScreenOn = this[Keys.keepScreenOn] ?: true,
                pinEnabled = this[Keys.pinEnabled] ?: false,
                pinHash = this[Keys.pinHash],
                demoMode = this[Keys.demoMode] ?: false,
                dailyResetEnabled = this[Keys.dailyResetEnabled] ?: false,
                dailyResetHour = this[Keys.dailyResetHour] ?: 5,
                dailyResetMinute = this[Keys.dailyResetMinute] ?: 0,
                timezoneId = this[Keys.timezoneId] ?: "Europe/Warsaw",
                alertThresholdEnabled = this[Keys.alertThreshold] ?: false,
                ttsEnabled = this[Keys.ttsEnabled] ?: false,
                poolFilter = pool,
                counterConfigured = pool.isCounterRulesReady(),
                imienneTimeoutSec = this[Keys.imienneTimeoutSec] ?: 15,
                namedStatusAlert = parseNamedStatusAlert(this[Keys.namedStatusAlertJson]),
                courierDeadlines = parseCourierDeadlines(this[Keys.courierDeadlinesJson]),
                courierToggleSoundEnabled = this[Keys.courierToggleSound] ?: true,
            )
        }

        private fun parseGroupOrder(raw: String?): List<GroupId> {
            if (raw.isNullOrBlank()) return listOf(GroupId.PICK_PACK_1, GroupId.PICK_PACK_2)
            val parsed = raw.split(",")
                .mapNotNull { GroupId.fromStorageKey(it.trim()) }
            return parsed.ifEmpty { listOf(GroupId.PICK_PACK_1, GroupId.PICK_PACK_2) }
        }

        private fun encodeCourierStates(states: List<CourierState>): String {
            val arr = JSONArray()
            states.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("group", s.groupId.storageKey)
                        .put("name", s.displayName)
                        .put("active", s.isActive)
                )
            }
            return arr.toString()
        }

        private fun parseCourierStates(raw: String?): List<CourierState> {
            if (raw.isNullOrBlank()) return defaultCourierStates()
            return try {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val group = GroupId.fromStorageKey(o.getString("group")) ?: continue
                        add(
                            CourierState(
                                id = o.getString("id"),
                                groupId = group,
                                displayName = o.optString("name"),
                                isActive = o.optBoolean("active", true),
                            )
                        )
                    }
                }.ifEmpty { defaultCourierStates() }
            } catch (_: Exception) {
                defaultCourierStates()
            }
        }

        fun encodePoolFilter(cfg: PoolFilterConfig): String {
            val root = JSONObject()
            root.put("syncIntervalSec", cfg.syncIntervalSec)
            root.put("onlyConfirmed", cfg.onlyConfirmed)
            root.put("estimatedAlertsEnabled", cfg.estimatedAlertsEnabled)
            root.put("statusIds", JSONArray(cfg.statusIds))
            val maps = JSONArray()
            cfg.deliveryMappings
                .groupBy { it.courierId }
                .map { (id, rows) -> DeliveryMapping.mergeForCourier(id, rows).normalized() }
                .filter { it.isValid() }
                .forEach { m ->
                    val patterns = JSONArray()
                    m.namePatterns.forEach { patterns.put(it) }
                    maps.put(
                        JSONObject()
                            .put("courierId", m.courierId)
                            .put("deliveryMethodId", m.deliveryMethodId)
                            .put("namePatterns", patterns)
                    )
                }
            root.put("deliveryMappings", maps)
            return root.toString()
        }

        fun parsePoolFilter(raw: String?): PoolFilterConfig {
            if (raw.isNullOrBlank()) return PoolFilterConfig()
            return try {
                val o = JSONObject(raw)
                val statuses = o.optJSONArray("statusIds") ?: JSONArray()
                val statusIds = buildList {
                    for (i in 0 until statuses.length()) add(statuses.getInt(i))
                }
                val mapsArr = o.optJSONArray("deliveryMappings") ?: JSONArray()
                val rawMaps = buildList {
                    for (i in 0 until mapsArr.length()) {
                        val m = mapsArr.getJSONObject(i)
                        val patternsArr = m.optJSONArray("namePatterns")
                        val patterns = buildList {
                            if (patternsArr != null) {
                                for (j in 0 until patternsArr.length()) {
                                    patternsArr.optString(j).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                                }
                            }
                        }
                        add(
                            DeliveryMapping(
                                courierId = m.optString("courierId"),
                                deliveryMethodId = if (m.has("deliveryMethodId") && !m.isNull("deliveryMethodId")) {
                                    m.getInt("deliveryMethodId")
                                } else null,
                                nameContains = m.optString("nameContains").ifBlank { null },
                                namePatterns = patterns,
                            )
                        )
                    }
                }
                val maps = rawMaps
                    .groupBy { it.courierId }
                    .map { (id, rows) -> DeliveryMapping.mergeForCourier(id, rows).normalized() }
                    .filter { it.isValid() }
                PoolFilterConfig(
                    syncIntervalSec = o.optInt("syncIntervalSec", 60).coerceIn(15, 600),
                    onlyConfirmed = o.optBoolean("onlyConfirmed", true),
                    estimatedAlertsEnabled = o.optBoolean("estimatedAlertsEnabled", false),
                    statusIds = statusIds,
                    deliveryMappings = maps,
                )
            } catch (_: Exception) {
                PoolFilterConfig()
            }
        }

        fun encodeNamedStatusAlert(cfg: NamedStatusAlertConfig): String {
            val root = JSONObject()
            root.put("enabled", cfg.enabled)
            root.put("thresholdMinutes", cfg.thresholdMinutes)
            root.put("cooldownMinutes", cfg.cooldownMinutes)
            val arr = JSONArray()
            cfg.statuses.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("statusId", s.statusId)
                        .put("displayName", s.displayName)
                )
            }
            root.put("statuses", arr)
            return root.toString()
        }

        fun parseNamedStatusAlert(raw: String?): NamedStatusAlertConfig {
            if (raw.isNullOrBlank()) {
                return NamedStatusAlertConfig(statuses = emptyList())
            }
            return try {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("statuses") ?: JSONArray()
                val statuses = buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        val id = s.optInt("statusId", 0)
                        val name = s.optString("displayName")
                        if (id > 0 && name.isNotBlank()) {
                            add(NamedStatusRef(id, name))
                        }
                    }
                }
                NamedStatusAlertConfig(
                    enabled = o.optBoolean("enabled", true),
                    thresholdMinutes = o.optInt("thresholdMinutes", 30).coerceIn(1, 24 * 60),
                    cooldownMinutes = o.optInt("cooldownMinutes", 10).coerceIn(1, 120),
                    statuses = statuses,
                )
            } catch (_: Exception) {
                NamedStatusAlertConfig()
            }
        }

        fun encodeCourierDeadlines(cfg: CourierDeadlineConfig): String {
            val root = JSONObject()
            root.put("enabled", cfg.enabled)
            val arr = JSONArray()
            cfg.deadlines.forEach { d ->
                arr.put(
                    JSONObject()
                        .put("courierId", d.courierId)
                        .put("hour", d.hour)
                        .put("minute", d.minute)
                )
            }
            root.put("deadlines", arr)
            return root.toString()
        }

        fun parseCourierDeadlines(raw: String?): CourierDeadlineConfig {
            val defaults = DefaultCouriers.ALL.map { CourierDeadline(it.id) }
            if (raw.isNullOrBlank()) return CourierDeadlineConfig(deadlines = defaults)
            return try {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("deadlines") ?: JSONArray()
                val parsed = buildList {
                    for (i in 0 until arr.length()) {
                        val d = arr.getJSONObject(i)
                        add(
                            CourierDeadline(
                                courierId = d.optString("courierId"),
                                hour = if (d.has("hour") && !d.isNull("hour")) d.getInt("hour") else null,
                                minute = if (d.has("minute") && !d.isNull("minute")) d.getInt("minute") else null,
                            )
                        )
                    }
                }.filter { it.courierId.isNotBlank() }
                val byId = parsed.associateBy { it.courierId }
                CourierDeadlineConfig(
                    enabled = o.optBoolean("enabled", true),
                    deadlines = DefaultCouriers.ALL.map { def ->
                        byId[def.id] ?: CourierDeadline(def.id)
                    },
                )
            } catch (_: Exception) {
                CourierDeadlineConfig(deadlines = defaults)
            }
        }

        fun parsePlayedKeys(raw: String?): Set<String> {
            if (raw.isNullOrBlank()) return emptySet()
            return try {
                val arr = JSONArray(raw)
                buildSet {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
            } catch (_: Exception) {
                emptySet()
            }
        }
    }
}
