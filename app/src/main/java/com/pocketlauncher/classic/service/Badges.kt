package com.pocketlauncher.classic.service

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which packages currently have a notification showing.
 *
 * Filled by [BadgeListener] once the user grants notification access; stays
 * empty otherwise, which simply means no fader ever lights up.
 */
object Badges {

    private val main = Handler(Looper.getMainLooper())

    var packages by mutableStateOf<Set<String>>(emptySet())
        private set

    fun publish(next: Set<String>) {
        main.post { packages = next }
    }
}
