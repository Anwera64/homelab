package com.homelab.household.app.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.wait_slow_hub
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.util.WaitPhase
import org.jetbrains.compose.resources.stringResource

/**
 * The third beat of a wait (design notes §6.21): at `motion.slow` a quiet line joins whatever
 * pattern is already on show.
 *
 * Deliberately muted rather than error-red, and deliberately not the screen's Unreachable wording.
 * Nothing has failed — the bar is still running and the hub may still answer — so the line says
 * only that this is taking longer than usual. The failure wording each screen already has takes
 * over when there really is one.
 */
@Composable
fun SlowLine(phase: WaitPhase, modifier: Modifier = Modifier) {
    if (phase != WaitPhase.Slow) return

    Text(
        text = stringResource(Res.string.wait_slow_hub),
        style = HearthTheme.typography.caption,
        color = HearthTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    )
}
