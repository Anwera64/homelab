package com.homelab.household.app.navigation

import androidx.navigation3.runtime.NavKey
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.Member

/** Every place the app can be. One entry per screen in [AppNavHost]. */
sealed interface Destination : NavKey {
    data object Launch : Destination

    /** "Who's here?" */
    data object SignIn : Destination

    /** The PIN pad of the member tapped on the picker, which stays underneath it. */
    data class Pin(
        val member: Member,
    ) : Destination

    data object FirstRun : Destination

    /** The way in for someone who isn't on the picker yet. */
    data object InviteCode : Destination

    /** The code checked out: who invited them, and the name they were invited as. */
    data class Join(
        val preview: InvitePreview,
        val code: String,
    ) : Destination

    /** How to get a new PIN when you have forgotten yours. */
    data class PinForgot(
        val member: Member,
    ) : Destination

    /** The code a housemate, or the hub itself, gave you. */
    data object ResetCode : Destination

    /** Choosing the PIN that code lets you set. */
    data class NewPin(
        val code: String,
    ) : Destination

    /**
     * The four tabs. Each is a root: switching tabs replaces the stack rather than stacking on it,
     * so Back from a tab leaves the app instead of walking backwards through the ones you visited.
     */
    sealed interface Tab : Destination

    /** Where a signed-in member lands. */
    data object Home : Tab

    data object Schedule : Tab

    data object Chats : Tab

    data object MySpace : Tab

    /**
     * A conversation, or a new one.
     *
     * [sessionId] is null when the hero + opens one that does not exist yet: the screen shows the
     * agent's greeting and the session is created on the first send, so a chat nobody spoke in is
     * never left behind on the hub.
     */
    data class Conversation(
        val sessionId: String? = null,
    ) : Destination

    data object Profile : Destination

    /** Which writes agents do for you without asking: the same settings as an approval card's box. */
    data object WhenAgentsAct : Destination

    /** What the agents know: your memories and the household's, each of yours removable. */
    data object Memories : Destination

    /** Changes an agent read in a chat that differ from what's stored, each answered in place. */
    data object ChangesToCheck : Destination

    data object Members : Destination

    data object InviteCreate : Destination

    /** Vouching for the member whose PIN reset you are approving. */
    data class PinApprove(
        val member: Member,
    ) : Destination

    data class RemoveMember(
        val member: Member,
    ) : Destination

    data object LeaveHousehold : Destination

    data object ChangePin : Destination

    /** Where your calendar lives: the first step of connecting one, from the profile. */
    data object CalendarPicker : Destination

    /** The details that provider needs, tried by the hub before anything is kept. */
    data class CalendarConnect(
        val provider: CalendarProvider,
    ) : Destination

    /** Google takes no password: its calendar is connected, or connected again, by signing in with Google. */
    data object GoogleCalendarSignIn : Destination

    /**
     * Which of the account's calendars agents read (#252). [afterConnect]: shown right after
     * connecting, so leaving it, by Done or Back, ends the whole connect flow.
     */
    data class CalendarChoose(
        val afterConnect: Boolean,
    ) : Destination
}
