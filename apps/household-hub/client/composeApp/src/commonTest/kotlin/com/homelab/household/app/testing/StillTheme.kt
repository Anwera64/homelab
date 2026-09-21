package com.homelab.household.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.LocalHearthMotion
import com.homelab.household.app.theme.StillMotion

/**
 * The app's theme with the motion switched off. **Every Compose UI test composes this, never
 * `HearthTheme` directly** — `StillMotionInTestsTest` in `:shared` fails the build otherwise.
 *
 * The reason is that the failure mode is silence. A Compose UI test synchronises on idleness, and
 * an infinite animation never lets the composition go idle, so a test that mounts a live one does
 * not fail with something you can read — it hangs until `waitForIdle`, `waitUntil` or `onNode…`
 * times out.
 *
 * `TestApp` composes this too, so there is one definition of "the theme, but still".
 */
@Composable
fun StillTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    HearthTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalHearthMotion provides StillMotion) {
            content()
        }
    }
}
