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
    fun send_passes_the_current_text() =
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
    fun send_is_always_clickable_even_when_empty() =
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
    fun the_composer_never_clears_the_text_itself() =
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
    fun send_is_big_enough_to_hit() =
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
    fun the_keyboard_send_key_sends_the_message() =
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
    fun the_keyboard_offers_send_rather_than_a_newline() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    MessageComposer(value = "", onValueChange = {}, onSend = {}, placeholder = "Message…")
                }
            }

            onMessageComposer { seesTheKeyboardOffersSend() }
        }

    @Test
    fun shows_the_placeholder_when_empty() =
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
