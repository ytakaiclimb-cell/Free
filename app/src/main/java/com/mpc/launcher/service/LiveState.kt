package com.mpc.launcher.service

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which packages currently have a notification showing. Pads glow on these. */
object Badges {

    private val main = Handler(Looper.getMainLooper())

    var packages by mutableStateOf<Set<String>>(emptySet())
        private set

    fun publish(next: Set<String>) {
        main.post { packages = next }
    }
}

/** What the media module shows. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
) {
    val line: String get() = if (artist.isBlank()) title else "$title — $artist"
}
