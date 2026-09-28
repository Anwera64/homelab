package com.homelab.household.presentation.googlesignin

data class GoogleSignInUiState(
    val status: GoogleSignInStatus = GoogleSignInStatus.Idle,
) {
    /** The button waits while the hub is asked or the browser is open. */
    val busy: Boolean get() = status == GoogleSignInStatus.Starting || status == GoogleSignInStatus.InBrowser
}
