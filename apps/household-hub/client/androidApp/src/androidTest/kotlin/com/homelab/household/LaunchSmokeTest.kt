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
    fun main_activity_reaches_resumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /** The launcher shows HyggeHub's own icon, not the Android default. */
    @Test
    fun the_app_uses_the_hyggehub_launcher_icon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals(R.mipmap.ic_launcher, app.icon)
    }

    /** Compose draws the whole screen; a platform action bar would sit on top of it. */
    @Test
    fun main_activity_has_no_action_bar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertNull(activity.actionBar) }
        }
    }
}
