package com.homelab.household.app.screens.whenagentsact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthSwitch
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.resources.members_unreachable
import com.homelab.household.app.resources.tool_permission_calendar_read
import com.homelab.household.app.resources.tool_permission_pdf
import com.homelab.household.app.resources.tool_permission_search
import com.homelab.household.app.resources.when_agents_act_always_asks
import com.homelab.household.app.resources.when_agents_act_back
import com.homelab.household.app.resources.when_agents_act_calendar
import com.homelab.household.app.resources.when_agents_act_calendar_add_asks
import com.homelab.household.app.resources.when_agents_act_calendar_add_auto
import com.homelab.household.app.resources.when_agents_act_calendar_change_asks
import com.homelab.household.app.resources.when_agents_act_calendar_change_auto
import com.homelab.household.app.resources.when_agents_act_calendar_remove_caption
import com.homelab.household.app.resources.when_agents_act_intro
import com.homelab.household.app.resources.when_agents_act_looking
import com.homelab.household.app.resources.when_agents_act_looking_note
import com.homelab.household.app.resources.when_agents_act_note_append_asks
import com.homelab.household.app.resources.when_agents_act_note_append_auto
import com.homelab.household.app.resources.when_agents_act_note_create_asks
import com.homelab.household.app.resources.when_agents_act_note_create_auto
import com.homelab.household.app.resources.when_agents_act_note_replace_caption
import com.homelab.household.app.resources.when_agents_act_notes
import com.homelab.household.app.resources.when_agents_act_pdf_caption
import com.homelab.household.app.resources.when_agents_act_read_calendar_caption
import com.homelab.household.app.resources.when_agents_act_retry
import com.homelab.household.app.resources.when_agents_act_runs_freely
import com.homelab.household.app.resources.when_agents_act_save_failed
import com.homelab.household.app.resources.when_agents_act_save_unreachable
import com.homelab.household.app.resources.when_agents_act_search_caption
import com.homelab.household.app.resources.when_agents_act_secret_note
import com.homelab.household.app.resources.when_agents_act_title
import com.homelab.household.app.screens.conversation.toolLabel
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.HubTool
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.presentation.whenagentsact.SaveFailure
import com.homelab.household.presentation.whenagentsact.ToolGroup
import com.homelab.household.presentation.whenagentsact.WhenAgentsActStatus
import com.homelab.household.presentation.whenagentsact.WhenAgentsActUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "When agents act" (board "Tools · when agents act"): what agents only look at, which runs
 * freely, and each write with its switch, or a lock on the two that can't be undone from a chat.
 *
 * The reading list is this screen's own copy, so it draws at once; only the writes come from the hub.
 * Stateless — [WhenAgentsActScreen] owns the ViewModel.
 */
@Composable
fun WhenAgentsActContent(
    state: WhenAgentsActUiState,
    onBack: () -> Unit,
    onToggle: (tool: String, action: ToolAction, automatic: Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.when_agents_act_back)) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = HearthTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            ) {
                Text(stringResource(Res.string.when_agents_act_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.when_agents_act_intro), style = type.body, color = colors.textMuted)
            }

            Section(
                title = stringResource(Res.string.when_agents_act_looking),
                note = stringResource(Res.string.when_agents_act_looking_note),
            ) {
                READS.forEachIndexed { index, (label, caption) ->
                    if (index > 0) HorizontalDivider(color = colors.outlineSoft)
                    ActionRow(label = stringResource(label), caption = stringResource(caption)) {
                        HearthChip(
                            label = stringResource(Res.string.when_agents_act_runs_freely),
                            variant = ChipVariant.Success,
                        )
                    }
                }
            }

            state.groups.forEach { group ->
                val failure = state.saveFailure?.takeIf { state.saveFailureIn == group.group }
                Section(
                    title = stringResource(group.group.title()),
                    failure = failure?.let { stringResource(it.words()) },
                ) {
                    group.approvals.forEachIndexed { index, approval ->
                        if (index > 0) HorizontalDivider(color = colors.outlineSoft)
                        WriteRow(approval = approval, onToggle = onToggle)
                    }
                }
            }

            if (state.status == WhenAgentsActStatus.Unreachable || state.status == WhenAgentsActStatus.Failed) {
                Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                    val words =
                        if (state.status ==
                            WhenAgentsActStatus.Unreachable
                        ) {
                            Res.string.members_unreachable
                        } else {
                            Res.string.members_failed
                        }
                    Text(stringResource(words), style = type.caption, color = colors.error)
                    SecondaryButton(text = stringResource(Res.string.when_agents_act_retry), onClick = onRetry)
                }
            }

            SecretNote(modifier = Modifier.padding(bottom = HearthTheme.spacing.xxl))
        }
    }
}

/** What agents only look at. Reading changes nothing, so none of it ever asks. */
private val READS =
    listOf(
        Res.string.tool_permission_calendar_read to Res.string.when_agents_act_read_calendar_caption,
        Res.string.tool_permission_search to Res.string.when_agents_act_search_caption,
        Res.string.tool_permission_pdf to Res.string.when_agents_act_pdf_caption,
    )

@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    failure: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(start = HearthTheme.spacing.xs),
            style = type.overline,
            color = colors.textMuted,
        )
        BentoCard(modifier = Modifier.fillMaxWidth()) { content() }
        if (note != null) {
            Text(
                note,
                modifier = Modifier.padding(start = HearthTheme.spacing.xs),
                style = type.caption,
                color = colors.textMuted,
            )
        }
        if (failure != null) {
            Text(
                failure,
                modifier = Modifier.padding(start = HearthTheme.spacing.xs),
                style = type.caption,
                color = colors.error,
            )
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HearthTheme.size.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs)) {
            Text(label, style = HearthTheme.typography.body, color = HearthTheme.colors.textPrimary)
            Text(caption, style = HearthTheme.typography.caption, color = HearthTheme.colors.textMuted)
        }
        trailing()
    }
}

/** A write: its switch, or, for removing and replacing, a lock that says it always asks. */
@Composable
private fun WriteRow(
    approval: ToolApproval,
    onToggle: (tool: String, action: ToolAction, automatic: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(toolLabel(approval.tool, approval.action).permission)
    ActionRow(label = label, caption = stringResource(captionOf(approval)), modifier = modifier) {
        if (approval.alwaysAsks) {
            HearthChip(label = stringResource(Res.string.when_agents_act_always_asks), icon = HearthIcon.SecretLocked)
        } else {
            HearthSwitch(
                checked = approval.automatic,
                onCheckedChange = { onToggle(approval.tool, approval.action, it) },
                contentDescription = label,
            )
        }
    }
}

@Composable
private fun SecretNote(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = HearthShapes.item, color = HearthTheme.colors.surfaceAlt) {
        Row(
            modifier = Modifier.padding(HearthTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        ) {
            HearthIconImage(
                icon = HearthIcon.SecretLocked,
                contentDescription = null,
                size = HearthTheme.size.iconLg,
                tint = HearthTheme.colors.textMuted,
            )
            Text(
                stringResource(Res.string.when_agents_act_secret_note),
                style = HearthTheme.typography.caption,
                color = HearthTheme.colors.textMuted,
            )
        }
    }
}

private fun ToolGroup.title(): StringResource =
    when (this) {
        ToolGroup.Calendar -> Res.string.when_agents_act_calendar
        ToolGroup.Notes -> Res.string.when_agents_act_notes
    }

private fun SaveFailure.words(): StringResource =
    when (this) {
        SaveFailure.Unreachable -> Res.string.when_agents_act_save_unreachable
        SaveFailure.Failed -> Res.string.when_agents_act_save_failed
    }

/** Each write's caption while it asks, and once it goes straight in. Locked writes say the same either way. */
private val CAPTIONS: Map<Pair<String, ToolAction>, Pair<StringResource, StringResource>> =
    mapOf(
        (HubTool.CALENDAR_WRITE to ToolAction.Create) to
            (Res.string.when_agents_act_calendar_add_asks to Res.string.when_agents_act_calendar_add_auto),
        (HubTool.CALENDAR_WRITE to ToolAction.Update) to
            (Res.string.when_agents_act_calendar_change_asks to Res.string.when_agents_act_calendar_change_auto),
        (HubTool.CALENDAR_WRITE to ToolAction.Delete) to
            (Res.string.when_agents_act_calendar_remove_caption to Res.string.when_agents_act_calendar_remove_caption),
        (HubTool.DOCUMENT_WRITER to ToolAction.Create) to
            (Res.string.when_agents_act_note_create_asks to Res.string.when_agents_act_note_create_auto),
        (HubTool.DOCUMENT_WRITER to ToolAction.Append) to
            (Res.string.when_agents_act_note_append_asks to Res.string.when_agents_act_note_append_auto),
        (HubTool.DOCUMENT_WRITER to ToolAction.Replace) to
            (Res.string.when_agents_act_note_replace_caption to Res.string.when_agents_act_note_replace_caption),
    )

/** What the switch means as it stands. The view model only hands over writes listed in [CAPTIONS]. */
private fun captionOf(approval: ToolApproval): StringResource {
    val (asks, auto) = CAPTIONS.getValue(approval.tool to approval.action)
    return if (approval.automatic) auto else asks
}

@PreviewDayNight
@Composable
private fun WhenAgentsActContentPreview(
    @PreviewParameter(WhenAgentsActUiStateProvider::class) state: WhenAgentsActUiState,
) {
    HearthTheme {
        WhenAgentsActContent(state = state, onBack = {}, onToggle = { _, _, _ -> }, onRetry = {})
    }
}
