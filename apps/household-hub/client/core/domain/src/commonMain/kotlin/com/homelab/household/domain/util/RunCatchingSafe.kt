package com.homelab.household.domain.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] that lets [CancellationException] through, so a cancelled coroutine stops instead
 * of carrying on as if its block had failed. Inline so the block may call suspend functions.
 */
inline fun <T> runCatchingSafe(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
