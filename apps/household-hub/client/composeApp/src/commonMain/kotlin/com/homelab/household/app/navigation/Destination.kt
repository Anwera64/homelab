package com.homelab.household.app.navigation

import androidx.navigation3.runtime.NavKey
import com.homelab.household.domain.model.Member

/** Every place the app can be. One entry per screen in [AppNavHost]. */
sealed interface Destination : NavKey {
    data object Launch : Destination

    /** "Who's here?" */
    data object SignIn : Destination

    /** The PIN pad of the member tapped on the picker, which stays underneath it. */
    data class Pin(val member: Member) : Destination

    data object FirstRun : Destination

    /** Where a signed-in member lands. */
    data object Home : Destination
}
