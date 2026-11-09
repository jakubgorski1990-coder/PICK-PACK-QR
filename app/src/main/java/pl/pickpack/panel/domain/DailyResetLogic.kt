package pl.pickpack.panel.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure daily courier reset gate (Europe/Warsaw by default).
 * Fires once per calendar day after the configured local HH:mm threshold.
 */
object DailyResetLogic {
    val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")

    fun dayKey(now: ZonedDateTime): String = now.toLocalDate().toString()

    fun dayKey(zone: ZoneId = WARSAW): String = LocalDate.now(zone).toString()

    /**
     * @return true when reset should run now (enabled, past HH:mm today, not yet marked).
     */
    fun shouldReset(
        enabled: Boolean,
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
        lastResetDay: String?,
    ): Boolean {
        if (!enabled) return false
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val key = dayKey(now)
        if (lastResetDay == key) return false
        return now.hour > h || (now.hour == h && now.minute >= m)
    }
}
