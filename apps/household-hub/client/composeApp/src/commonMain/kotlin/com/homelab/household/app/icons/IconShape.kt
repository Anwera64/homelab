package com.homelab.household.app.icons

sealed interface IconShape {
    val pathData: String

    data class Stroke(override val pathData: String) : IconShape
    data class Solid(override val pathData: String) : IconShape
}
