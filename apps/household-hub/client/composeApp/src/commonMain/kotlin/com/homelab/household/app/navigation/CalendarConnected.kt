package com.homelab.household.app.navigation

/**
 * What the calendar connect flow sends back when a calendar connects, over Navigation 3's
 * `ResultEventBus`. A conversation that opened the flow from a fix card fetches itself once, so
 * the card shows what the hub marked fixed; nothing polls the hub for the calendar's state.
 */
data object CalendarConnected
