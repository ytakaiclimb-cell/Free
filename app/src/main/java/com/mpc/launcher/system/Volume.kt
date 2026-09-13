package com.mpc.launcher.system

import android.content.Context
import android.media.AudioManager

/** Media volume, as a 0..1 value the fader can ride. */
class Volume(context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val max: Int get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private var beforeMute = 0

    val level: Float
        get() = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat() }
            .getOrDefault(0f)

    val muted: Boolean get() = level <= 0f

    fun set(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    /** Tap behaviour: drops to zero, and a second tap restores what it was. */
    fun toggleMute() {
        val current = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        if (current > 0) {
            beforeMute = current
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
        } else {
            val restore = if (beforeMute > 0) beforeMute else max / 3
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0) }
        }
    }
}
