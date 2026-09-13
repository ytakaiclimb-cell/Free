package com.pocketlauncher.classic.util

import android.content.Context
import android.media.AudioManager

/** Media volume, plus the "is something playing" flag the wheel spins on. */
class Volume(context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val max: Int get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    val level: Int get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    val musicActive: Boolean get() = runCatching { audio.isMusicActive }.getOrDefault(false)

    /** Moves media volume by [delta] notches; returns the new level. */
    fun nudge(delta: Int): Int {
        val next = (level + delta).coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0) }
        return next
    }
}
