package com.homelab.household.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.homelab.household.app.platform.LocalDragHidesKeyboard
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test

/**
 * Screens scroll: the content region moves, header and navigation stay pinned (design notes §2).
 * The content is handed the space the bars and the window leave it, and scrolls however it likes.
 */
@OptIn(ExperimentalTestApi::class)
class HearthScaffoldTest {
    @Test
    fun `GIVEN a scaffold with a header a bottom bar and long content WHEN the content is scrolled THEN the header and bottom bar stay on screen`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthScaffold(
                        header = { Text("HyggeHub · Household") },
                        bottomBar = { Text("Bottom bar") },
                    ) { padding ->
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(padding),
                        ) {
                            repeat(120) { Text("Row $it") }
                        }
                    }
                }
            }

            onHearthScaffold {
                scrollsToAndSees("Row 119")

                doesNotSeeOnScreen("Row 0")
                seesText("HyggeHub · Household")
                seesText("Bottom bar")
            }
        }

    @Test
    fun `GIVEN a scaffold holding a lazy list WHEN the list is scrolled to its end THEN the last row and the header are visible`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthScaffold(header = { Text("Chats") }) { padding ->
                        LazyColumn(modifier = Modifier.testTag("list"), contentPadding = padding) {
                            items(500) { Text("Chat $it") }
                        }
                    }
                }
            }

            onHearthScaffold {
                scrollsTheListTo("list", 499)

                seesText("Chat 499")
                seesText("Chats")
            }
        }

    @Test
    fun `GIVEN a scaffold with a header and a bottom bar WHEN it is shown THEN the content sits between the two bars`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthScaffold(
                        header = {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(BAR),
                            )
                        },
                        bottomBar = {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(BAR),
                            )
                        },
                        contentWindowInsets = WindowInsets(0.dp),
                    ) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .testTag("content"),
                        )
                    }
                }
            }

            onHearthScaffold { seesContentBetween("content", top = BAR, bottom = BAR) }
        }

    @Test
    fun `GIVEN a scaffold without bars but with a status bar inset WHEN it is shown THEN the inset and the gutter reach the content`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthScaffold(contentWindowInsets = WindowInsets(top = STATUS_BAR)) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .testTag("content"),
                        )
                    }
                }
            }

            onHearthScaffold { seesContentAt("content", top = STATUS_BAR, left = 20.dp) }
        }

    @Test
    fun `GIVEN a scaffold with a focused field WHEN empty space is tapped THEN the field loses focus`() =
        runComposeUiTest {
            setContent { StillTheme { FieldAndButton() } }

            onHearthScaffold {
                focusesTheField(FIELD)
                tapsEmptySpace(EMPTY)

                seesTheFieldNotFocused(FIELD)
            }
        }

    @Test
    fun `GIVEN a focused field on a screen that cannot scroll WHEN empty space is dragged THEN the field keeps focus`() =
        runComposeUiTest {
            setContent { StillTheme { FieldAndButton() } }

            onHearthScaffold {
                focusesTheField(FIELD)
                dragsUp(EMPTY)

                seesTheFieldFocused(FIELD)
            }
        }

    @Test
    fun `GIVEN a scaffold with a focused field WHEN a button is tapped THEN the button runs`() =
        runComposeUiTest {
            setContent { StillTheme { FieldAndButton() } }

            onHearthScaffold {
                focusesTheField(FIELD)
                tapsTheButton("Save")

                seesText("Saved 1")
            }
        }

    @Test
    fun `GIVEN drags hide the keyboard WHEN the form is dragged THEN the field loses focus and the form scrolls`() =
        runComposeUiTest {
            setContent { StillTheme { LongForm(dragHidesKeyboard = true) } }

            onHearthScaffold {
                focusesTheField(FIELD)
                dragsUp(FORM)

                seesTheFieldNotFocused(FIELD)
                doesNotSeeOnScreen("Row 0")
            }
        }

    @Test
    fun `GIVEN drags do not hide the keyboard WHEN the form is dragged THEN the field keeps focus`() =
        runComposeUiTest {
            setContent { StillTheme { LongForm(dragHidesKeyboard = false) } }

            onHearthScaffold {
                focusesTheField(FIELD)
                dragsUp(FORM)

                seesTheFieldFocused(FIELD)
            }
        }

    @Test
    fun `GIVEN drags hide the keyboard WHEN a drag moves along the focused field THEN the field keeps focus`() =
        runComposeUiTest {
            setContent { StillTheme { LongForm(dragHidesKeyboard = true) } }

            onHearthScaffold {
                focusesTheField(FIELD)
                dragsAlong(FIELD)

                seesTheFieldFocused(FIELD)
            }
        }

    private companion object {
        val BAR = 56.dp
        val STATUS_BAR = 24.dp
        const val FIELD = "field"
        const val EMPTY = "empty"
        const val FORM = "form"
    }
}

/** A form: one field, one button counting its taps, and empty space below them. */
@Composable
private fun FieldAndButton() {
    var text by remember { mutableStateOf("") }
    var saves by remember { mutableIntStateOf(0) }
    HearthScaffold(header = { Text("Form") }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            BasicTextField(value = text, onValueChange = { text = it }, modifier = Modifier.testTag("field"))
            Button(onClick = { saves++ }) { Text(if (saves == 0) "Save" else "Saved $saves") }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("empty"),
            )
        }
    }
}

/** A form longer than the screen, its one field holding more text than fits on its line. */
@Composable
private fun LongForm(dragHidesKeyboard: Boolean) {
    var text by remember { mutableStateOf("A title long enough to scroll sideways inside its own field ".repeat(4)) }
    CompositionLocalProvider(LocalDragHidesKeyboard provides dragHidesKeyboard) {
        HearthScaffold(header = { Text("Form") }) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .testTag("form"),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("field"),
                )
                repeat(60) { Text("Row $it") }
            }
        }
    }
}
