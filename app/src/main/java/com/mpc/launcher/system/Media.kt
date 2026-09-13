package com.mpc.launcher.system

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.mpc.launcher.service.NotificationBridge
import com.mpc.launcher.service.NowPlaying

/**
 * Reads whatever is playing through the active media sessions. Needs
 * notification access; without it [nowPlaying] simply stays null.
 */
class Media(private val context: Context) {

    private val component = ComponentName(context, NotificationBridge::class.java)

    private fun controllers(): List<MediaController> = runCatching {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager.getActiveSessions(component)
    }.getOrDefault(emptyList())

    /** The session that is playing, else the most recent one. */
    private fun active(): MediaController? {
        val all = controllers()
        return all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: all.firstOrNull()
    }

    fun nowPlaying(): NowPlaying? {
        val controller = active() ?: return null
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (title.isBlank()) return null
        return NowPlaying(
            title = title,
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    /** Toggles the active session. Returns false when there is nothing to toggle. */
    fun togglePlayPause(): Boolean {
        val controller = active() ?: return false
        val transport = controller.transportControls
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            transport.pause()
        } else {
            transport.play()
        }
        return true
    }
}
