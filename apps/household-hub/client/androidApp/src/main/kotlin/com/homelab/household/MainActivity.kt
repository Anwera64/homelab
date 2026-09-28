package com.homelab.household

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.homelab.household.app.App

class MainActivity : ComponentActivity() {
    private lateinit var externalApps: AndroidExternalApps

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: swaps the splash theme for Theme.HyggeHub. The splash leaves with
        // the first frame — nothing holds it on screen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        externalApps = AndroidExternalApps(this)
        setContent {
            App(externalApps = externalApps)
        }
    }

    /** A browser sign-in coming back (`hyggehub://calendar/…`), delivered here because the activity is `singleTask`. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { externalApps.onRedirect(it.toString()) }
    }

    // After onNewIntent, so a sign-in that came back has already been handed over.
    override fun onResume() {
        super.onResume()
        externalApps.onResumed()
    }
}
