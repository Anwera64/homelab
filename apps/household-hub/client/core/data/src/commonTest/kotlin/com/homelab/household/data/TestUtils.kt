package com.homelab.household.data

suspend inline fun <reified T : Throwable> assertThrowsSuspend(crossinline block: suspend () -> Unit) {
    try {
        block()
        throw AssertionError("Expected ${T::class.simpleName} to be thrown, but nothing was thrown")
    } catch (e: Throwable) {
        if (e !is T) {
            throw AssertionError("Expected ${T::class.simpleName} but was ${e::class.simpleName}: ${e.message}", e)
        }
    }
}
