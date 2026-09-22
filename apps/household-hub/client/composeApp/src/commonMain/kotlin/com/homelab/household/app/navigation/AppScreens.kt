package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import com.homelab.household.app.screens.changepin.ChangePinScreen
import com.homelab.household.app.screens.chats.ChatsScreen
import com.homelab.household.app.screens.conversation.ConversationScreen
import com.homelab.household.app.screens.firstrun.FirstRunScreen
import com.homelab.household.app.screens.invitecode.InviteCodeScreen
import com.homelab.household.app.screens.invitecreate.InviteCreateScreen
import com.homelab.household.app.screens.join.JoinScreen
import com.homelab.household.app.screens.launch.LaunchScreen
import com.homelab.household.app.screens.leavehousehold.LeaveHouseholdScreen
import com.homelab.household.app.screens.members.MembersScreen
import com.homelab.household.app.screens.pinapprove.PinApproveScreen
import com.homelab.household.app.screens.pinentry.PinEntryScreen
import com.homelab.household.app.screens.pinforgot.PinForgotScreen
import com.homelab.household.app.screens.placeholder.HomePlaceholderScreen
import com.homelab.household.app.screens.profile.ProfileScreen
import com.homelab.household.app.screens.profilepicker.ProfilePickerScreen
import com.homelab.household.app.screens.removemember.RemoveMemberScreen
import com.homelab.household.app.screens.resetpin.NewPinScreen
import com.homelab.household.app.screens.resetpin.ResetCodeScreen
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.Member

/**
 * The screens [AppNavHost] can show, behind one seam.
 *
 * Navigation only needs to know that a destination draws something and reports back; what a
 * screen does before it calls back is its own business. Keeping that behind an interface lets
 * the navigation tests stub the screens out and test the back stack alone.
 */
interface AppScreens {
    @Composable fun Launch(
        onSignIn: () -> Unit,
        onFirstRun: () -> Unit,
    )

    @Composable fun SignIn(
        onSelectMember: (Member) -> Unit,
        onInviteCode: () -> Unit,
    )

    @Composable fun Pin(
        member: Member,
        onSignedIn: () -> Unit,
        onBack: () -> Unit,
        onForget: () -> Unit,
    )

    @Composable fun FirstRun(
        onCreate: () -> Unit,
        onSignIn: () -> Unit,
    )

    @Composable fun InviteCode(
        onBack: () -> Unit,
        onInvite: (InvitePreview, String) -> Unit,
    )

    @Composable fun Join(
        preview: InvitePreview,
        code: String,
        onJoin: () -> Unit,
        onExpire: () -> Unit,
    )

    @Composable fun PinForgot(
        member: Member,
        onBack: () -> Unit,
        onHaveCode: () -> Unit,
    )

    @Composable fun ResetCode(
        onBack: () -> Unit,
        onCode: (String) -> Unit,
    )

    @Composable fun NewPin(
        code: String,
        onSignedIn: () -> Unit,
    )

    @Composable fun Home(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    )

    @Composable fun Schedule(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    )

    @Composable fun MySpace(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    )

    @Composable fun Chats(
        onProfile: () -> Unit,
        onOpen: (String) -> Unit,
        onNewChat: () -> Unit,
        tabs: @Composable () -> Unit,
    )

    @Composable fun Conversation(
        sessionId: String?,
        onBack: () -> Unit,
    )

    @Composable fun Profile(
        onBack: () -> Unit,
        onMembers: () -> Unit,
        onChangePin: () -> Unit,
        onLeave: () -> Unit,
        onSignedOut: () -> Unit,
    )

    @Composable fun Members(
        onBack: () -> Unit,
        onInvite: () -> Unit,
        onResetPin: (Member) -> Unit,
        onRemove: (Member) -> Unit,
    )

    @Composable fun InviteCreate(onBack: () -> Unit)

    @Composable fun PinApprove(
        member: Member,
        onBack: () -> Unit,
    )

    @Composable fun RemoveMember(
        member: Member,
        onBack: () -> Unit,
        onRemove: () -> Unit,
    )

    @Composable fun LeaveHousehold(
        onBack: () -> Unit,
        onLeft: () -> Unit,
    )

    @Composable fun ChangePin(
        onBack: () -> Unit,
        onChange: () -> Unit,
    )
}

/** The screens the app actually runs. */
object RealAppScreens : AppScreens {
    @Composable
    override fun Launch(
        onSignIn: () -> Unit,
        onFirstRun: () -> Unit,
    ) {
        LaunchScreen(onSignIn = onSignIn, onFirstRun = onFirstRun)
    }

    @Composable
    override fun SignIn(
        onSelectMember: (Member) -> Unit,
        onInviteCode: () -> Unit,
    ) {
        ProfilePickerScreen(onSelectMember = onSelectMember, onInviteCode = onInviteCode)
    }

    @Composable
    override fun Pin(
        member: Member,
        onSignedIn: () -> Unit,
        onBack: () -> Unit,
        onForget: () -> Unit,
    ) {
        PinEntryScreen(member = member, onSignedIn = onSignedIn, onBack = onBack, onForget = onForget)
    }

    @Composable
    override fun FirstRun(
        onCreate: () -> Unit,
        onSignIn: () -> Unit,
    ) {
        FirstRunScreen(onCreate = onCreate, onSignIn = onSignIn)
    }

    @Composable
    override fun InviteCode(
        onBack: () -> Unit,
        onInvite: (InvitePreview, String) -> Unit,
    ) {
        InviteCodeScreen(onBack = onBack, onInvite = onInvite)
    }

    @Composable
    override fun Join(
        preview: InvitePreview,
        code: String,
        onJoin: () -> Unit,
        onExpire: () -> Unit,
    ) {
        JoinScreen(preview = preview, code = code, onJoin = onJoin, onExpire = onExpire)
    }

    @Composable
    override fun PinForgot(
        member: Member,
        onBack: () -> Unit,
        onHaveCode: () -> Unit,
    ) {
        PinForgotScreen(member = member, onBack = onBack, onHaveCode = onHaveCode)
    }

    @Composable
    override fun ResetCode(
        onBack: () -> Unit,
        onCode: (String) -> Unit,
    ) {
        ResetCodeScreen(onBack = onBack, onCode = onCode)
    }

    @Composable
    override fun NewPin(
        code: String,
        onSignedIn: () -> Unit,
    ) {
        NewPinScreen(code = code, onSignedIn = onSignedIn)
    }

    @Composable
    override fun Home(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    ) {
        HomePlaceholderScreen(onProfile = onProfile, tabs = tabs)
    }

    @Composable
    override fun Schedule(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    ) {
        HomePlaceholderScreen(onProfile = onProfile, tabs = tabs)
    }

    @Composable
    override fun MySpace(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    ) {
        HomePlaceholderScreen(onProfile = onProfile, tabs = tabs)
    }

    @Composable
    override fun Chats(
        onProfile: () -> Unit,
        onOpen: (String) -> Unit,
        onNewChat: () -> Unit,
        tabs: @Composable () -> Unit,
    ) {
        ChatsScreen(onOpen = onOpen, onNewChat = onNewChat, tabs = tabs)
    }

    @Composable
    override fun Conversation(
        sessionId: String?,
        onBack: () -> Unit,
    ) {
        ConversationScreen(sessionId = sessionId, onBack = onBack)
    }

    @Composable
    override fun Profile(
        onBack: () -> Unit,
        onMembers: () -> Unit,
        onChangePin: () -> Unit,
        onLeave: () -> Unit,
        onSignedOut: () -> Unit,
    ) {
        ProfileScreen(
            onBack = onBack,
            onMembers = onMembers,
            onChangePin = onChangePin,
            onLeave = onLeave,
            onSignedOut = onSignedOut,
        )
    }

    @Composable
    override fun Members(
        onBack: () -> Unit,
        onInvite: () -> Unit,
        onResetPin: (Member) -> Unit,
        onRemove: (Member) -> Unit,
    ) {
        MembersScreen(onBack = onBack, onInvite = onInvite, onResetPin = onResetPin, onRemove = onRemove)
    }

    @Composable
    override fun InviteCreate(onBack: () -> Unit) {
        InviteCreateScreen(onBack = onBack)
    }

    @Composable
    override fun PinApprove(
        member: Member,
        onBack: () -> Unit,
    ) {
        PinApproveScreen(member = member, onBack = onBack)
    }

    @Composable
    override fun RemoveMember(
        member: Member,
        onBack: () -> Unit,
        onRemove: () -> Unit,
    ) {
        RemoveMemberScreen(member = member, onBack = onBack, onRemove = onRemove)
    }

    @Composable
    override fun LeaveHousehold(
        onBack: () -> Unit,
        onLeft: () -> Unit,
    ) {
        LeaveHouseholdScreen(onBack = onBack, onLeft = onLeft)
    }

    @Composable
    override fun ChangePin(
        onBack: () -> Unit,
        onChange: () -> Unit,
    ) {
        ChangePinScreen(onBack = onBack, onChange = onChange)
    }
}
