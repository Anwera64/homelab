package com.homelab.household

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.homelab.household.app.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: swaps the splash theme for Theme.HyggeHub. The splash leaves with
        // the first frame — nothing holds it on screen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val externalApps = AndroidExternalApps(this)
        setContent {
            App(externalApps = externalApps)
        }
    }
}
