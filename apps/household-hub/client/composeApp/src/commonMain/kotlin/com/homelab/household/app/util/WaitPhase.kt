package com.homelab.household.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.homelab.household.app.theme.HearthTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Where a wait on the hub stands. One definition for every screen, so the hold cannot drift
 * apart between them — the same reason a gap is a spacing step rather than a number per screen.
 */
enum class WaitPhase {
    /** Nothing is drawn. A hub on the LAN answers well inside the hold, so this is the common case. */
    Hidden,

    /** The screen's waiting pattern is on show. */
    Showing,

    /** Still waiting, longer than usual. The pattern stays and a quiet line joins it; nothing has failed. */
    Slow
}

/**
 * The waiting rule: nothing until [hold] has passed, the pattern until [slow], then the quiet line.
 *
 * Deliberately a plain function over [Duration] — no clock, no coroutines, no Compose — so the
 * rule can be tested exactly. `rememberWaitPhase` is what drives it from a real elapsed time.
 */
fun waitPhase(elapsed: Duration, hold: Duration, slow: Duration): WaitPhase = when {
    elapsed < hold -> WaitPhase.Hidden
    elapsed < slow -> WaitPhase.Showing
    else -> WaitPhase.Slow
}

/**
 * [waitPhase] driven by a real clock, for a screen that is [busy].
 *
 * Two rules the pure function cannot carry on its own:
 * - the phase restarts every time a wait does, so a second call gets its own hold;
 * - once a pattern is on show it stays for `motion.minimumVisible` after the answer arrives, which
 *   is what stops a hub replying at 260 ms from flashing a skeleton for a single frame.
 *
 * Under `StillMotion` it short-circuits to [busy] with no delay pending at all. That is not only
 * for speed: a Compose UI test synchronises on idleness, and a coroutine parked on a `delay` under
 * a virtual-time scheduler either holds the test open or fast-forwards it into [WaitPhase.Slow].
 */
@Composable
fun rememberWaitPhase(busy: Boolean): WaitPhase {
    val motion = HearthTheme.motion
    var phase by remember { mutableStateOf(WaitPhase.Hidden) }
    var shownAt by remember { mutableStateOf<TimeMark?>(null) }

    LaunchedEffect(busy, motion) {
        if (!motion.animate) {
            phase = if (busy) WaitPhase.Showing else WaitPhase.Hidden
            return@LaunchedEffect
        }

        if (busy) {
            val started = TimeSource.Monotonic.markNow()
            while (true) {
                val next = waitPhase(started.elapsedNow(), motion.hold, motion.slow)
                if (next != WaitPhase.Hidden && shownAt == null) shownAt = TimeSource.Monotonic.markNow()
                phase = next
                if (next == WaitPhase.Slow) break
                // Sleep to the next threshold rather than polling towards it: the rule is a
                // function of elapsed time, so there is nothing to look at in between.
                val threshold = if (next == WaitPhase.Hidden) motion.hold else motion.slow
                delay(threshold - started.elapsedNow())
            }
        } else {
            // The floor runs from when the pattern appeared, not from now, so a wait that has
            // already outrun it clears immediately.
            val visibleFor = shownAt?.elapsedNow()
            if (visibleFor != null && visibleFor < motion.minimumVisible) {
                delay(motion.minimumVisible - visibleFor)
            }
            shownAt = null
            phase = WaitPhase.Hidden
        }
    }

    return phase
}
