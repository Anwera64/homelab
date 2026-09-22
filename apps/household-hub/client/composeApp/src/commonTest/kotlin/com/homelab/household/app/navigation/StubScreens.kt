package com.homelab.household.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.Member

/**
 * Screens as black boxes: each destination says which one it is, and offers the ways out that
 * the real screen decides between.
 */
class StubScreens : AppScreens {
    @Composable
    override fun Launch(
        onSignIn: () -> Unit,
        onFirstRun: () -> Unit,
    ) {
        Column {
            Text(LAUNCH)
            Text(GO_TO_SIGN_IN, modifier = Modifier.clickable { onSignIn() })
            Text(GO_TO_FIRST_RUN, modifier = Modifier.clickable { onFirstRun() })
        }
    }

    @Composable
    override fun SignIn(
        onSelectMember: (Member) -> Unit,
        onInviteCode: () -> Unit,
    ) {
        Column {
            Text(SIGN_IN)
            Text(PICK_EMMA, modifier = Modifier.clickable { onSelectMember(EMMA) })
            Text(HAVE_AN_INVITE, modifier = Modifier.clickable { onInviteCode() })
        }
    }

    @Composable
    override fun Pin(
        member: Member,
        onSignedIn: () -> Unit,
        onBack: () -> Unit,
        onForget: () -> Unit,
    ) {
        Column {
            Text(pinOf(member))
            Text(SIGNED_IN, modifier = Modifier.clickable { onSignedIn() })
            Text(BACK, modifier = Modifier.clickable { onBack() })
            Text(FORGOTTEN, modifier = Modifier.clickable { onForget() })
        }
    }

    @Composable
    override fun FirstRun(
        onCreate: () -> Unit,
        onSignIn: () -> Unit,
    ) {
        Column {
            Text(FIRST_RUN)
            Text(CREATED, modifier = Modifier.clickable { onCreate() })
            Text(SIGN_IN_INSTEAD, modifier = Modifier.clickable { onSignIn() })
        }
    }

    @Composable
    override fun InviteCode(
        onBack: () -> Unit,
        onInvite: (InvitePreview, String) -> Unit,
    ) {
        Column {
            Text(INVITE_CODE)
            Text(CODE_ACCEPTED, modifier = Modifier.clickable { onInvite(INVITE, "K7M2QP") })
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun Join(
        preview: InvitePreview,
        code: String,
        onJoin: () -> Unit,
        onExpire: () -> Unit,
    ) {
        Column {
            Text(joinOf(preview))
            Text(JOINED, modifier = Modifier.clickable { onJoin() })
            Text(CODE_EXPIRED, modifier = Modifier.clickable { onExpire() })
        }
    }

    @Composable
    override fun PinForgot(
        member: Member,
        onBack: () -> Unit,
        onHaveCode: () -> Unit,
    ) {
        Column {
            Text(forgotOf(member))
            Text(HAVE_A_RESET_CODE, modifier = Modifier.clickable { onHaveCode() })
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun ResetCode(
        onBack: () -> Unit,
        onCode: (String) -> Unit,
    ) {
        Column {
            Text(RESET_CODE)
            Text(RESET_CODE_TYPED, modifier = Modifier.clickable { onCode("P4XN7T") })
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun NewPin(
        code: String,
        onSignedIn: () -> Unit,
    ) {
        Column {
            Text(newPinFor(code))
            Text(SIGNED_IN, modifier = Modifier.clickable { onSignedIn() })
        }
    }

    @Composable
    override fun Home(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    ) {
        Column {
            Text(HOME)
            Text(GO_TO_PROFILE, modifier = Modifier.clickable { onProfile() })
            tabs()
        }
    }

    @Composable
    override fun Schedule(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    ) {
        Column {
            Text(SCHEDULE)
            tabs()
        }
    }

    @Composable
    override fun MySpace(
        onProfile: () -> Unit,
        tabs: @Composable () -> Unit,
    ) {
        Column {
            Text(MY_SPACE)
            tabs()
        }
    }

    @Composable
    override fun Chats(
        onProfile: () -> Unit,
        onOpen: (String) -> Unit,
        tabs: @Composable () -> Unit,
    ) {
        Column {
            Text(CHATS)
            Text(OPEN_A_CHAT, modifier = Modifier.clickable { onOpen("s-1") })
            tabs()
        }
    }

    @Composable
    override fun Conversation(
        sessionId: String?,
        onBack: () -> Unit,
    ) {
        Column {
            Text(if (sessionId == null) NEW_CONVERSATION else conversationOf(sessionId))
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun Profile(
        onBack: () -> Unit,
        onMembers: () -> Unit,
        onChangePin: () -> Unit,
        onLeave: () -> Unit,
        onSignedOut: () -> Unit,
    ) {
        Column {
            Text(PROFILE)
            Text(GO_TO_MEMBERS, modifier = Modifier.clickable { onMembers() })
            Text(GO_TO_CHANGE_PIN, modifier = Modifier.clickable { onChangePin() })
            Text(GO_TO_LEAVE, modifier = Modifier.clickable { onLeave() })
            Text(SIGNED_OUT, modifier = Modifier.clickable { onSignedOut() })
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun Members(
        onBack: () -> Unit,
        onInvite: () -> Unit,
        onResetPin: (Member) -> Unit,
        onRemove: (Member) -> Unit,
    ) {
        Column {
            Text(MEMBERS)
            Text(GO_TO_INVITE, modifier = Modifier.clickable { onInvite() })
            Text(RESET_LIAMS_PIN, modifier = Modifier.clickable { onResetPin(LIAM) })
            Text(REMOVE_LIAM, modifier = Modifier.clickable { onRemove(LIAM) })
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun InviteCreate(onBack: () -> Unit) {
        Column {
            Text(INVITE_CREATE)
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun PinApprove(
        member: Member,
        onBack: () -> Unit,
    ) {
        Column {
            Text(approveOf(member))
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun RemoveMember(
        member: Member,
        onBack: () -> Unit,
        onRemove: () -> Unit,
    ) {
        Column {
            Text(removeOf(member))
            Text(REMOVED, modifier = Modifier.clickable { onRemove() })
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun LeaveHousehold(
        onBack: () -> Unit,
        onLeft: () -> Unit,
    ) {
        Column {
            Text(LEAVE)
            Text(LEFT, modifier = Modifier.clickable { onLeft() })
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    @Composable
    override fun ChangePin(
        onBack: () -> Unit,
        onChange: () -> Unit,
    ) {
        Column {
            Text(CHANGE_PIN)
            Text(PIN_CHANGED, modifier = Modifier.clickable { onChange() })
            Text(BACK, modifier = Modifier.clickable { onBack() })
        }
    }

    companion object {
        const val LAUNCH = "launch screen"
        const val SIGN_IN = "sign in screen"
        const val FIRST_RUN = "first run screen"
        const val HOME = "home screen"
        const val SCHEDULE = "schedule screen"
        const val MY_SPACE = "my space screen"
        const val CHATS = "chats screen"
        const val NEW_CONVERSATION = "new conversation screen"
        const val OPEN_A_CHAT = "open a chat"

        fun conversationOf(sessionId: String) = "conversation screen $sessionId"

        const val INVITE_CODE = "invite code screen"
        const val RESET_CODE = "reset code screen"
        const val PROFILE = "profile screen"
        const val MEMBERS = "members screen"
        const val INVITE_CREATE = "invite create screen"
        const val LEAVE = "leave household screen"
        const val CHANGE_PIN = "change pin screen"
        const val GO_TO_SIGN_IN = "go to sign in"
        const val GO_TO_FIRST_RUN = "go to first run"
        const val GO_TO_PROFILE = "go to profile"
        const val GO_TO_MEMBERS = "go to members"
        const val GO_TO_INVITE = "go to invite"
        const val GO_TO_CHANGE_PIN = "go to change pin"
        const val GO_TO_LEAVE = "go to leave"
        const val CREATED = "household created"
        const val SIGN_IN_INSTEAD = "sign in instead"
        const val PICK_EMMA = "pick emma"
        const val HAVE_AN_INVITE = "have an invite code"
        const val CODE_ACCEPTED = "code accepted"
        const val CODE_EXPIRED = "code expired"
        const val JOINED = "joined the household"
        const val FORGOTTEN = "forgotten the pin"
        const val HAVE_A_RESET_CODE = "have a reset code"
        const val RESET_CODE_TYPED = "reset code typed"
        const val RESET_LIAMS_PIN = "reset liam pin"
        const val REMOVE_LIAM = "remove liam"
        const val REMOVED = "member removed"
        const val LEFT = "left the household"
        const val PIN_CHANGED = "pin changed"
        const val SIGNED_IN = "signed in"
        const val SIGNED_OUT = "signed out"
        const val BACK = "back"

        val EMMA = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
        val LIAM = Member(id = "liam", name = "Liam", avatarColor = "#C05638")
        val INVITE = InvitePreview(invitedName = "Liam", inviterName = "Emma", inviterAvatarColor = "#3C6E4E")

        fun pinOf(member: Member) = "pin pad of ${member.name}"

        fun forgotOf(member: Member) = "forgotten pin of ${member.name}"

        fun approveOf(member: Member) = "approve reset for ${member.name}"

        fun removeOf(member: Member) = "remove ${member.name}"

        fun joinOf(preview: InvitePreview) = "join as ${preview.invitedName}"

        fun newPinFor(code: String) = "new pin for $code"
    }
}
