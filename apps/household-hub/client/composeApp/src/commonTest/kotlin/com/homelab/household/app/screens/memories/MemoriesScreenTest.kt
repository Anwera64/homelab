package com.homelab.household.app.screens.memories

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.runScreenTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The memory page, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class MemoriesScreenTest {
    private val wait = 5_000L

    private val long =
        "Sam is vegetarian and doesn’t eat fish. He eats eggs and cheese, avoids gelatine, takes oat milk " +
            "in his coffee, and doesn’t like mushrooms or anything very spicy, except his grandmother’s curry."

    private fun household() =
        FakeMembersHub().apply {
            remembers("p1", "Prefers dinner after 20:00 on weekdays.")
            remembers("p2", "Studio Jury on Friday 17 October, 09:00.", expiresAt = "2026-10-17T10:00:00Z")
            remembers("p3", "Is vegetarian, but cooks fish for Liam.", youAsked = true)
            remembers("h1", "Dinner together on Saturday 18 October, 20:00.", household = true)
            remembers("h2", "The boiler service is booked yearly in October.", household = true, mine = false)
            remembers("h3", long, household = true, mine = false)
        }

    @Test
    fun `GIVEN memories WHEN the page opens THEN Yours lists yours with their agent and until and You asked`() {
        runScreenTest {
            memoriesScreen(household())

            onMemories {
                seesTheTitle()
                seesTheTabs(yours = 3, household = 3)
                seesMemory("Prefers dinner after 20:00 on weekdays.")
                seesItWasNotedBy("Coordinator")
                seesUntil(day = 17)
                seesYouAsked()
                canRemove("Prefers dinner after 20:00 on weekdays.")
                doesNotSee("The boiler service is booked yearly in October.")
            }
        }
    }

    @Test
    fun `GIVEN household memories WHEN Household is opened THEN only your own have X and long ones show in full and Secret Mode is noted`() {
        runScreenTest {
            memoriesScreen(household())

            onMemories {
                opensHousehold(count = 3)
                seesMemory("Dinner together on Saturday 18 October, 20:00.")
                canRemove("Dinner together on Saturday 18 October, 20:00.")
                seesMemory("The boiler service is booked yearly in October.")
                cannotRemove("The boiler service is booked yearly in October.")
                seesItWasNotedBy("Liam")
                seesMemory(long)
                seesTheSecretModeNote()
            }
        }
    }

    @Test
    fun `GIVEN a memory WHEN X is tapped THEN it turns into Removed in place and Undo brings it back`() {
        val hub = household()

        runScreenTest {
            memoriesScreen(hub)

            onMemories {
                removes("Prefers dinner after 20:00 on weekdays.")
                seesItRemoved()
                seesTheTabs(yours = 2, household = 3)
            }
            waitUntil(timeoutMillis = wait) { !hub.isRemembered("p1") }

            onMemories {
                undoes()
                seesNothingRemoved()
                seesTheTabs(yours = 3, household = 3)
            }
            waitUntil(timeoutMillis = wait) { hub.isRemembered("p1") }
        }
    }

    @Test
    fun `GIVEN the hub won't remove WHEN X is tapped THEN the memory stays and the page says why`() {
        val hub = household().apply { refusesMemoryRemoval() }

        runScreenTest {
            memoriesScreen(hub)

            onMemories {
                removes("Prefers dinner after 20:00 on weekdays.")
                seesTheRemovalFailed()
                seesNothingRemoved()
                seesMemory("Prefers dinner after 20:00 on weekdays.")
            }
            assertTrue(hub.isRemembered("p1"))
        }
    }

    @Test
    fun `GIVEN changes to check WHEN the page opens THEN a band counts them and opens Changes to check`() {
        val hub =
            household().apply {
                readsAChange("c1", stored = "Is vegetarian.", read = "Eats fish again.")
                readsAChange("c2", stored = "Works late on Thursdays.", read = "Works late on Fridays.")
            }
        var opened = 0

        runScreenTest {
            memoriesScreen(hub, onChangesToCheck = { opened++ })

            onMemories {
                seesTheBand(count = 2)
                opensTheBand()
            }
            waitUntil(timeoutMillis = wait) { opened == 1 }
        }
    }

    @Test
    fun `GIVEN nothing to check WHEN the page opens THEN there is no band`() {
        runScreenTest {
            memoriesScreen(household())

            onMemories {
                seesTheTitle()
                seesNoBand()
            }
        }
    }

    @Test
    fun `GIVEN nothing is remembered WHEN the page opens THEN it says so`() {
        runScreenTest {
            memoriesScreen(FakeMembersHub())

            onMemories {
                seesTheTabs(yours = 0, household = 0)
                seesNothingRemembered()
            }
        }
    }

    @Test
    fun `GIVEN the hub is down WHEN the page opens THEN it says it can't reach the hub and offers Try again`() {
        val hub = household().apply { isOffline() }

        runScreenTest {
            memoriesScreen(hub)

            onMemories { seesTheHubCannotBeReached() }
            assertFalse(hub.requests.any { it.contains("restore") })
        }
    }

    @Test
    fun every_previewed_state_draws() {
        MemoriesUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                memoriesContent(state)
                waitForIdle()
            }
        }
    }
}
