package com.homelab.household.app.screens.pinforgot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SkeletonBlock
import com.homelab.household.app.components.SkeletonCircle
import com.homelab.household.app.components.SkeletonGroup
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.forgot_alone_command
import com.homelab.household.app.resources.forgot_alone_detail
import com.homelab.household.app.resources.forgot_alone_title
import com.homelab.household.app.resources.forgot_ask
import com.homelab.household.app.resources.forgot_ask_detail
import com.homelab.household.app.resources.forgot_back
import com.homelab.household.app.resources.forgot_detail
import com.homelab.household.app.resources.forgot_footnote
import com.homelab.household.app.resources.forgot_have_code
import com.homelab.household.app.resources.forgot_step_one
import com.homelab.household.app.resources.forgot_step_three
import com.homelab.household.app.resources.forgot_step_two
import com.homelab.household.app.resources.forgot_title
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.presentation.pinforgot.PinForgotStatus
import com.homelab.household.presentation.pinforgot.PinForgotUiState
import org.jetbrains.compose.resources.stringResource

/**
 * There is no email to send a reset to, so recovery is social: another member vouches for you.
 * Whoever can reach the hub itself is the backstop, and that cannot be lost with a phone.
 *
 * Stateless — [PinForgotScreen] owns the ViewModel.
 */
@Composable
fun PinForgotContent(
    state: PinForgotUiState,
    onHaveCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val vouching = state.others.firstOrNull()

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.forgot_back)) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                Text(stringResource(Res.string.forgot_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.forgot_detail), style = type.body, color = colors.textMuted)
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                // Who can vouch is the one thing here the hub has to answer. "I have a reset code"
                // is always true, so it stays tappable while the rest arrives.
                if (state.status == PinForgotStatus.Loading) {
                    ArrivingVouch()
                } else if (vouching != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MemberAvatar(
                            name = vouching.name,
                            colour = vouching.avatarColor,
                            size = HearthTheme.size.touchTarget,
                            glyph = type.glyphMd,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs)) {
                            Text(
                                stringResource(Res.string.forgot_ask, vouching.name),
                                style = type.bodyStrong,
                                color = colors.textPrimary,
                            )
                            Text(
                                stringResource(Res.string.forgot_ask_detail),
                                style = type.caption,
                                color = colors.textMuted,
                            )
                        }
                    }
                    Step(number = 1, text = stringResource(Res.string.forgot_step_one, vouching.name))
                    Step(number = 2, text = stringResource(Res.string.forgot_step_two))
                    Step(number = 3, text = stringResource(Res.string.forgot_step_three))
                }
                PrimaryButton(
                    text = stringResource(Res.string.forgot_have_code),
                    onClick = onHaveCode,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.forgot_alone_title), style = type.heading, color = colors.textPrimary)
                Text(stringResource(Res.string.forgot_alone_detail), style = type.body, color = colors.textMuted)
                Text(
                    text = stringResource(Res.string.forgot_alone_command, state.member.name),
                    style = type.monoSm,
                    color = colors.textMuted,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceAlt, HearthShapes.item)
                            .padding(HearthTheme.spacing.md),
                )
            }

            Text(
                text = stringResource(Res.string.forgot_footnote),
                style = type.caption,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = HearthTheme.spacing.xxl),
            )
        }
    }
}

/**
 * The housemate who can vouch, before the hub has named them. Drawn at the real block's geometry —
 * the face, the two lines beside it, and the three steps — so nothing shifts when the name lands.
 */
@Composable
private fun ArrivingVouch() {
    SkeletonGroup {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCircle(size = HearthTheme.size.touchTarget)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
            ) {
                SkeletonBlock(widthFraction = ASK_WIDTH)
                SkeletonBlock(widthFraction = ASK_DETAIL_WIDTH, height = HearthTheme.spacing.md)
            }
        }
        repeat(STEPS) { position ->
            SkeletonBlock(
                widthFraction = STEP_WIDTHS[position],
                height = HearthTheme.spacing.md,
                position = position,
            )
        }
    }
}

/** How much of the row each stand-in fills. Unequal, so it reads as a wait and not as a grid. */
private const val ASK_WIDTH = 0.55f
private const val ASK_DETAIL_WIDTH = 0.4f
private const val STEPS = 3
private val STEP_WIDTHS = listOf(0.9f, 0.75f, 0.8f)

@Composable
private fun Step(
    number: Int,
    text: String,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Row(
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(number.toString(), style = type.monoSm, color = colors.primary)
        Text(text, style = type.label, color = colors.textMuted)
    }
}

@PreviewDayNight
@Composable
private fun PinForgotContentPreview(
    @PreviewParameter(PinForgotUiStateProvider::class) state: PinForgotUiState,
) {
    HearthTheme {
        PinForgotContent(state = state, onHaveCode = {}, onBack = {})
    }
}
