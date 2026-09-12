package com.homelab.household.app.navigation

import androidx.navigation3.runtime.NavKey

/** Every place the app can be. One entry per screen in [AppNavHost]. */
sealed interface Destination : NavKey {
    data object Launch : Destination
    data object SignIn : Destination
    data object FirstRun : Destination
}
