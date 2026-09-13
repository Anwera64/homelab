package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.home_detail
import com.homelab.household.app.resources.home_title
import com.homelab.household.app.screens.firstrun.FirstRunScreen
import com.homelab.household.app.screens.launch.LaunchScreen
import com.homelab.household.app.screens.pinentry.PinEntryScreen
import com.homelab.household.app.screens.placeholder.PlaceholderContent
import com.homelab.household.app.screens.profilepicker.ProfilePickerScreen
import com.homelab.household.domain.model.Member
import org.jetbrains.compose.resources.stringResource

/**
 * The screens [AppNavHost] can show, behind one seam.
 *
 * Navigation only needs to know that a destination draws something and reports back; what a
 * screen does before it calls back is its own business. Keeping that behind an interface lets
 * the navigation tests stub the screens out and test the back stack alone.
 */
interface AppScreens {
    @Composable fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit)

    @Composable fun SignIn(onMemberSelected: (Member) -> Unit)

    @Composable fun Pin(member: Member, onSignedIn: () -> Unit, onBack: () -> Unit)

    @Composable fun FirstRun(onCreated: () -> Unit, onSignIn: () -> Unit)

    @Composable fun Home()
}

/** The screens the app actually runs. */
object RealAppScreens : AppScreens {

    @Composable
    override fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit) {
        LaunchScreen(onSignIn = onSignIn, onFirstRun = onFirstRun)
    }

    @Composable
    override fun SignIn(onMemberSelected: (Member) -> Unit) {
        ProfilePickerScreen(onMemberSelected = onMemberSelected)
    }

    @Composable
    override fun Pin(member: Member, onSignedIn: () -> Unit, onBack: () -> Unit) {
        PinEntryScreen(member = member, onSignedIn = onSignedIn, onBack = onBack)
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
