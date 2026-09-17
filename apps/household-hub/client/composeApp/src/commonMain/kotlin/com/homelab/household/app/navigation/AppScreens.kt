package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import com.homelab.household.app.screens.changepin.ChangePinScreen
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
    @Composable fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit)

    @Composable fun SignIn(onMemberSelected: (Member) -> Unit, onInviteCode: () -> Unit)

    @Composable fun Pin(member: Member, onSignedIn: () -> Unit, onBack: () -> Unit, onForgotten: () -> Unit)

    @Composable fun FirstRun(onCreated: () -> Unit, onSignIn: () -> Unit)

    @Composable fun InviteCode(onBack: () -> Unit, onInvite: (InvitePreview, String) -> Unit)

    @Composable fun Join(preview: InvitePreview, code: String, onJoined: () -> Unit, onExpired: () -> Unit)

    @Composable fun PinForgot(member: Member, onBack: () -> Unit, onHaveCode: () -> Unit)

    @Composable fun ResetCode(onBack: () -> Unit, onCode: (String) -> Unit)

    @Composable fun NewPin(code: String, onSignedIn: () -> Unit)

    @Composable fun Home(onProfile: () -> Unit)

    @Composable fun Profile(
        onBack: () -> Unit,
        onMembers: () -> Unit,
        onChangePin: () -> Unit,
        onLeave: () -> Unit,
        onSignedOut: () -> Unit
    )

    @Composable fun Members(
        onBack: () -> Unit,
        onInvite: () -> Unit,
        onResetPin: (Member) -> Unit,
        onRemove: (Member) -> Unit
    )

    @Composable fun InviteCreate(onBack: () -> Unit)

    @Composable fun PinApprove(member: Member, onBack: () -> Unit)

    @Composable fun RemoveMember(member: Member, onBack: () -> Unit, onRemoved: () -> Unit)

    @Composable fun LeaveHousehold(onBack: () -> Unit, onLeft: () -> Unit)

    @Composable fun ChangePin(onBack: () -> Unit, onChanged: () -> Unit)
}

/** The screens the app actually runs. */
object RealAppScreens : AppScreens {

    @Composable
    override fun Launch(onSignIn: () -> Unit, onFirstRun: () -> Unit) {
        LaunchScreen(onSignIn = onSignIn, onFirstRun = onFirstRun)
    }

    @Composable
    override fun SignIn(onMemberSelected: (Member) -> Unit, onInviteCode: () -> Unit) {
        ProfilePickerScreen(onMemberSelected = onMemberSelected, onInviteCode = onInviteCode)
    }

    @Composable
    override fun Pin(member: Member, onSignedIn: () -> Unit, onBack: () -> Unit, onForgotten: () -> Unit) {
        PinEntryScreen(member = member, onSignedIn = onSignedIn, onBack = onBack, onForgotten = onForgotten)
    }

    @Composable
    override fun FirstRun(onCreated: () -> Unit, onSignIn: () -> Unit) {
        FirstRunScreen(onCreated = onCreated, onSignIn = onSignIn)
    }

    @Composable
    override fun InviteCode(onBack: () -> Unit, onInvite: (InvitePreview, String) -> Unit) {
        InviteCodeScreen(onBack = onBack, onInvite = onInvite)
    }

    @Composable
    override fun Join(preview: InvitePreview, code: String, onJoined: () -> Unit, onExpired: () -> Unit) {
        JoinScreen(preview = preview, code = code, onJoined = onJoined, onExpired = onExpired)
    }

    @Composable
    override fun PinForgot(member: Member, onBack: () -> Unit, onHaveCode: () -> Unit) {
        PinForgotScreen(member = member, onBack = onBack, onHaveCode = onHaveCode)
    }

    @Composable
    override fun ResetCode(onBack: () -> Unit, onCode: (String) -> Unit) {
        ResetCodeScreen(onBack = onBack, onCode = onCode)
    }

    @Composable
    override fun NewPin(code: String, onSignedIn: () -> Unit) {
        NewPinScreen(code = code, onSignedIn = onSignedIn)
    }

    @Composable
    override fun Home(onProfile: () -> Unit) {
        HomePlaceholderScreen(onProfile = onProfile)
    }

    @Composable
    override fun Profile(
        onBack: () -> Unit,
        onMembers: () -> Unit,
        onChangePin: () -> Unit,
        onLeave: () -> Unit,
        onSignedOut: () -> Unit
    ) {
        ProfileScreen(
            onBack = onBack,
            onMembers = onMembers,
            onChangePin = onChangePin,
            onLeave = onLeave,
            onSignedOut = onSignedOut
        )
    }

    @Composable
    override fun Members(
        onBack: () -> Unit,
        onInvite: () -> Unit,
        onResetPin: (Member) -> Unit,
        onRemove: (Member) -> Unit
    ) {
        MembersScreen(onBack = onBack, onInvite = onInvite, onResetPin = onResetPin, onRemove = onRemove)
    }

    @Composable
    override fun InviteCreate(onBack: () -> Unit) {
        InviteCreateScreen(onBack = onBack)
    }

    @Composable
    override fun PinApprove(member: Member, onBack: () -> Unit) {
        PinApproveScreen(member = member, onBack = onBack)
    }

    @Composable
    override fun RemoveMember(member: Member, onBack: () -> Unit, onRemoved: () -> Unit) {
        RemoveMemberScreen(member = member, onBack = onBack, onRemoved = onRemoved)
    }

    @Composable
    override fun LeaveHousehold(onBack: () -> Unit, onLeft: () -> Unit) {
        LeaveHouseholdScreen(onBack = onBack, onLeft = onLeft)
    }

    @Composable
    override fun ChangePin(onBack: () -> Unit, onChanged: () -> Unit) {
        ChangePinScreen(onBack = onBack, onChanged = onChanged)
    }
}
