package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class CourierDeadlineLogicTest {
    private val zone = CourierDeadlineLogic.WARSAW

    @Test
    fun firesAtExactly30And25Before() {
        val day = LocalDate.of(2026, 10, 26)
        val deadline = LocalTime.of(14, 0)
        val at30 = ZonedDateTime.of(day, LocalTime.of(13, 30), zone)
        val at25 = ZonedDateTime.of(day, LocalTime.of(13, 35), zone)
        val atOther = ZonedDateTime.of(day, LocalTime.of(13, 31), zone)

        assertThat(CourierDeadlineLogic.slotsDueNow(at30, 14, 0))
            .containsExactly(DeadlineSlot.MIN_30)
        assertThat(CourierDeadlineLogic.slotsDueNow(at25, 14, 0))
            .containsExactly(DeadlineSlot.MIN_25)
        assertThat(CourierDeadlineLogic.slotsDueNow(atOther, deadline.hour, deadline.minute))
            .isEmpty()
    }

    @Test
    fun dueEvents_skipsAlreadyPlayed() {
        val day = LocalDate.of(2026, 10, 26)
        val now = ZonedDateTime.of(day, LocalTime.of(13, 30), zone)
        val cfg = CourierDeadlineConfig(
            enabled = true,
            deadlines = listOf(CourierDeadline("orlen", 14, 0)),
        )
        val key = CourierDeadlineLogic.playKey(day, "orlen", DeadlineSlot.MIN_30)
        assertThat(CourierDeadlineLogic.dueEvents(now, cfg, emptySet()))
            .containsExactly("orlen" to DeadlineSlot.MIN_30)
        assertThat(CourierDeadlineLogic.dueEvents(now, cfg, setOf(key))).isEmpty()
    }

    @Test
    fun disabledOrUnset_noEvents() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 10, 26), LocalTime.of(13, 30), zone)
        assertThat(
            CourierDeadlineLogic.dueEvents(
                now,
                CourierDeadlineConfig(enabled = false, deadlines = listOf(CourierDeadline("dhl", 14, 0))),
                emptySet(),
            )
        ).isEmpty()
        assertThat(
            CourierDeadlineLogic.dueEvents(
                now,
                CourierDeadlineConfig(deadlines = listOf(CourierDeadline("dhl"))),
                emptySet(),
            )
        ).isEmpty()
    }
}
