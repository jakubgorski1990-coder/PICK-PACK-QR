package pl.pickpack.panel.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NamedStatusAlertLogicTest {

    private fun order(
        id: Long,
        statusId: Int,
        enteredSec: Long?,
    ) = PoolOrder(
        orderId = id,
        statusId = statusId,
        deliveryMethodId = null,
        deliveryMethodName = null,
        confirmed = true,
        dateInStatusEpochSec = enteredSec,
    )

    @Test
    fun overdue_whenOlderThanThreshold() {
        val now = 1_700_000_000L
        val orders = listOf(
            order(1, 356748, now - 31 * 60),
            order(2, 356748, now - 10 * 60),
            order(3, 999, now - 100 * 60),
            order(4, 356749, null),
        )
        val overdue = NamedStatusAlertLogic.overdueOrders(
            orders,
            namedStatusIds = setOf(356748, 356749),
            nowEpochSec = now,
            thresholdMinutes = 30,
        )
        assertThat(overdue.map { it.orderId }).containsExactly(1L)
    }

    @Test
    fun shouldPlay_firstBreachImmediate() {
        assertThat(
            NamedStatusAlertLogic.shouldPlay(
                overdueCount = 2,
                wasActive = false,
                lastPlayedEpochMs = null,
                nowEpochMs = 1000,
                cooldownMinutes = 10,
            )
        ).isTrue()
    }

    @Test
    fun shouldPlay_whileActive_respectsCooldownoldown() {
        val now = 1_000_000L
        assertThat(
            NamedStatusAlertLogic.shouldPlay(
                overdueCount = 1,
                wasActive = true,
                lastPlayedEpochMs = now - 5 * 60_000L,
                nowEpochMs = now,
                cooldownMinutes = 10,
            )
        ).isFalse()
        assertThat(
            NamedStatusAlertLogic.shouldPlay(
                overdueCount = 1,
                wasActive = true,
                lastPlayedEpochMs = now - 11 * 60_000L,
                nowEpochMs = now,
                cooldownMinutes = 10,
            )
        ).isTrue()
    }

    @Test
    fun shouldPlay_noOverdue_never() {
        assertThat(
            NamedStatusAlertLogic.shouldPlay(0, wasActive = true, lastPlayedEpochMs = 0, nowEpochMs = 99, cooldownMinutes = 1)
        ).isFalse()
    }

    @Test
    fun defaultSeed_hasTenStatuses() {
        assertThat(NamedStatusAlertConfig.defaultSeed()).hasSize(10)
        assertThat(NamedStatusAlertConfig.defaultSeed().map { it.statusId })
            .containsExactly(
                356748, 356749, 356750, 356751, 356752,
                356753, 356754, 356755, 356756, 356757,
            ).inOrder()
    }
}
