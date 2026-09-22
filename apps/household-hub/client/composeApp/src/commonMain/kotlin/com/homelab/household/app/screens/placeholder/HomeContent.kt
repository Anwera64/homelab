package com.homelab.household.app.screens.placeholder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.components.HearthBottomNav
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.NavTab
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.home_detail
import com.homelab.household.app.resources.home_title
import com.homelab.household.app.resources.profile_open
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.User
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomePlaceholderContent(
    member: User?,
    onProfile: () -> Unit,
    description: String,
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    HearthScaffold(
        modifier = modifier,
        bottomBar = tabs,
        header = {
            Box(
                modifier =
                    Modifier
                        .safeContentPadding()
                        .fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                MemberAvatar(
                    name = member?.fullName.orEmpty(),
                    colour = member?.avatarColor ?: DEFAULT_COLOUR,
                    size = HearthTheme.size.touchTarget,
                    glyph = HearthTheme.typography.glyphMd,
                    modifier =
                        Modifier
                            .clickable(onClick = onProfile)
                            .semantics { contentDescription = description },
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            PlaceholderContent(
                title = stringResource(Res.string.home_title),
                detail = stringResource(Res.string.home_detail),
            )
        }
    }
}

/** The default colour a member wears when the hub hasn't said. */
private const val DEFAULT_COLOUR = "#3C6E4E"

@PreviewDayNight
@Composable
private fun HomePlaceHolderPreview() =
    HearthTheme {
        HomePlaceholderContent(
            modifier = Modifier,
            member =
                User(
                    id = "",
                    fullName = "Full name",
                    avatarColor = DEFAULT_COLOUR,
                    isAdmin = false,
                    isActive = true,
                ),
            onProfile = {},
            description = stringResource(Res.string.profile_open),
            tabs = {
                HearthBottomNav(selected = NavTab.Household, onSelect = {}, onNewChat = {})
            },
        )
    }
