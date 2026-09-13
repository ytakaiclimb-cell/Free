package com.pocketlauncher.classic.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketlauncher.classic.data.AppEntry
import com.pocketlauncher.classic.data.AppRepository
import com.pocketlauncher.classic.util.SystemApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Mode { MENU, CLOCK }

enum class Page { ROOT, APPS, ABOUT }

enum class MenuAction { OPEN_APPS, OPEN_CLOCK, OPEN_SETTINGS, OPEN_HOME_SETTINGS, OPEN_ABOUT }

data class MenuItem(val label: String, val action: MenuAction)

/**
 * Everything the click wheel drives: which screen is showing and where the
 * selection sits on it. Held by the Activity so it survives recomposition and
 * so [onResumed] can run when the user comes back from another app.
 */
class LauncherState(context: Context) {

    private val repository = AppRepository(context.applicationContext)

    val rootItems: List<MenuItem> = listOf(
        MenuItem("すべてのアプリ", MenuAction.OPEN_APPS),
        MenuItem("時計", MenuAction.OPEN_CLOCK),
        MenuItem("システム設定", MenuAction.OPEN_SETTINGS),
        MenuItem("既定のホームアプリ", MenuAction.OPEN_HOME_SETTINGS),
        MenuItem("このランチャーについて", MenuAction.OPEN_ABOUT),
    )

    var mode by mutableStateOf(Mode.MENU)
        private set
    var page by mutableStateOf(Page.ROOT)
        private set
    var rootIndex by mutableIntStateOf(0)
        private set
    var appsIndex by mutableIntStateOf(0)
        private set
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    /** Bumped on every onResume so the UI can refresh the app list. */
    var resumeTick by mutableIntStateOf(0)
        private set

    /**
     * Set when the user long-presses the centre button in clock mode. The next
     * time the launcher comes back to the foreground it drops out of clock mode.
     */
    private var returnToMenuOnResume = false

    suspend fun loadApps() {
        val loaded = withContext(Dispatchers.IO) {
            repository.refresh()
            repository.apps()
        }
        apps = loaded
        if (appsIndex > loaded.lastIndex) appsIndex = maxOf(0, loaded.lastIndex)
    }

    fun iconOf(entry: AppEntry) = repository.icon(entry)

    // ---- click wheel ----------------------------------------------------

    /** One notch of wheel rotation. Positive is clockwise, i.e. downwards. */
    fun scroll(delta: Int) {
        if (mode == Mode.CLOCK) return
        when (page) {
            Page.ROOT -> rootIndex = (rootIndex + delta).coerceIn(0, rootItems.lastIndex)
            Page.APPS -> if (apps.isNotEmpty()) {
                appsIndex = (appsIndex + delta).coerceIn(0, apps.lastIndex)
            }
            Page.ABOUT -> Unit
        }
    }

    fun select(context: Context) {
        if (mode == Mode.CLOCK) return
        when (page) {
            Page.ROOT -> dispatch(context, rootItems[rootIndex].action)
            Page.APPS -> apps.getOrNull(appsIndex)?.let { repository.launch(it) }
            Page.ABOUT -> Unit
        }
    }

    /**
     * Long press on the centre button. In clock mode this is the shortcut to the
     * device clock app; coming back afterwards drops out of clock mode.
     *
     * @return true when the gesture did something, so the caller can buzz.
     */
    fun centerLongPress(context: Context): Boolean {
        if (mode != Mode.CLOCK) return false
        if (!SystemApps.openClock(context)) return false
        returnToMenuOnResume = true
        return true
    }

    /** The MENU button, and the system back gesture. */
    fun back(): Boolean = when {
        mode == Mode.CLOCK -> {
            mode = Mode.MENU
            true
        }
        page != Page.ROOT -> {
            page = Page.ROOT
            true
        }
        else -> false
    }

    // ---- dock -----------------------------------------------------------

    fun toggleClock() {
        mode = if (mode == Mode.CLOCK) Mode.MENU else Mode.CLOCK
    }

    fun openApps() {
        mode = Mode.MENU
        page = Page.APPS
    }

    fun openRoot() {
        mode = Mode.MENU
        page = Page.ROOT
    }

    // ---- lifecycle ------------------------------------------------------

    fun onResumed() {
        if (returnToMenuOnResume) {
            returnToMenuOnResume = false
            mode = Mode.MENU
        }
        resumeTick++
    }

    private fun dispatch(context: Context, action: MenuAction) {
        when (action) {
            MenuAction.OPEN_APPS -> page = Page.APPS
            MenuAction.OPEN_CLOCK -> mode = Mode.CLOCK
            MenuAction.OPEN_SETTINGS -> SystemApps.openSettings(context)
            MenuAction.OPEN_HOME_SETTINGS -> SystemApps.openHomeSettings(context)
            MenuAction.OPEN_ABOUT -> page = Page.ABOUT
        }
    }
}
