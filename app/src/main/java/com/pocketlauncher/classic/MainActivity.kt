package com.pocketlauncher.classic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pocketlauncher.classic.ui.ClassicApp
import com.pocketlauncher.classic.ui.LauncherState

class MainActivity : ComponentActivity() {

    private lateinit var state: LauncherState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(this)
        setContent { ClassicApp(state) }
    }

    override fun onResume() {
        super.onResume()
        // Drops back to the home ring when returning from the clock app.
        state.onResumed()
    }
}
