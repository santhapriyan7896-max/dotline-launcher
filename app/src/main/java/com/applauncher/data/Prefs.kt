package com.applauncher.data

import android.content.Context

/** Home screen tiles, in the order they can appear. [wide] tiles take a full row. */
enum class TileId(val title: String, val wide: Boolean) {
    CLOCK("Clock", true),
    DATE("Date", false),
    BATTERY("Battery", false),
    TOGGLES("Torch and Wi-Fi", true),
    MUSIC("Music", true),
    WEATHER("Weather", false),
    EVENT("Next event", true),
    PHOTO("Photo", false),
    TIMER("Timer", false);

    companion object {
        /** One page: calendar where music was, timer next to weather. Music and Photo can be switched on in Settings. */
        val DEFAULT = listOf(CLOCK, DATE, BATTERY, TOGGLES, EVENT, WEATHER, TIMER)
    }
}

/** Everything the user can change in Settings. */
data class LauncherSettings(
    val tiles: List<TileId> = TileId.DEFAULT,
    /** Icons per dock row: 4 or 5. */
    val dockColumns: Int = 4,
    /** null = follow the phone's setting. */
    val clock24h: Boolean? = null,
    val hiddenApps: Set<String> = emptySet(),
    val drawerLabels: Boolean = true,
    /** true = white symbols (Nothing style), false = the apps' normal colour icons. */
    val monoIcons: Boolean = true,
    /** Per app: "color" or "sym:<name>". Missing = automatic. */
    val iconOverrides: Map<String, String> = emptyMap(),
    val photoGrayscale: Boolean = false,
    /** Lock wallpaper design: DOTS, RING or TEXT, and the word used by TEXT. */
    val lockStyle: String = "DOTS",
    val lockText: String = "HELLO",
    /** Show the Dot lock screen over the phone's own lock screen (needs the lock service on). */
    val lockScreen: Boolean = false,
    /** Red dot on app icons that have notifications. */
    val notificationDots: Boolean = true,
    /** Dot-matrix battery animation when you plug in (on home and the lock screen). */
    val chargingAnimation: Boolean = true,
)

/** Small settings store for the launcher. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Null until the user (or first-run setup) has picked favourites. */
    var favorites: List<String>?
        get() = sp.getString(KEY_FAVORITES, null)?.split('\n')?.filter { it.isNotBlank() }
        set(value) = sp.edit().putString(KEY_FAVORITES, value?.joinToString("\n")).apply()

    /** True once the dock's old Phone app has been swapped for ours. */
    var dialerSwapped: Boolean
        get() = sp.getBoolean("dialer_swapped", false)
        set(value) = sp.edit().putBoolean("dialer_swapped", value).apply()

    /** Bumped when the home layout changes in an update, so saved layouts are updated once. */
    var layoutVersion: Int
        get() = sp.getInt("layout_version", 1)
        set(value) = sp.edit().putInt("layout_version", value).apply()

    var settings: LauncherSettings
        get() = LauncherSettings(
            tiles = sp.getString("tiles", null)
                ?.split(',')
                ?.mapNotNull { name -> TileId.entries.firstOrNull { it.name == name } }
                ?.let { saved ->
                    // Tiles added in an update appear at the end, switched on.
                    val known = sp.getString("tiles_known", null)?.split(',')?.toSet()
                        ?: TileId.entries.map { it.name }.toSet() - TileId.TIMER.name
                    saved + TileId.entries.filter { it.name !in known && it !in saved }
                }
                ?: TileId.DEFAULT,
            dockColumns = sp.getInt("dock_columns", 4),
            clock24h = if (sp.contains("clock_24h")) sp.getBoolean("clock_24h", false) else null,
            hiddenApps = sp.getStringSet("hidden_apps", emptySet())!!.toSet(),
            drawerLabels = sp.getBoolean("drawer_labels", true),
            monoIcons = sp.getBoolean("mono_icons", true),
            iconOverrides = sp.getString("icon_overrides", "")!!
                .split('\n')
                .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toMap(),
            photoGrayscale = sp.getBoolean("photo_grayscale", false),
            lockStyle = sp.getString("lock_style", "DOTS")!!,
            lockText = sp.getString("lock_text", "HELLO")!!,
            lockScreen = sp.getBoolean("lock_screen", false),
            notificationDots = sp.getBoolean("notification_dots", true),
            chargingAnimation = sp.getBoolean("charging_animation", true),
        )
        set(s) {
            sp.edit()
                .putString("tiles", s.tiles.joinToString(",") { it.name })
                .putString("tiles_known", TileId.entries.joinToString(",") { it.name })
                .putInt("dock_columns", s.dockColumns)
                .apply { if (s.clock24h == null) remove("clock_24h") else putBoolean("clock_24h", s.clock24h) }
                .putStringSet("hidden_apps", s.hiddenApps)
                .putBoolean("drawer_labels", s.drawerLabels)
                .putBoolean("mono_icons", s.monoIcons)
                .putString("icon_overrides", s.iconOverrides.entries.joinToString("\n") { "${it.key}=${it.value}" })
                .putBoolean("photo_grayscale", s.photoGrayscale)
                .putString("lock_style", s.lockStyle)
                .putString("lock_text", s.lockText)
                .putBoolean("lock_screen", s.lockScreen)
                .putBoolean("notification_dots", s.notificationDots)
                .putBoolean("charging_animation", s.chargingAnimation)
                .apply()
        }

    // --- Small caches for tiles ---

    var weatherJson: String?
        get() = sp.getString("weather_json", null)
        set(value) = sp.edit().putString("weather_json", value).apply()

    private companion object {
        const val KEY_FAVORITES = "favorites"
    }
}
