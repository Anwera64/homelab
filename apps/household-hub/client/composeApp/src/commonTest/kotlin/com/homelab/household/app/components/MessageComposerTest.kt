package com.homelab.household.app.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MessageComposerTest {
    @Test
    fun `GIVEN a message composer WHEN a message is typed and send is tapped THEN the typed text is sent`() =
        runComposeUiTest {
            var text by mutableStateOf("")
            var sent: String? = null
            setContent {
                StillTheme {
                    MessageComposer(
                        value = text,
                        onValueChange = { text = it },
                        onSend = { sent = it },
                        placeholder = "Message the Coordinator…",
                    )
                }
            }

            onMessageComposer {
                typesMessage("What's left this week?")
                tapsSend()
            }

            assertEquals("What's left this week?", sent)
        }

    @Test
    fun `GIVEN an empty message composer WHEN send is tapped THEN the tap still reaches the send handler`() =
        runComposeUiTest {
            var sends = 0
            setContent {
                StillTheme {
                    MessageComposer(value = "", onValueChange = {}, onSend = { sends++ }, placeholder = "Message…")
                }
            }

            onMessageComposer {
                seesSendReadyToTap()
                tapsSend()
            }

            assertEquals(1, sends)
        }

    @Test
    fun `GIVEN a message composer holding text WHEN send is tapped THEN the text is still there`() =
        runComposeUiTest {
            var text by mutableStateOf("Move dinner to 20:00")
            setContent {
                StillTheme {
                    MessageComposer(value = text, onValueChange = { text = it }, onSend = {}, placeholder = "Message…")
                }
            }

            onMessageComposer {
                tapsSend()

                seesTypedMessage("Move dinner to 20:00")
            }
        }

    /** Send used to be 44dp square, under the floor — the one thing the scale actually grew. */
    @Test
    fun `GIVEN a message composer WHEN it is shown THEN the send button is big enough to hit`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    MessageComposer(value = "", onValueChange = {}, onSend = {}, placeholder = "Message…")
                }
            }

            onMessageComposer { seesSendBigEnoughToHit() }
        }

    /**
     * The keyboard's own key sends, because typing a message and reaching for a button is one
     * motion too many — and the key says "Send" rather than offering a newline, so it has to.
     */
    @Test
    fun `GIVEN a message composer WHEN a message is typed and the keyboard send key is pressed THEN the typed text is sent`() =
        runComposeUiTest {
            var text by mutableStateOf("")
            var sent: String? = null
            setContent {
                StillTheme {
                    MessageComposer(
                        value = text,
                        onValueChange = { text = it },
                        onSend = { sent = it },
                        placeholder = "Message the Coordinator…",
                    )
                }
            }

            onMessageComposer {
                typesMessage("Move dinner to 20:00")
                pressesTheKeyboardSendKey()
            }

            assertEquals("Move dinner to 20:00", sent)
        }

    @Test
    fun `GIVEN a message composer WHEN it is shown THEN the keyboard offers send rather than a newline`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    MessageComposer(value = "", onValueChange = {}, onSend = {}, placeholder = "Message…")
                }
            }

            onMessageComposer { seesTheKeyboardOffersSend() }
        }

    @Test
    fun `GIVEN an empty message composer WHEN it is shown THEN the placeholder is visible`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    MessageComposer(
                        value = "",
                        onValueChange = {},
                        onSend = {},
                        placeholder = "Message the Coordinator…",
                    )
                }
            }

            onMessageComposer { seesPlaceholder("Message the Coordinator…") }
        }
}
