package com.pocketlauncher.classic.data

import android.content.Context

/** Small, synchronous settings store. Everything here is cheap to read. */
class Prefs(context: Context) {

    private val store = context.getSharedPreferences("pocket_classic", Context.MODE_PRIVATE)

    var dark: Boolean
        get() = store.getBoolean(KEY_DARK, false)
        set(value) = store.edit().putBoolean(KEY_DARK, value).apply()

    /** [AppEntry.key] assigned to one of the eight fader ends, or null when unset. */
    fun faderSlot(slot: Int): String? = store.getString("$KEY_FADER$slot", null)

    fun setFaderSlot(slot: Int, key: String?) {
        store.edit().putString("$KEY_FADER$slot", key).apply()
    }

    private companion object {
        const val KEY_DARK = "dark"
        const val KEY_FADER = "fader_"
    }
}
