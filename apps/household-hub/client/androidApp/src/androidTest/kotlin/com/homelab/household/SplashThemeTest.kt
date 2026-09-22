package com.homelab.household

import android.content.ComponentName
import android.util.TypedValue
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The system splash shows the launch tile on the Hearth canvas, then hands the window to the app's
 * own theme — so what Compose draws over sits on the canvas, not on the splash's background.
 */
@RunWith(AndroidJUnit4::class)
class SplashThemeTest {
    @Test
    fun main_activity_opens_with_the_splash_theme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity =
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                0,
            )

        assertEquals(R.style.Theme_HyggeHub_Starting, activity.themeResource)
    }

    @Test
    fun once_resumed_the_window_is_back_on_the_app_theme() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val background = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.windowBackground, background, true)

                // Only Theme.HyggeHub puts the canvas colour behind the window.
                assertEquals(R.color.hearth_canvas, background.resourceId)
            }
        }
    }
}
