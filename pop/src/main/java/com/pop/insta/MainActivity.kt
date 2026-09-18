package com.pop.insta

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.pop.insta.ui.EditorState
import com.pop.insta.ui.PopScreen

class MainActivity : ComponentActivity() {

    private lateinit var state: EditorState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = EditorState(this, lifecycleScope)
        setContent { PopScreen(state) }
        handOff(intent)
    }

    /** A POP shared in from the gallery, Drive, or a PDF viewer. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handOff(intent)
    }

    private fun handOff(intent: Intent?) {
        val uri = incoming(intent) ?: return
        state.open(listOf(uri))
    }

    private fun incoming(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> extraStream(intent)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun extraStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
}
