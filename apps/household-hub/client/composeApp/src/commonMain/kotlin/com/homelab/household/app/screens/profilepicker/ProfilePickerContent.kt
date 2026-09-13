package com.homelab.household.app.screens.profilepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.picker_failed
import com.homelab.household.app.resources.picker_hint
import com.homelab.household.app.resources.picker_member_count
import com.homelab.household.app.resources.picker_member_description
import com.homelab.household.app.resources.picker_retry
import com.homelab.household.app.resources.picker_title
import com.homelab.household.app.resources.picker_unreachable
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.profilepicker.PickerStatus
import com.homelab.household.presentation.profilepicker.ProfilePickerUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Who's here?" — the household's faces, centred. A full-screen state, so it doesn't scroll.
 *
 * Stateless — [ProfilePickerScreen] owns the ViewModel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePickerContent(
    state: ProfilePickerUiState,
    onMemberSelected: (Member) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val status = state.status

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
            .padding(horizontal = HearthTheme.spacing.xxl, vertical = HearthTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxxl, Alignment.CenterVertically)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)
        ) {
            Text(
                text = stringResource(Res.string.picker_title),
                style = type.hero,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            if (status is PickerStatus.Loaded) {
                Text(
                    text = pluralStringResource(Res.plurals.picker_member_count, status.members.size, status.members.size),
                    style = type.body,
                    color = colors.textMuted
                )
            }
        }

        when (status) {
            PickerStatus.Loading -> LinearProgressIndicator(
                modifier = Modifier
                    .width(HearthTheme.size.progressTrack)
                    .height(HearthTheme.size.progressHeight),
                color = colors.primary,
                trackColor = colors.outline,
                strokeCap = StrokeCap.Round,
                gapSize = HearthTheme.spacing.none
            )

            is PickerStatus.Loaded -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl)
                ) {
                    status.members.forEach { member ->
                        Face(member = member, onClick = { onMemberSelected(member) })
                    }
                }
                Text(
                    text = stringResource(Res.string.picker_hint),
                    style = type.label,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }

            PickerStatus.Unreachable -> Problem(stringResource(Res.string.picker_unreachable), onRetry)
            PickerStatus.Failed -> Problem(stringResource(Res.string.picker_failed), onRetry)
        }
    }
}

@Composable
private fun Face(member: Member, onClick: () -> Unit) {
    val colors = HearthTheme.colors
    Column(
        modifier = Modifier
            .clip(HearthShapes.item)
            .clickable(onClickLabel = stringResource(Res.string.picker_member_description, member.name), onClick = onClick)
            .padding(HearthTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
    ) {
        MemberAvatar(
            name = member.name,
            colour = member.avatarColor,
            size = HearthTheme.size.avatarHero,
            glyph = HearthTheme.typography.glyphHero
        )
        Text(text = member.name, style = HearthTheme.typography.bodyLarge, color = colors.textMuted)
    }
}

@Composable
private fun Problem(message: String, onRetry: () -> Unit) {
    val colors = HearthTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl)
    ) {
        Text(
            text = message,
            style = HearthTheme.typography.body,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = HearthTheme.size.readingWidth)
        )
        PrimaryButton(
            text = stringResource(Res.string.picker_retry),
            onClick = onRetry,
            icon = HearthIcon.Retry,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@DayNightPreviews
@Composable
private fun ProfilePickerContentPreview(
    @PreviewParameter(ProfilePickerUiStateProvider::class) state: ProfilePickerUiState
) {
    HearthTheme {
        ProfilePickerContent(state = state, onMemberSelected = {}, onRetry = {})
    }
}
