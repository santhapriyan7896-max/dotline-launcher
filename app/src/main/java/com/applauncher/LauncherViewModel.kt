package com.applauncher

import android.app.Application
import android.annotation.SuppressLint
import android.app.SearchManager
import com.applauncher.system.LockService
import android.content.ComponentName
import android.content.Intent
import android.bluetooth.BluetoothManager
import android.graphics.Rect
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.applauncher.system.MediaListenerService
import com.applauncher.system.MusicWatcher
import com.applauncher.system.Torch
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applauncher.data.AppEntry
import com.applauncher.data.AppRepository
import com.applauncher.data.LauncherSettings
import com.applauncher.data.Prefs
import com.applauncher.data.SettingsStore
import com.applauncher.system.CalendarEvent
import com.applauncher.system.NextEvent
import com.applauncher.system.Weather
import com.applauncher.system.WeatherNow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import com.applauncher.ui.IconDispatcher
import com.applauncher.ui.IconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private val repo = AppRepository(application)
    private val prefs = Prefs(application)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _favoriteKeys = MutableStateFlow(prefs.favorites ?: emptyList())
    val favoriteKeys: StateFlow<List<String>> = _favoriteKeys.asStateFlow()

    /** The dock's apps, looked up on their own at startup so the dock appears before the full list. */
    private val _quickFavorites = MutableStateFlow<List<AppEntry>>(emptyList())

    val favoriteApps: StateFlow<List<AppEntry>> = combine(_apps, _favoriteKeys, _quickFavorites) { apps, keys, quick ->
        val byKey = (if (apps.isEmpty()) quick else apps).associateBy { it.key }
        keys.mapNotNull { byKey[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Settings ---

    private val _settings = SettingsStore.flow(application)
    val settings: StateFlow<LauncherSettings> = _settings

    fun updateSettings(change: (LauncherSettings) -> LauncherSettings) = SettingsStore.update(app, change)

    /** Apps shown in the drawer and search: everything except hidden apps. */
    val visibleApps: StateFlow<List<AppEntry>> = combine(_apps, _settings) { apps, s ->
        if (s.hiddenApps.isEmpty()) apps else apps.filter { it.key !in s.hiddenApps }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setHidden(entry: AppEntry, hidden: Boolean) = updateSettings {
        it.copy(hiddenApps = if (hidden) it.hiddenApps + entry.key else it.hiddenApps - entry.key)
    }

    /** "color", "sym:<name>" or null for automatic. */
    fun setIconOverride(entry: AppEntry, value: String?) = updateSettings {
        it.copy(iconOverrides = if (value == null) it.iconOverrides - entry.key else it.iconOverrides + (entry.key to value))
    }

    // --- Weather, steps, next event and photo tiles ---

    private val _weather = MutableStateFlow(WeatherNow.fromJson(prefs.weatherJson))
    val weather: StateFlow<WeatherNow?> = _weather.asStateFlow()
    private var weatherJob: Job? = null

    /** Fetches weather at most every 30 minutes (or now, if [force]). */
    fun refreshWeather(force: Boolean = false) {
        if (!Weather.hasPermission(app) || weatherJob?.isActive == true) return
        val age = System.currentTimeMillis() - (_weather.value?.fetchedAt ?: 0L)
        if (!force && age < 30 * 60 * 1000L) return
        weatherJob = viewModelScope.launch(Dispatchers.IO) {
            Weather.fetch(app)?.let {
                _weather.value = it
                prefs.weatherJson = it.toJson()
            } ?: run { if (force) toastOnMain("Couldn't get the weather") }
        }
    }

    private val _nextEvent = MutableStateFlow<CalendarEvent?>(null)
    val nextEvent: StateFlow<CalendarEvent?> = _nextEvent.asStateFlow()

    fun refreshEvent() {
        viewModelScope.launch(Dispatchers.IO) { _nextEvent.value = NextEvent.load(app) }
    }

    val photoFile: File get() = File(app.filesDir, "photo_tile.jpg")
    private val _photoVersion = MutableStateFlow(if (photoFile.exists()) photoFile.lastModified() else 0L)
    /** Changes whenever a new photo is picked; 0 = no photo yet. */
    val photoVersion: StateFlow<Long> = _photoVersion.asStateFlow()

    /** Saves a smaller copy of the picked photo, so the tile keeps working even if the original moves. */
    fun setPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = app.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 900) sample *= 2
                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching
                photoFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                _photoVersion.value = System.currentTimeMillis()
            }.onFailure { toastOnMain("Couldn't use that photo") }
        }
    }

    private fun toastOnMain(message: String) {
        viewModelScope.launch(Dispatchers.Main) { toast(message) }
    }

    /** False until the app list has been read once. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _isDefaultHome = MutableStateFlow(true)
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _quickFavorites.value = repo.loadEntries(_favoriteKeys.value)
        }
        refresh()
        viewModelScope.launch {
            repo.changes().collect {
                repo.clearIconCache()
                refresh()
            }
        }
    }

    private fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        val apps = repo.loadApps()
        _apps.value = apps

        fun mainAppFor(pkg: String?) = apps.firstOrNull { it.packageName == pkg && !it.isWork }

        // First run: start the home screen with a few everyday apps.
        if (prefs.favorites == null) {
            val keys = repo.defaultFavoritePackages().mapNotNull { mainAppFor(it)?.key }
            prefs.favorites = keys
            _favoriteKeys.value = keys
        }
        swapDockPhoneOnce(apps)
        _loaded.value = true
        checkDefaultHome()
        preloadIcons(apps)
    }

    /** Once Dotline is the default phone app, put its Phone on the dock in place of the old one. */
    private fun swapDockPhoneOnce(apps: List<AppEntry>) {
        if (prefs.dialerSwapped || repo.dialerPackage() != app.packageName) return
        val ours = apps.firstOrNull { it.component.className == DIALER_ACTIVITY } ?: return
        val otherDialers = repo.dialHandlerPackages() - app.packageName
        val keys = _favoriteKeys.value
        val swapped = keys.map { key -> if (key.substringBefore('/') in otherDialers) ours.key else key }.distinct()
        if (swapped != keys) {
            _favoriteKeys.value = swapped
            prefs.favorites = swapped
        }
        prefs.dialerSwapped = true
    }

    private var preloadJob: Job? = null

    /** Builds every icon once in the background, so scrolling the drawer never has to. */
    private fun preloadIcons(apps: List<AppEntry>) {
        preloadJob?.cancel()
        val px = (IconSize.value * app.resources.displayMetrics.density).roundToInt()
        // Home screen apps first, they're the ones on screen.
        val favorites = _favoriteKeys.value.toSet()
        val ordered = apps.sortedByDescending { it.key in favorites }
        preloadJob = viewModelScope.launch(IconDispatcher) {
            for (entry in ordered) {
                ensureActive()
                repo.loadIcon(entry, px, mono = true)
            }
        }
    }

    fun checkDefaultHome() {
        _isDefaultHome.value = repo.isDefaultHome()
    }

    // --- Home screen tiles ---

    val torch = Torch(application)
    val music = MusicWatcher(application)

    private val wifi = application.getSystemService(WifiManager::class.java)
    private val _wifiOn = MutableStateFlow(false)
    val wifiOn: StateFlow<Boolean> = _wifiOn.asStateFlow()

    private val bluetooth = application.getSystemService(BluetoothManager::class.java)?.adapter
    private val _bluetoothOn = MutableStateFlow(false)
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn.asStateFlow()

    /** Home screen became visible: refresh everything that may have changed meanwhile. */
    fun onHomeVisible() {
        checkDefaultHome()
        refreshRadios()
        music.start()
        refreshWeather()
        refreshEvent()
    }

    fun refreshRadios() {
        _wifiOn.value = runCatching { wifi?.isWifiEnabled == true }.getOrDefault(false)
        _bluetoothOn.value = runCatching { bluetooth?.isEnabled == true }.getOrDefault(false)
    }

    /** Apps can't switch Bluetooth themselves any more, so open its settings. */
    fun openBluetoothSettings() = startIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openSettings() = startIntent(Intent(Settings.ACTION_SETTINGS))

    fun onHomeHidden() = music.stop()

    /** Apps can't switch Wi-Fi themselves any more, so open the system Wi-Fi panel. */
    fun openWifiPanel() = startIntent(
        if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI)
        else Intent(Settings.ACTION_WIFI_SETTINGS)
    )

    /** Opens the "Notification access" switch for this launcher. */
    fun openNotificationAccess() {
        val component = ComponentName(app, MediaListenerService::class.java).flattenToString()
        val direct = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        } else null
        val ok = direct != null && runCatching {
            app.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) startIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /**
     * Pulls down the notification panel. Android has no public way to do this,
     * so this uses the same hidden call other launchers use.
     */
    @SuppressLint("WrongConstant")
    fun openNotifications() {
        runCatching {
            val statusBar = app.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(statusBar)
        }.onFailure { toast("Couldn't open notifications") }
    }

    /** Double-tap to lock. The first time, sends you to switch on the lock service. */
    fun lockScreen() {
        if (LockService.lock()) return
        toast("Turn on “Dotline” here to use double-tap to lock")
        startIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onCleared() {
        torch.release()
        music.stop()
    }

    fun cachedIcon(app: AppEntry, sizePx: Int, mono: Boolean): ImageBitmap? =
        repo.cachedIcon(app, sizePx, mono)

    fun loadIcon(app: AppEntry, sizePx: Int, mono: Boolean): ImageBitmap? =
        repo.loadIcon(app, sizePx, mono)

    /** Call off the main thread. */
    fun hasOwnMonochrome(app: AppEntry): Boolean = repo.hasOwnMonochrome(app)

    private val smsPackage: String? by lazy {
        runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(app) }.getOrNull()
    }

    /**
     * Whether [entry] gets a notification dot, given the apps that currently have one.
     * The launcher's own Phone and Messages mirror missed calls and new texts.
     */
    fun hasDot(entry: AppEntry, badged: Set<String>): Boolean = when {
        entry.packageName != app.packageName -> entry.packageName in badged
        entry.component.className.endsWith(".MessagesActivity") -> smsPackage in badged
        entry.component.className.endsWith(".DialerActivity") ->
            "com.android.server.telecom" in badged || "com.android.dialer" in badged
        else -> false
    }

    fun launch(entry: AppEntry, sourceBounds: Rect? = null, options: Bundle? = null) {
        if (!repo.launch(entry, sourceBounds, options)) toast("Couldn't open ${entry.label}")
    }

    fun isFavorite(entry: AppEntry) = entry.key in _favoriteKeys.value

    fun toggleFavorite(entry: AppEntry) {
        val current = _favoriteKeys.value
        val next = when {
            entry.key in current -> current - entry.key
            current.size >= MAX_FAVORITES -> {
                toast("Home holds up to $MAX_FAVORITES apps")
                return
            }
            else -> current + entry.key
        }
        _favoriteKeys.value = next
        prefs.favorites = next
    }

    fun openAppInfo(entry: AppEntry) = repo.openAppInfo(entry)

    fun uninstall(entry: AppEntry) = startIntent(repo.uninstallIntent(entry))

    fun searchWeb(query: String) =
        startIntent(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))

    fun startIntent(intent: Intent) {
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast("No app can do that") }
    }

    private fun toast(message: String) =
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val MAX_FAVORITES = 8
        private const val DIALER_ACTIVITY = "com.applauncher.phone.DialerActivity"
    }
}
