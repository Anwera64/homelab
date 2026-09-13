package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.home_detail
import com.homelab.household.app.resources.home_title
import com.homelab.household.app.resources.sign_in_detail
import com.homelab.household.app.resources.sign_in_title
import com.homelab.household.app.screens.firstrun.FirstRunScreen
import com.homelab.household.app.screens.launch.LaunchScreen
import com.homelab.household.app.screens.placeholder.PlaceholderContent
import org.jetbrains.compose.resources.stringResource

/**
 * The screens [AppNavHost] can show, behind one seam.
 *
 * Navigation only needs to know that a destination draws something and reports back; what a
 * screen does before it calls back is its own business. Keeping that behind an interface lets
 * the navigation tests stub the screens out and test the back stack alone.
 */
interface AppScreens {
    @Composable fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit, onSignedIn: () -> Unit)

    @Composable fun SignIn()

    @Composable fun FirstRun(onCreated: () -> Unit, onSignIn: () -> Unit)

    @Composable fun Home()
}

/** The screens the app actually runs. */
object RealAppScreens : AppScreens {

    @Composable
    override fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit, onSignedIn: () -> Unit) {
        LaunchScreen(onSignIn = onSignIn, onFirstRun = onFirstRun, onSignedIn = onSignedIn)
    }

    @Composable
    override fun SignIn() {
        PlaceholderContent(
            title = stringResource(Res.string.sign_in_title),
            detail = stringResource(Res.string.sign_in_detail)
        )
    }

    @Composable
    override fun FirstRun(onCreated: () -> Unit, onSignIn: () -> Unit) {
        FirstRunScreen(onCreated = onCreated, onSignIn = onSignIn)
    }

    @Composable
    override fun Home() {
        PlaceholderContent(
            title = stringResource(Res.string.home_title),
            detail = stringResource(Res.string.home_detail)
        )
    }
}
