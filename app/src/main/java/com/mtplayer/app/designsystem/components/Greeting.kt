package com.mtplayer.app.designsystem.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtplayer.app.R
import kotlinx.coroutines.delay
import java.util.Calendar

/** Part of the day used by the time-based greeting in the Home top bar. */
enum class DayPart(@StringRes val greeting: Int) {
    MORNING(R.string.greeting_morning),
    AFTERNOON(R.string.greeting_afternoon),
    EVENING(R.string.greeting_evening),
    NIGHT(R.string.greeting_night),
}

object Greeting {
    /** 05–11 morning, 12–16 afternoon, 17–21 evening, otherwise night. */
    fun dayPartFor(hour: Int): DayPart = when (hour) {
        in 5..11 -> DayPart.MORNING
        in 12..16 -> DayPart.AFTERNOON
        in 17..21 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }

    fun now(calendar: Calendar = Calendar.getInstance()): DayPart = dayPartFor(calendar.get(Calendar.HOUR_OF_DAY))

    /** Milliseconds until the next full hour of [calendar] (always > 0). */
    fun millisToNextHour(calendar: Calendar = Calendar.getInstance()): Long {
        val next = (calendar.clone() as Calendar).apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return (next.timeInMillis - calendar.timeInMillis).coerceAtLeast(1L)
    }
}

/**
 * The greeting for the current hour. It re-reads the clock whenever the screen resumes and
 * then once at each hour boundary, so it never goes stale and never ticks needlessly.
 */
@Composable
fun rememberGreeting(): String {
    var part by remember { mutableStateOf(Greeting.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                part = Greeting.now()
                delay(Greeting.millisToNextHour() + 500)
            }
        }
    }
    return stringResource(part.greeting)
}
