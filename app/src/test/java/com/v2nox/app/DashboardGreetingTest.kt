package com.v2nox.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardGreetingTest {
    @Test
    fun greetingMatchesEveryHour() {
        for (hour in 0..23) {
            val expected = when (hour) {
                in 5..11 -> "Доброе утро!"
                in 12..16 -> "Добрый день!"
                in 17..22 -> "Добрый вечер!"
                else -> "Доброй ночи!"
            }
            assertEquals("Hour: $hour", expected, greetingForHour(hour))
        }
    }
}
