package pl.pickpack.panel.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class DeadlineSlot(val minutesBefore: Int, val keySuffix: String) {
    MIN_30(30, "30"),
    MIN_25(25, "25"),
}

data class CourierDeadline(
    val courierId: String,
    /** null hour/minute = disabled for this courier */
    val hour: Int? = null,
    val minute: Int? = null,
) {
    val isSet: Boolean get() = hour != null && minute != null &&
        hour in 0..23 && minute in 0..59
}

data class CourierDeadlineConfig(
    val enabled: Boolean = true,
    val deadlines: List<CourierDeadline> = DefaultCouriers.ALL.map {
        CourierDeadline(it.id)
    },
) {
    fun forCourier(id: String): CourierDeadline =
        deadlines.find { it.courierId == id } ?: CourierDeadline(id)
}

/**
 * Pure deadline slot detection in Europe/Warsaw (or given zone).
 * Fires when current clock minute equals (deadline − 30) or (deadline − 25).
 */
object CourierDeadlineLogic {
    val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")

    fun slotsDueNow(
        now: ZonedDateTime,
        deadlineHour: Int,
        deadlineMinute: Int,
    ): List<DeadlineSlot> {
        val localDate = now.toLocalDate()
        val deadline = ZonedDateTime.of(localDate, LocalTime.of(deadlineHour, deadlineMinute), now.zone)
        val nowMinute = now.withSecond(0).withNano(0)
        return buildList {
            for (slot in DeadlineSlot.entries) {
                val target = deadline.minusMinutes(slot.minutesBefore.toLong())
                    .withSecond(0).withNano(0)
                if (target.toLocalDate() == localDate && target == nowMinute) {
                    add(slot)
                }
            }
        }
    }

    fun playKey(day: LocalDate, courierId: String, slot: DeadlineSlot): String =
        "${day}:$courierId:${slot.keySuffix}"

    fun dueEvents(
        now: ZonedDateTime,
        config: CourierDeadlineConfig,
        alreadyPlayed: Set<String>,
    ): List<Pair<String, DeadlineSlot>> {
        if (!config.enabled) return emptyList()
        val day = now.toLocalDate()
        return buildList {
            for (d in config.deadlines) {
                if (!d.isSet) continue
                val slots = slotsDueNow(now, d.hour!!, d.minute!!)
                for (slot in slots) {
                    val key = playKey(day, d.courierId, slot)
                    if (key !in alreadyPlayed) add(d.courierId to slot)
                }
            }
        }
    }
}
