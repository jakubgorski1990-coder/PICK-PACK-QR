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
import pl.pickpack.panel.domain.GroupId
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
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            visibleCodeCount = prefs[Keys.visibleCodeCount] ?: 2,
            groupOrder = parseGroupOrder(prefs[Keys.groupOrder]),
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            pinEnabled = prefs[Keys.pinEnabled] ?: false,
            pinHash = prefs[Keys.pinHash],
            demoMode = prefs[Keys.demoMode] ?: false,
            dailyResetEnabled = prefs[Keys.dailyResetEnabled] ?: false,
            dailyResetHour = prefs[Keys.dailyResetHour] ?: 5,
            dailyResetMinute = prefs[Keys.dailyResetMinute] ?: 0,
            timezoneId = prefs[Keys.timezoneId] ?: "Europe/Warsaw",
            alertThresholdEnabled = prefs[Keys.alertThreshold] ?: false,
            ttsEnabled = prefs[Keys.ttsEnabled] ?: false,
        )
    }

    val courierStatesFlow: Flow<List<CourierState>> = context.dataStore.data.map { prefs ->
        parseCourierStates(prefs[Keys.courierStates])
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                visibleCodeCount = prefs[Keys.visibleCodeCount] ?: 2,
                groupOrder = parseGroupOrder(prefs[Keys.groupOrder]),
                keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
                pinEnabled = prefs[Keys.pinEnabled] ?: false,
                pinHash = prefs[Keys.pinHash],
                demoMode = prefs[Keys.demoMode] ?: false,
                dailyResetEnabled = prefs[Keys.dailyResetEnabled] ?: false,
                dailyResetHour = prefs[Keys.dailyResetHour] ?: 5,
                dailyResetMinute = prefs[Keys.dailyResetMinute] ?: 0,
                timezoneId = prefs[Keys.timezoneId] ?: "Europe/Warsaw",
                alertThresholdEnabled = prefs[Keys.alertThreshold] ?: false,
                ttsEnabled = prefs[Keys.ttsEnabled] ?: false,
            )
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
    }
}
