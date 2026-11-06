package com.applauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One shared copy of the settings, so a change in Settings shows up on the home screen at once. */
object SettingsStore {
    private var prefs: Prefs? = null
    private var state: MutableStateFlow<LauncherSettings>? = null

    @Synchronized
    private fun init(context: Context): MutableStateFlow<LauncherSettings> {
        state?.let { return it }
        val p = Prefs(context.applicationContext)
        prefs = p
        var loaded = p.settings
        // Layout 2: Photo and Music off the home screen, calendar and timer in their places.
        if (p.layoutVersion < 2) {
            loaded = loaded.copy(tiles = TileId.DEFAULT)
            p.settings = loaded
            p.layoutVersion = 2
        }
        return MutableStateFlow(loaded).also { state = it }
    }

    fun flow(context: Context): StateFlow<LauncherSettings> = init(context)

    @Synchronized
    fun update(context: Context, change: (LauncherSettings) -> LauncherSettings) {
        val s = init(context)
        val next = change(s.value)
        s.value = next
        prefs?.settings = next
    }
}
