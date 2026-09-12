package com.homelab.household.domain.util

import com.homelab.household.domain.assertThrowsSuspend
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RunCatchingSafeTest {

    @Test
    fun wraps_a_returned_value_as_success() {
        val result = runCatchingSafe { 42 }

        assertEquals(42, result.getOrNull())
    }

    @Test
    fun wraps_an_ordinary_exception_as_failure() {
        val result = runCatchingSafe { throw IllegalStateException("boom") }

        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    /** Swallowing cancellation would let a cancelled coroutine carry on as if it had failed. */
    @Test
    fun rethrows_cancellation_instead_of_wrapping_it() = runTest {
        assertThrowsSuspend<CancellationException> {
            runCatchingSafe { throw CancellationException("cancelled") }
        }
    }
}
