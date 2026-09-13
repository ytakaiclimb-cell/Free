package com.mpc.launcher.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpc.launcher.data.AppEntry
import com.mpc.launcher.data.AppRepository
import com.mpc.launcher.data.FormFactor
import com.mpc.launcher.data.GRID_COLUMNS
import com.mpc.launcher.data.GRID_ROWS
import com.mpc.launcher.data.Layout
import com.mpc.launcher.data.LayoutStore
import com.mpc.launcher.data.Module
import com.mpc.launcher.data.Page
import com.mpc.launcher.data.defaultLayout
import com.mpc.launcher.service.NowPlaying
import com.mpc.launcher.system.Brightness
import com.mpc.launcher.system.Media
import com.mpc.launcher.system.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the app picker sheet is currently choosing for. */
sealed interface PickerTarget {
    data class PadPrimary(val moduleId: String) : PickerTarget
    data class PadSecondary(val moduleId: String) : PickerTarget
    data class FolderAdd(val moduleId: String) : PickerTarget
    data class FlickUp(val moduleId: String) : PickerTarget
    data class FlickDown(val moduleId: String) : PickerTarget
}

/**
 * Everything the home screen reads and writes: the layout, which page is up,
 * whether the editor is open, and the live system readings the modules show.
 */
class LauncherState(context: Context) {

    private val appContext = context.applicationContext
    private val repository = AppRepository(appContext)
    private val store = LayoutStore(appContext)

    val volume = Volume(appContext)
    val brightness = Brightness(appContext)
    private val media = Media(appContext)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var layout by mutableStateOf(Layout(emptyList()))
        private set
    var form by mutableStateOf(FormFactor.UNFOLDED)
        private set

    var editing by mutableStateOf(false)
        private set
    var drawerOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set

    /** The knob whose folder is open, if any. */
    var openFolder by mutableStateOf<String?>(null)
        private set
    var picker by mutableStateOf<PickerTarget?>(null)
        private set

    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    var resumeTick by mutableIntStateOf(0)
        private set

    val pages: List<Page> get() = layout.pages

    val filteredApps: List<AppEntry>
        get() = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

    fun entry(key: String?): AppEntry? = repository.byKey(key)

    fun icon(entry: AppEntry) = repository.icon(entry)

    fun launch(key: String?) = repository.launch(key)

    fun launch(entry: AppEntry) = repository.launch(entry)

    fun moduleById(id: String?): Module? =
        pages.asSequence().flatMap { it.modules }.firstOrNull { it.id == id }

    // ---- loading ---------------------------------------------------------

    suspend fun load(form: FormFactor) {
        this.form = form
        val loaded = withContext(Dispatchers.IO) {
            repository.refresh()
            val installed = repository.apps()
            val stored = store.load(form) ?: defaultLayout(repository).also { store.save(form, it) }
            installed to stored
        }
        apps = loaded.first
        layout = loaded.second
    }

    /** Polls the media session; sessions have no cheap change callback here. */
    suspend fun runTicker() {
        while (true) {
            val playing = media.nowPlaying()
            if (playing != nowPlaying) nowPlaying = playing
            delay(1_000)
        }
    }

    fun toggleMedia(): Boolean = media.togglePlayPause()

    // ---- layout editing --------------------------------------------------

    private fun commit(next: Layout) {
        layout = next
        store.save(form, next)
    }

    private fun mapModule(moduleId: String, transform: (Module) -> Module) {
        commit(
            Layout(
                pages.map { page ->
                    if (page.modules.none { it.id == moduleId }) {
                        page
                    } else {
                        page.copy(modules = page.modules.map { if (it.id == moduleId) transform(it) else it })
                    }
                },
            ),
        )
    }

    fun startEditing() {
        editing = true
    }

    fun stopEditing() {
        editing = false
    }

    /**
     * Drops [moduleId] at a grid cell, clamped inside the grid and refused when
     * it would land on top of another module.
     */
    fun moveModule(pageIndex: Int, moduleId: String, col: Int, row: Int) {
        val page = pages.getOrNull(pageIndex) ?: return
        val module = page.modules.firstOrNull { it.id == moduleId } ?: return
        val clampedCol = col.coerceIn(0, GRID_COLUMNS - module.spanX)
        val clampedRow = row.coerceIn(0, GRID_ROWS - module.spanY)
        if (clampedCol == module.col && clampedRow == module.row) return

        val moved = module.copy(col = clampedCol, row = clampedRow)
        if (page.modules.any { it.id != moduleId && it.overlaps(moved) }) return

        commit(
            Layout(
                pages.mapIndexed { index, candidate ->
                    if (index != pageIndex) {
                        candidate
                    } else {
                        candidate.copy(modules = candidate.modules.map { if (it.id == moduleId) moved else it })
                    }
                },
            ),
        )
    }

    fun renamePage(pageIndex: Int, title: String) {
        commit(
            Layout(
                pages.mapIndexed { index, page ->
                    if (index == pageIndex) page.copy(title = title) else page
                },
            ),
        )
    }

    fun addPage() {
        commit(Layout(pages + Page(title = "PAGE ${pages.size + 1}")))
    }

    fun removeModule(moduleId: String) {
        commit(Layout(pages.map { page -> page.copy(modules = page.modules.filterNot { it.id == moduleId }) }))
    }

    fun resetLayout() {
        val fresh = defaultLayout(repository)
        commit(fresh)
    }

    // ---- folders and the picker -----------------------------------------

    fun openFolder(moduleId: String) {
        openFolder = moduleId
    }

    fun closeFolder() {
        openFolder = null
    }

    fun openPicker(target: PickerTarget) {
        picker = target
        query = ""
    }

    fun closePicker() {
        picker = null
        query = ""
    }

    fun pick(entry: AppEntry) {
        when (val target = picker) {
            is PickerTarget.PadPrimary -> mapModule(target.moduleId) { it.copy(primary = entry.key) }
            is PickerTarget.PadSecondary -> mapModule(target.moduleId) { it.copy(secondary = entry.key) }
            is PickerTarget.FolderAdd -> mapModule(target.moduleId) {
                if (entry.key in it.folderApps) it else it.copy(folderApps = it.folderApps + entry.key)
            }
            is PickerTarget.FlickUp -> mapModule(target.moduleId) { it.copy(flickUp = entry.key) }
            is PickerTarget.FlickDown -> mapModule(target.moduleId) { it.copy(flickDown = entry.key) }
            null -> Unit
        }
        closePicker()
    }

    fun removeFromFolder(moduleId: String, key: String) {
        mapModule(moduleId) { it.copy(folderApps = it.folderApps - key) }
    }

    // ---- drawer ----------------------------------------------------------

    fun openDrawer() {
        drawerOpen = true
        query = ""
    }

    fun closeDrawer() {
        drawerOpen = false
        query = ""
    }

    fun updateQuery(value: String) {
        query = value
    }

    /** Back and Home both collapse whatever is on top, innermost first. */
    fun dismissTop(): Boolean = when {
        picker != null -> { closePicker(); true }
        openFolder != null -> { closeFolder(); true }
        drawerOpen -> { closeDrawer(); true }
        editing -> { stopEditing(); true }
        else -> false
    }

    // ---- lifecycle -------------------------------------------------------

    fun onResumed() {
        resumeTick++
    }
}
