package pl.pickpack.panel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class DailyResetLogicTest {
    private val zone = DailyResetLogic.WARSAW

    private fun at(hour: Int, minute: Int, day: LocalDate = LocalDate.of(2026, 11, 9)): ZonedDateTime =
        ZonedDateTime.of(day, LocalTime.of(hour, minute), zone)

    @Test
    fun defaultsMatchFifteenThirty() {
        val s = AppSettings()
        assertEquals(15, s.dailyResetHour)
        assertEquals(30, s.dailyResetMinute)
        assertFalse(s.dailyResetEnabled)
    }

    @Test
    fun disabledNeverFires() {
        assertFalse(
            DailyResetLogic.shouldReset(
                enabled = false,
                now = at(15, 30),
                hour = 15,
                minute = 30,
                lastResetDay = null,
            ),
        )
    }

    @Test
    fun beforeThresholdDoesNotFire() {
        assertFalse(
            DailyResetLogic.shouldReset(
                enabled = true,
                now = at(15, 29),
                hour = 15,
                minute = 30,
                lastResetDay = null,
            ),
        )
    }

    @Test
    fun atAndAfterThresholdFiresOncePerDay() {
        assertTrue(
            DailyResetLogic.shouldReset(
                enabled = true,
                now = at(15, 30),
                hour = 15,
                minute = 30,
                lastResetDay = null,
            ),
        )
        assertTrue(
            DailyResetLogic.shouldReset(
                enabled = true,
                now = at(16, 0),
                hour = 15,
                minute = 30,
                lastResetDay = null,
            ),
        )
        assertFalse(
            DailyResetLogic.shouldReset(
                enabled = true,
                now = at(16, 0),
                hour = 15,
                minute = 30,
                lastResetDay = "2026-11-09",
            ),
        )
    }

    @Test
    fun customEditableTimeIsHonored() {
        assertFalse(
            DailyResetLogic.shouldReset(
                enabled = true,
                now = at(9, 59),
                hour = 10,
                minute = 0,
                lastResetDay = null,
            ),
        )
        assertTrue(
            DailyResetLogic.shouldReset(
                enabled = true,
                now = at(10, 0),
                hour = 10,
                minute = 0,
                lastResetDay = null,
            ),
        )
    }

    @Test
    fun dayKeyUsesLocalCalendarDate() {
        assertEquals("2026-11-09", DailyResetLogic.dayKey(at(23, 59)))
        assertEquals(
            "2026-11-10",
            DailyResetLogic.dayKey(at(0, 0, LocalDate.of(2026, 11, 10))),
        )
    }
}
