package com.homelab.household.app.util

import kotlin.time.Duration

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
