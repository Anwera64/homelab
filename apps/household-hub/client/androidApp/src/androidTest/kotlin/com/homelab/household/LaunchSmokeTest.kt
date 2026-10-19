package com.homelab.household

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The app starts, Koin wires up and the launch screen composes without crashing. */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @Test
    fun GIVEN_the_app_is_installed_WHEN_the_main_screen_is_launched_and_resumed_THEN_it_stays_resumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /** The launcher shows HyggeHub's own icon, not the Android default. */
    @Test
    fun GIVEN_the_installed_app_WHEN_its_application_info_is_read_THEN_the_launcher_icon_is_the_HyggeHub_one() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals(R.mipmap.ic_launcher, app.icon)
    }

    /** Compose draws the whole screen; a platform action bar would sit on top of it. */
    @Test
    fun GIVEN_the_main_screen_is_launched_WHEN_its_action_bar_is_looked_up_THEN_there_is_none() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertNull(activity.actionBar) }
        }
    }
}
