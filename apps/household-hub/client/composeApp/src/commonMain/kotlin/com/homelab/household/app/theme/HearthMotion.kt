package com.homelab.household.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long waiting takes, and whether it moves at all (design notes §3, §6.21). Read it from the
 * theme — `HearthTheme.motion.hold` — for the same reason space and size come through the theme:
 * the four beats of a wait are decided once, so no screen can invent its own.
 *
 * **A Compose UI test that does not provide [StillMotion] will hang, not fail.** The test runtime
 * synchronises on idleness, and an infinite animation never lets the composition go idle, so
 * `waitForIdle`, `waitUntil` and every `onNode…` time out instead of reporting anything useful.
 * `TestApp` provides [StillMotion] for exactly this reason; a component that animates must read
 * [animate] and draw a resting frame when it is false.
 */
@Immutable
data class HearthMotion(
    /** Nothing is drawn for this long: a hub on the LAN answers well inside it. */
    val hold: Duration = 250.milliseconds,
    /** Once a pattern is on show it stays at least this long, so it can never flash. */
    val minimumVisible: Duration = 400.milliseconds,
    /** Longer than usual. The pattern stays and a quiet line joins it; nothing has failed. */
    val slow: Duration = 8.seconds,
    /** One pass of a progress bar along its track. */
    val barCycle: Duration = 1150.milliseconds,
    /** One breath of a skeleton block, dim to full and back. */
    val breathe: Duration = 1600.milliseconds,
    /**
     * How far behind its neighbour each block in a row of them runs. Six blocks standing in for
     * six code characters read as one thing arriving rather than six lights blinking together.
     */
    val breatheStagger: Duration = 60.milliseconds,
    /** One pass of the wave across the PIN pad's six dots. */
    val wave: Duration = 1400.milliseconds,
    /** How far behind its neighbour each dot in that wave runs. */
    val waveStagger: Duration = 90.milliseconds,
    /** How far a dot lifts at the top of the wave. A [Dp], which is why it lives here. */
    val waveLift: Dp = 4.dp,
    /** False draws every animated component's resting frame instead of starting it. */
    val animate: Boolean = true
)

/** The app's own scale, and the only one there is outside tests. */
val DefaultMotion = HearthMotion()

/**
 * Motion off, for tests. [hold] and [minimumVisible] collapse to zero so nothing is time-gated and
 * a screen test never waits on a clock; [slow] keeps its 8 seconds so the slow line cannot turn up
 * by accident in a test that never meant to reach it.
 */
val StillMotion = HearthMotion(
    hold = Duration.ZERO,
    minimumVisible = Duration.ZERO,
    breatheStagger = Duration.ZERO,
    animate = false
)
