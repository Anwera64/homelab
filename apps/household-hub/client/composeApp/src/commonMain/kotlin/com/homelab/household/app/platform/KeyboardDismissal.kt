package com.homelab.household.app.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether scrolling a screen hides the keyboard, as iOS apps do: there it is the other way to put
 * the keyboard away besides a tap. Android has a back button for it, so it keeps its keyboard while
 * a form scrolls to the next field. [App] provides it per platform.
 */
val LocalDragHidesKeyboard = staticCompositionLocalOf { false }
