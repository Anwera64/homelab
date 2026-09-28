package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.homelab.household.app.components.HearthBottomNav
import com.homelab.household.app.components.NavTab
import com.homelab.household.domain.usecase.HasStoredSessionUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The root of the app: it owns the back stack and nothing else. Screens own their own state and
 * report back through the callbacks wired here, so navigation lives in exactly one place.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    screens: AppScreens = RealAppScreens,
    backStack: SnapshotStateList<NavKey> = rememberAppBackStack(),
    session: AppSession = rememberAppSession(),
) {
    LaunchedEffect(session) {
        // Listening before renewing, so a token the hub refuses on the way sends the phone back too.
        launch(start = CoroutineStart.UNDISPATCHED) {
            session.signedOut.collect { backStack.startOver(Destination.SignIn) }
        }
        session.renew()
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        // Each destination keeps its ViewModels until it leaves the back stack, not while a screen
        // covers it: a new chat is still there after connecting a calendar from it, and a PIN pad
        // opened again after Back starts clean.
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<Destination.Launch> {
                    screens.Launch(
                        onSignIn = { backStack.startOver(Destination.SignIn) },
                        onFirstRun = { backStack.startOver(Destination.FirstRun) },
                    )
                }
                entry<Destination.SignIn> {
                    // The picker stays under the PIN pad, so back from a PIN returns to it.
                    screens.SignIn(
                        onSelectMember = { member -> backStack.add(Destination.Pin(member)) },
                        onInviteCode = { backStack.add(Destination.InviteCode) },
                    )
                }
                entry<Destination.Pin> { destination ->
                    screens.Pin(
                        member = destination.member,
                        onSignedIn = { backStack.startOver(Destination.Home) },
                        onBack = { backStack.removeLastOrNull() },
                        onForget = { backStack.add(Destination.PinForgot(destination.member)) },
                    )
                }
                entry<Destination.FirstRun> {
                    screens.FirstRun(
                        onCreate = { backStack.startOver(Destination.Home) },
                        onSignIn = { backStack.startOver(Destination.SignIn) },
                    )
                }
                entry<Destination.InviteCode> {
                    screens.InviteCode(
                        onBack = { backStack.removeLastOrNull() },
                        onInvite = { preview, code -> backStack.add(Destination.Join(preview, code)) },
                    )
                }
                entry<Destination.Join> { destination ->
                    screens.Join(
                        preview = destination.preview,
                        code = destination.code,
                        onJoin = { backStack.startOver(Destination.Home) },
                        // The code is spent or gone: back to typing one, not to the form.
                        onExpire = { backStack.startOver(Destination.InviteCode) },
                    )
                }
                entry<Destination.PinForgot> { destination ->
                    screens.PinForgot(
                        member = destination.member,
                        onBack = { backStack.removeLastOrNull() },
                        onHaveCode = { backStack.add(Destination.ResetCode) },
                    )
                }
                entry<Destination.ResetCode> {
                    screens.ResetCode(
                        onBack = { backStack.removeLastOrNull() },
                        onCode = { code -> backStack.add(Destination.NewPin(code)) },
                    )
                }
                entry<Destination.NewPin> { destination ->
                    screens.NewPin(
                        code = destination.code,
                        onSignedIn = { backStack.startOver(Destination.Home) },
                    )
                }
                entry<Destination.Home> {
                    screens.Home(
                        onProfile = { backStack.add(Destination.Profile) },
                        tabs = { backStack.Tabs(NavTab.Household) },
                    )
                }
                entry<Destination.Schedule> {
                    screens.Schedule(
                        onProfile = { backStack.add(Destination.Profile) },
                        tabs = { backStack.Tabs(NavTab.Schedule) },
                    )
                }
                entry<Destination.Chats> {
                    screens.Chats(
                        onProfile = { backStack.add(Destination.Profile) },
                        onOpen = { sessionId -> backStack.add(Destination.Conversation(sessionId)) },
                        onNewChat = { backStack.add(Destination.Conversation()) },
                        tabs = { backStack.Tabs(NavTab.Chats) },
                    )
                }
                entry<Destination.MySpace> {
                    screens.MySpace(
                        onProfile = { backStack.add(Destination.Profile) },
                        tabs = { backStack.Tabs(NavTab.MySpace) },
                    )
                }
                entry<Destination.Conversation> { destination ->
                    screens.Conversation(
                        sessionId = destination.sessionId,
                        onBack = { backStack.removeLastOrNull() },
                        // A failed calendar step's fix: connecting lands back here, to ask again.
                        onConnectCalendar = { backStack.add(Destination.CalendarPicker) },
                    )
                }
                entry<Destination.Profile> {
                    screens.Profile(
                        onBack = { backStack.removeLastOrNull() },
                        onMembers = { backStack.add(Destination.Members) },
                        onChangePin = { backStack.add(Destination.ChangePin) },
                        onLeave = { backStack.add(Destination.LeaveHousehold) },
                        onSignedOut = { backStack.startOver(Destination.SignIn) },
                        onCalendar = { backStack.add(Destination.CalendarPicker) },
                        onCalendarSignInAgain = { backStack.add(Destination.GoogleCalendarSignIn) },
                    )
                }
                entry<Destination.Members> {
                    screens.Members(
                        onBack = { backStack.removeLastOrNull() },
                        onInvite = { backStack.add(Destination.InviteCreate) },
                        onResetPin = { member -> backStack.add(Destination.PinApprove(member)) },
                        onRemove = { member -> backStack.add(Destination.RemoveMember(member)) },
                    )
                }
                entry<Destination.InviteCreate> {
                    screens.InviteCreate(onBack = { backStack.removeLastOrNull() })
                }
                entry<Destination.PinApprove> { destination ->
                    screens.PinApprove(member = destination.member, onBack = { backStack.removeLastOrNull() })
                }
                entry<Destination.RemoveMember> { destination ->
                    screens.RemoveMember(
                        member = destination.member,
                        onBack = { backStack.removeLastOrNull() },
                        // They are gone: the list behind this screen would still show them.
                        onRemove = { backStack.startOver(Destination.Members) },
                    )
                }
                entry<Destination.LeaveHousehold> {
                    screens.LeaveHousehold(
                        onBack = { backStack.removeLastOrNull() },
                        onLeft = { backStack.startOver(Destination.SignIn) },
                    )
                }
                entry<Destination.ChangePin> {
                    screens.ChangePin(
                        onBack = { backStack.removeLastOrNull() },
                        onChange = { backStack.removeLastOrNull() },
                    )
                }
                entry<Destination.CalendarPicker> {
                    screens.CalendarPicker(
                        onBack = { backStack.removeLastOrNull() },
                        onPick = { provider ->
                            backStack.add(
                                if (provider.signsIn) {
                                    Destination.GoogleCalendarSignIn
                                } else {
                                    Destination.CalendarConnect(provider)
                                },
                            )
                        },
                    )
                }
                entry<Destination.CalendarConnect> { destination ->
                    screens.CalendarConnect(
                        provider = destination.provider,
                        onBack = { backStack.removeLastOrNull() },
                        // Connected: back past the picker, to wherever the calendar was asked for.
                        onConnect = { backStack.backPastCalendarScreens() },
                    )
                }
                entry<Destination.GoogleCalendarSignIn> {
                    screens.GoogleCalendarSignIn(
                        onBack = { backStack.removeLastOrNull() },
                        onConnect = { backStack.backPastCalendarScreens() },
                    )
                }
            },
    )
}

/**
 * Where the app starts, decided on the phone: home for a member still signed in here, launch for
 * everyone else. Waits for the hub on nothing â€” a token it no longer accepts signs the phone out
 * when the session is renewed, or on the next call. Hoisted so a test can watch where it goes.
 */
@Composable
fun rememberAppBackStack(): SnapshotStateList<NavKey> {
    val hasStoredSession = koinInject<HasStoredSessionUseCase>()
    return remember {
        mutableStateListOf(if (hasStoredSession()) Destination.Home else Destination.Launch)
    }
}

/** Somewhere the user can't come back from: launch's answer, a finished form, a signed-in member. */
private fun SnapshotStateList<NavKey>.startOver(destination: Destination) {
    clear()
    add(destination)
}

/**
 * The bottom bar, wired to the back stack.
 *
 * Each tab is a root rather than a push: moving between them replaces the stack, so Back from a
 * tab leaves the app instead of retracing the tabs you happened to visit. The raised + always
 * opens a conversation with no session â€” one is created on the first send.
 */
@Composable
private fun SnapshotStateList<NavKey>.Tabs(selected: NavTab) {
    HearthBottomNav(
        selected = selected,
        onSelect = { tab ->
            startOver(
                when (tab) {
                    NavTab.Household -> Destination.Home
                    NavTab.Schedule -> Destination.Schedule
                    NavTab.Chats -> Destination.Chats
                    NavTab.MySpace -> Destination.MySpace
                },
            )
        },
        onNewChat = { add(Destination.Conversation()) },
    )
}

/**
 * A calendar connected: back past the picker to where it was asked for, the profile with the new
 * row in it, or the conversation whose step needed it.
 */
private fun MutableList<NavKey>.backPastCalendarScreens() {
    removeAll {
        it == Destination.CalendarPicker ||
            it is Destination.CalendarConnect ||
            it == Destination.GoogleCalendarSignIn
    }
}
