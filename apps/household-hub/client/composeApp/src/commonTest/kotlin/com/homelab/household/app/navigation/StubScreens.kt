package com.homelab.household.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Screens as black boxes: each destination says which one it is, and offers the ways out that
 * the real screen decides between.
 */
class StubScreens : AppScreens {

    @Composable
    override fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit, onSignedIn: () -> Unit) {
        Column {
            Text(LAUNCH)
            Text(GO_TO_SIGN_IN, modifier = Modifier.clickable { onSignIn() })
            Text(GO_TO_FIRST_RUN, modifier = Modifier.clickable { onFirstRun() })
            Text(GO_HOME, modifier = Modifier.clickable { onSignedIn() })
        }
    }

    @Composable
    override fun SignIn() {
        Text(SIGN_IN)
    }

    @Composable
    override fun FirstRun(onCreated: () -> Unit, onSignIn: () -> Unit) {
        Column {
            Text(FIRST_RUN)
            Text(CREATED, modifier = Modifier.clickable { onCreated() })
            Text(SIGN_IN_INSTEAD, modifier = Modifier.clickable { onSignIn() })
        }
    }

    @Composable
    override fun Home() {
        Text(HOME)
    }

    companion object {
        const val LAUNCH = "launch screen"
        const val SIGN_IN = "sign in screen"
        const val FIRST_RUN = "first run screen"
        const val HOME = "home screen"
        const val GO_TO_SIGN_IN = "go to sign in"
        const val GO_TO_FIRST_RUN = "go to first run"
        const val GO_HOME = "go home"
        const val CREATED = "household created"
        const val SIGN_IN_INSTEAD = "sign in instead"
    }
}
