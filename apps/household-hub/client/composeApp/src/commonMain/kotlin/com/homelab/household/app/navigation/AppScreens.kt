package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import com.homelab.household.app.screens.launch.LaunchScreen
import com.homelab.household.app.screens.placeholder.PlaceholderContent

/**
 * The screens [AppNavHost] can show, behind one seam.
 *
 * Navigation only needs to know that a destination draws something and reports back; what a
 * screen does before it calls back is its own business. Keeping that behind an interface lets
 * the navigation tests stub the screens out and test the back stack alone.
 */
interface AppScreens {
    @Composable fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit)

    @Composable fun SignIn()

    @Composable fun FirstRun()
}

/** The screens the app actually runs. */
object RealAppScreens : AppScreens {

    @Composable
    override fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit) {
        LaunchScreen(onSignIn = onSignIn, onFirstRun = onFirstRun)
    }

    @Composable
    override fun SignIn() {
        PlaceholderContent(
            title = "Sign in",
            detail = "The profile picker and PIN arrive in slice 1."
        )
    }

    @Composable
    override fun FirstRun() {
        PlaceholderContent(
            title = "First run",
            detail = "Setting up the first account arrives in slice 1."
        )
    }
}
