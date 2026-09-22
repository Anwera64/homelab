package com.homelab.household.app.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A destination's ViewModels belong to that destination: they go when it leaves the stack, and
 * coming back starts a fresh one — so a PIN pad reopened for someone doesn't still hold the
 * digits, or the lock, from last time.
 */
@OptIn(ExperimentalTestApi::class)
class EntryViewModelsTest {
    private class Counting : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun leaving_clears_the_view_models_and_coming_back_makes_new_ones() =
        runComposeUiTest {
            var shown by mutableStateOf(true)
            val seen = mutableListOf<Counting>()

            setContent {
                if (shown) {
                    WithEntryViewModels {
                        val vm = viewModel { Counting() }
                        if (seen.lastOrNull() !== vm) seen += vm
                        Text("entry")
                    }
                }
            }
            waitForIdle()

            shown = false
            waitForIdle()
            assertEquals(true, seen.single().cleared)

            shown = true
            waitForIdle()
            assertEquals(2, seen.size)
            assertEquals(false, seen.last().cleared)
        }
}
