package com.mtplayer.app.designsystem

import com.mtplayer.app.designsystem.components.DayPart
import com.mtplayer.app.designsystem.components.Greeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GreetingTest {

    @Test fun everyHourMapsToTheExpectedPart() {
        val expected = (0..23).associateWith { h ->
            when (h) {
                in 5..11 -> DayPart.MORNING
                in 12..16 -> DayPart.AFTERNOON
                in 17..21 -> DayPart.EVENING
                else -> DayPart.NIGHT
            }
        }
        for ((hour, part) in expected) assertEquals("hour $hour", part, Greeting.dayPartFor(hour))
    }

    @Test fun boundaries() {
        assertEquals(DayPart.NIGHT, Greeting.dayPartFor(4))
        assertEquals(DayPart.MORNING, Greeting.dayPartFor(5))
        assertEquals(DayPart.MORNING, Greeting.dayPartFor(11))
        assertEquals(DayPart.AFTERNOON, Greeting.dayPartFor(12))
        assertEquals(DayPart.AFTERNOON, Greeting.dayPartFor(16))
        assertEquals(DayPart.EVENING, Greeting.dayPartFor(17))
        assertEquals(DayPart.EVENING, Greeting.dayPartFor(21))
        assertEquals(DayPart.NIGHT, Greeting.dayPartFor(22))
        assertEquals(DayPart.NIGHT, Greeting.dayPartFor(0))
    }

    @Test fun nowUsesTheCalendarHour() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka")).apply { set(Calendar.HOUR_OF_DAY, 18) }
        assertEquals(DayPart.EVENING, Greeting.now(cal))
    }

    @Test fun millisToNextHourIsWithinOneHour() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 19, 10, 59, 30); set(Calendar.MILLISECOND, 0)
        }
        assertEquals(30_000L, Greeting.millisToNextHour(cal))
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        assertEquals(3_600_000L, Greeting.millisToNextHour(cal))
        // Day rollover at 23:xx still lands on the next midnight.
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 45)
        val ms = Greeting.millisToNextHour(cal)
        assertTrue(ms in 1..3_600_000L)
        assertEquals(15 * 60_000L, ms)
    }
}
