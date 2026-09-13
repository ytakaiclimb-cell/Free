package com.mpc.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.mpc.launcher.ui.LauncherScreen
import com.mpc.launcher.ui.LauncherState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var state: LauncherState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(this)
        lifecycleScope.launch { state.runTicker() }
        setContent { LauncherScreen(state) }
    }

    override fun onResume() {
        super.onResume()
        state.onResumed()
    }

    /** Pressing HOME while already home closes whatever is open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        state.dismissTop()
    }
}
