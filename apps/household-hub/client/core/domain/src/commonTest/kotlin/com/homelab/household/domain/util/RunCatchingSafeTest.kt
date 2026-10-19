package com.homelab.household.domain.util

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RunCatchingSafeTest {
    @Test
    fun `GIVEN a block returning a value WHEN it is run safely THEN the value comes back as a success`() {
        val result = runCatchingSafe { 42 }

        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `GIVEN a block throwing an ordinary exception WHEN it is run safely THEN the exception comes back as a failure`() {
        val result = runCatchingSafe { throw IllegalStateException("boom") }

        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    /** Swallowing cancellation would let a cancelled coroutine carry on as if it had failed. */
    @Test
    fun `GIVEN a block that is cancelled WHEN it is run safely THEN the cancellation is rethrown rather than wrapped`() =
        runTest {
            assertFailsWith<CancellationException> {
                runCatchingSafe { throw CancellationException("cancelled") }
            }
        }
}
