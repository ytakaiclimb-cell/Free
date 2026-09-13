package com.mpc.launcher.data

import android.content.Context

/**
 * Layouts, kept separately for the folded and unfolded screens so each keeps
 * its own arrangement, as the spec asks.
 */
class LayoutStore(context: Context) {

    private val store = context.getSharedPreferences("mpc", Context.MODE_PRIVATE)

    fun load(form: FormFactor): Layout? = layoutFromJson(store.getString(form.key, null) ?: return null)

    fun save(form: FormFactor, layout: Layout) {
        store.edit().putString(form.key, layout.toJson()).apply()
    }

    fun clear(form: FormFactor) {
        store.edit().remove(form.key).apply()
    }
}

enum class FormFactor(val key: String) {
    /** The cover screen, or any narrow window. */
    FOLDED("layout_folded"),

    /** The inner screen. */
    UNFOLDED("layout_unfolded"),
}

/**
 * The layout a fresh install starts with: the M.P.C page from the design, plus
 * a second page for everything else.
 */
fun defaultLayout(repository: AppRepository): Layout {
    val apps = repository.apps()
    var spare = 0
    fun next(): String? = apps.getOrNull(spare++)?.key

    fun pick(vararg packages: String): String? =
        repository.byPackage(*packages)?.key ?: next()

    val spotify = repository.byPackage("com.spotify.music")?.key
    val camera = repository.cameraApp()?.key

    val mpc = Page(
        title = "M.P.C",
        modules = listOf(
            Module("media", ModuleType.MEDIA, col = 1, row = 0, spanX = 4, spanY = 1),
            Module("calendar", ModuleType.CALENDAR, col = 1, row = 2, spanX = 3, spanY = 3),
            Module("clock", ModuleType.CLOCK, col = 0, row = 6, spanX = 5, spanY = 1),
            Module(
                id = "fader-launch",
                type = ModuleType.FADER,
                col = 0, row = 7, spanX = 1, spanY = 2,
                faderRole = FaderRole.LAUNCH,
                flickUp = spotify,
                flickDown = camera,
            ),
            Module("pad-1", ModuleType.PAD, 1, 7, primary = pick("com.anthropic.claude")),
            Module(
                id = "pad-2", type = ModuleType.PAD, col = 2, row = 7,
                primary = pick("com.google.android.youtube"),
                secondary = repository.byPackage("com.google.android.apps.youtube.music")?.key,
            ),
            Module(
                id = "pad-3", type = ModuleType.PAD, col = 3, row = 7,
                primary = pick("com.instagram.android"),
                secondary = repository.byPackage("com.twitter.android", "com.x.android")?.key,
            ),
            Module(
                id = "knob-media", type = ModuleType.KNOB, col = 4, row = 7,
                folderName = "Media",
                folderApps = listOfNotNull(spotify, repository.byPackage("com.google.android.youtube")?.key),
            ),
            Module("pad-4", ModuleType.PAD, 1, 8, primary = pick("com.google.android.gm")),
            Module("pad-5", ModuleType.PAD, 2, 8, primary = pick("com.android.chrome")),
            Module("pad-6", ModuleType.PAD, 3, 8, primary = pick("jp.naver.line.android")),
            Module(
                id = "knob-work", type = ModuleType.KNOB, col = 4, row = 8,
                folderName = "Work",
                folderApps = listOfNotNull(next(), next(), next()),
            ),
        ),
    )

    val backyard = Page(
        title = "BACKYARD",
        subtitle = "uragawa",
        modules = listOf(
            Module("fader-brt", ModuleType.FADER, 0, 7, 1, 2, faderRole = FaderRole.BRIGHTNESS),
            Module("fader-vol", ModuleType.FADER, 1, 7, 1, 2, faderRole = FaderRole.VOLUME),
            Module("pad-b1", ModuleType.PAD, 2, 7, primary = next()),
            Module("pad-b2", ModuleType.PAD, 3, 7, primary = next()),
            Module("pad-b3", ModuleType.PAD, 4, 7, primary = next()),
            Module("pad-b4", ModuleType.PAD, 2, 8, primary = next()),
            Module("pad-b5", ModuleType.PAD, 3, 8, primary = next()),
            Module("pad-b6", ModuleType.PAD, 4, 8, primary = next()),
        ),
    )

    return Layout(listOf(mpc, backyard))
}
