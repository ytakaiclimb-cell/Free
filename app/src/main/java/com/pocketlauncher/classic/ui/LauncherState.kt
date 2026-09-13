package com.pocketlauncher.classic.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketlauncher.classic.data.AppEntry
import com.pocketlauncher.classic.data.AppRepository
import com.pocketlauncher.classic.data.Prefs
import com.pocketlauncher.classic.util.SystemApps
import com.pocketlauncher.classic.util.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Screen { HOME, MENU, CLOCK, DRAWER }

data class MenuEntry(val label: String, val action: MenuAction, val enabled: Boolean = true)

enum class MenuAction {
    APPS, RECENTS, FAVORITES, COVER_FLOW, CLOCK, ALL_APPS, NOTIFICATION_ACCESS, HOME_APP,
}

/** Eight fader ends: slot = row * 4 + column * 2 + (0 up, 1 down). */
const val FADER_SLOTS = 8

/** Minute presets the faders turn into in clock mode, in slot order. */
val TIMER_PRESETS = listOf(1, 3, 5, 10, 15, 30, 45, 60)

/**
 * Everything the wheel, the faders and the bottom switches read and write.
 * Owned by the Activity so it survives recomposition and folding.
 */
class LauncherState(context: Context) {

    private val appContext = context.applicationContext
    private val repository = AppRepository(appContext)
    private val prefs = Prefs(appContext)
    val volume = Volume(appContext)

    val menuEntries: List<MenuEntry> = listOf(
        MenuEntry("Apps", MenuAction.APPS),
        MenuEntry("Recents", MenuAction.RECENTS, enabled = false),
        MenuEntry("Favorites", MenuAction.FAVORITES),
        MenuEntry("Cover Flow", MenuAction.COVER_FLOW, enabled = false),
        MenuEntry("Clock", MenuAction.CLOCK),
        MenuEntry("All Apps", MenuAction.ALL_APPS),
        MenuEntry("Notifications", MenuAction.NOTIFICATION_ACCESS),
        MenuEntry("Home App", MenuAction.HOME_APP),
    )

    var screen by mutableStateOf(Screen.HOME)
        private set
    var dark by mutableStateOf(prefs.dark)
        private set
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    /** Ring selection. Kept as a float so the ring glides instead of snapping. */
    var ringIndex by mutableFloatStateOf(0f)
        private set
    var menuIndex by mutableIntStateOf(0)
        private set
    var drawerIndex by mutableIntStateOf(0)
        private set
    var query by mutableStateOf("")
        private set

    /** 0..1, rises with how fast the wheel is being turned. Drives the ring and the wave. */
    var energy by mutableFloatStateOf(0f)
        private set

    var volumeVisible by mutableStateOf(false)
        private set
    var volumeLevel by mutableIntStateOf(0)
        private set
    var musicActive by mutableStateOf(false)
        private set

    var resumeTick by mutableIntStateOf(0)
        private set

    private var returnToHomeOnResume = false

    val selectedApp: AppEntry? get() = apps.getOrNull(ringIndex.toInt())

    val filteredApps: List<AppEntry>
        get() = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

    // ---- loading ---------------------------------------------------------

    suspend fun loadApps() {
        val loaded = withContext(Dispatchers.IO) {
            repository.refresh()
            repository.apps()
        }
        apps = loaded
        if (loaded.isNotEmpty()) {
            ringIndex = ringIndex.coerceIn(0f, loaded.lastIndex.toFloat())
        }
    }

    fun iconOf(entry: AppEntry) = repository.icon(entry)

    /** Decays wheel energy, and checks now and then whether audio is playing. */
    suspend fun runTicker() {
        var tick = 0
        while (true) {
            delay(80)
            if (energy > 0f) energy = (energy - 0.06f).coerceAtLeast(0f)
            if (tick % 12 == 0) {
                val playing = volume.musicActive
                if (playing != musicActive) musicActive = playing
            }
            tick++
        }
    }

    // ---- wheel -----------------------------------------------------------

    fun scroll(delta: Int) {
        energy = (energy + 0.30f).coerceAtMost(1f)
        when (screen) {
            Screen.HOME -> if (apps.isNotEmpty()) {
                ringIndex = (ringIndex + delta).coerceIn(0f, apps.lastIndex.toFloat())
            }
            Screen.MENU -> menuIndex = (menuIndex + delta).coerceIn(0, menuEntries.lastIndex)
            Screen.DRAWER -> {
                val last = filteredApps.lastIndex
                if (last >= 0) drawerIndex = (drawerIndex + delta).coerceIn(0, last)
            }
            Screen.CLOCK -> Unit
        }
    }

    fun volumeGestureStart() {
        volumeLevel = volume.level
        volumeVisible = true
    }

    fun volumeStep(delta: Int) {
        volumeLevel = volume.nudge(delta)
        volumeVisible = true
    }

    fun volumeGestureEnd() {
        volumeVisible = false
    }

    /** Centre button. */
    fun select() {
        when (screen) {
            Screen.HOME -> selectedApp?.let { repository.launch(it) }
            Screen.MENU -> menuEntries[menuIndex].let { if (it.enabled) dispatch(it.action) }
            Screen.DRAWER -> filteredApps.getOrNull(drawerIndex)?.let { repository.launch(it) }
            Screen.CLOCK -> Unit
        }
    }

    /**
     * Centre button held down. Everywhere this goes home; in clock mode it is
     * the shortcut to the device clock app, and coming back drops to home.
     */
    fun centerLongPress(context: Context): Boolean {
        if (screen == Screen.CLOCK) {
            if (!SystemApps.openClock(context)) return false
            returnToHomeOnResume = true
            return true
        }
        goHome()
        return true
    }

    /** The MENU label on the wheel, and the system back gesture. */
    fun back(): Boolean = when (screen) {
        Screen.DRAWER -> {
            goHome()
            true
        }
        Screen.CLOCK -> {
            screen = Screen.HOME
            true
        }
        Screen.MENU -> {
            screen = Screen.HOME
            true
        }
        Screen.HOME -> {
            screen = Screen.MENU
            true
        }
    }

    fun goHome() {
        screen = Screen.HOME
        query = ""
    }

    /** The system back gesture: like MENU, except it never leaves the home ring. */
    fun systemBack() {
        if (screen != Screen.HOME) back()
    }

    fun nudge(delta: Int) {
        repeat(3) { scroll(if (delta > 0) 1 else -1) }
    }

    // ---- bottom switches -------------------------------------------------

    fun toggleClock() {
        screen = if (screen == Screen.CLOCK) Screen.HOME else Screen.CLOCK
    }

    fun toggleDark() {
        dark = !dark
        prefs.dark = dark
    }

    /** The "Y" switch: opens the Y Assistant app. */
    fun openAssistant(): Boolean {
        val entry = repository.byLabel("Y Assistant", "Y アシスタント", "Assistant") ?: return false
        repository.launch(entry)
        return true
    }

    // ---- drawer ----------------------------------------------------------

    fun openDrawer() {
        screen = Screen.DRAWER
        query = ""
        drawerIndex = 0
    }

    fun closeDrawer() {
        if (screen == Screen.DRAWER) goHome()
    }

    fun setQuery(value: String) {
        query = value
        drawerIndex = 0
    }

    fun launch(entry: AppEntry) = repository.launch(entry)

    /** Hands a countdown of [minutes] to the device clock app. */
    fun startTimer(minutes: Int): Boolean = SystemApps.startTimer(appContext, minutes)

    // ---- faders ----------------------------------------------------------

    fun faderApp(slot: Int): AppEntry? = repository.byKey(prefs.faderSlot(slot))

    fun assignFader(slot: Int, entry: AppEntry?) {
        prefs.setFaderSlot(slot, entry?.key)
        faderRevision++
    }

    /** Bumped on assignment so the fader row recomposes. */
    var faderRevision by mutableIntStateOf(0)
        private set

    /** Fills any empty fader slot with a sensible default once apps are known. */
    fun seedFadersIfEmpty() {
        if (apps.isEmpty()) return
        if ((0 until FADER_SLOTS).any { prefs.faderSlot(it) != null }) return
        apps.take(FADER_SLOTS).forEachIndexed { slot, entry -> prefs.setFaderSlot(slot, entry.key) }
        faderRevision++
    }

    // ---- lifecycle -------------------------------------------------------

    fun onResumed() {
        if (returnToHomeOnResume) {
            returnToHomeOnResume = false
            goHome()
        }
        resumeTick++
    }

    private fun dispatch(action: MenuAction) {
        when (action) {
            MenuAction.APPS -> goHome()
            MenuAction.FAVORITES -> openDrawer()
            MenuAction.CLOCK -> screen = Screen.CLOCK
            MenuAction.ALL_APPS -> openDrawer()
            MenuAction.NOTIFICATION_ACCESS -> SystemApps.openNotificationAccess(appContext)
            MenuAction.HOME_APP -> SystemApps.openHomeSettings(appContext)
            MenuAction.RECENTS, MenuAction.COVER_FLOW -> Unit
        }
    }
}
