package com.homelab.household.presentation.leavehousehold

/** What happens once the account is gone. */
sealed interface LeaveHouseholdEvent {
    data object Left : LeaveHouseholdEvent
}
