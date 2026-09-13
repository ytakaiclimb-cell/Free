package com.mpc.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/** The grid is this many columns wide and this many rows tall on every page. */
const val GRID_COLUMNS = 5
const val GRID_ROWS = 9

enum class ModuleType { PAD, KNOB, FADER, CALENDAR, CLOCK, MEDIA }

/** What a fader is wired to. */
enum class FaderRole {
    /** Slide for media volume, tap to mute. */
    VOLUME,

    /** Slide for screen brightness, tap for auto. */
    BRIGHTNESS,

    /** Flick up or down to launch an app. */
    LAUNCH,
}

/**
 * One thing on the grid. Every module carries every field; which ones matter
 * depends on [type], which keeps persistence and the editor simple.
 */
data class Module(
    val id: String,
    val type: ModuleType,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    /** PAD: the app a tap opens. */
    val primary: String? = null,
    /** PAD: the app a downward slide opens. */
    val secondary: String? = null,
    /** KNOB: folder name and contents. */
    val folderName: String = "Folder",
    val folderApps: List<String> = emptyList(),
    /** FADER. */
    val faderRole: FaderRole = FaderRole.VOLUME,
    val flickUp: String? = null,
    val flickDown: String? = null,
) {
    val endCol: Int get() = col + spanX
    val endRow: Int get() = row + spanY

    fun overlaps(other: Module): Boolean =
        col < other.endCol && other.col < endCol && row < other.endRow && other.row < endRow
}

data class Page(
    val title: String,
    val subtitle: String = "",
    val modules: List<Module> = emptyList(),
)

data class Layout(val pages: List<Page>)

// ---- JSON ---------------------------------------------------------------

fun Layout.toJson(): String {
    val pagesJson = JSONArray()
    pages.forEach { page ->
        val modulesJson = JSONArray()
        page.modules.forEach { module ->
            modulesJson.put(
                JSONObject()
                    .put("id", module.id)
                    .put("type", module.type.name)
                    .put("col", module.col)
                    .put("row", module.row)
                    .put("spanX", module.spanX)
                    .put("spanY", module.spanY)
                    .put("primary", module.primary ?: JSONObject.NULL)
                    .put("secondary", module.secondary ?: JSONObject.NULL)
                    .put("folderName", module.folderName)
                    .put("folderApps", JSONArray(module.folderApps))
                    .put("faderRole", module.faderRole.name)
                    .put("flickUp", module.flickUp ?: JSONObject.NULL)
                    .put("flickDown", module.flickDown ?: JSONObject.NULL),
            )
        }
        pagesJson.put(
            JSONObject()
                .put("title", page.title)
                .put("subtitle", page.subtitle)
                .put("modules", modulesJson),
        )
    }
    return JSONObject().put("pages", pagesJson).toString()
}

fun layoutFromJson(raw: String): Layout? = runCatching {
    val root = JSONObject(raw)
    val pagesJson = root.getJSONArray("pages")
    val pages = (0 until pagesJson.length()).map { pageIndex ->
        val pageJson = pagesJson.getJSONObject(pageIndex)
        val modulesJson = pageJson.getJSONArray("modules")
        val modules = (0 until modulesJson.length()).map { moduleIndex ->
            val json = modulesJson.getJSONObject(moduleIndex)
            val folderJson = json.optJSONArray("folderApps") ?: JSONArray()
            Module(
                id = json.getString("id"),
                type = ModuleType.valueOf(json.getString("type")),
                col = json.getInt("col"),
                row = json.getInt("row"),
                spanX = json.optInt("spanX", 1),
                spanY = json.optInt("spanY", 1),
                primary = json.optStringOrNull("primary"),
                secondary = json.optStringOrNull("secondary"),
                folderName = json.optString("folderName", "Folder"),
                folderApps = (0 until folderJson.length()).map { folderJson.getString(it) },
                faderRole = FaderRole.valueOf(json.optString("faderRole", FaderRole.VOLUME.name)),
                flickUp = json.optStringOrNull("flickUp"),
                flickDown = json.optStringOrNull("flickDown"),
            )
        }
        Page(
            title = pageJson.optString("title", ""),
            subtitle = pageJson.optString("subtitle", ""),
            modules = modules,
        )
    }
    Layout(pages)
}.getOrNull()

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
