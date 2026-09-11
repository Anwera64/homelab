package com.homelab.household

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.homelab.household.app.App
import com.homelab.household.data.di.DEFAULT_BASE_URL

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(hubAddress = DEFAULT_BASE_URL.substringAfter("://"))
        }
    }
}
