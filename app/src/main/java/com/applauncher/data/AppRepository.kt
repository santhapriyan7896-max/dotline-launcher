package com.applauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.os.Bundle
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One launchable app (an app can appear twice if it also lives in a work profile). */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val isWork: Boolean,
    /** Built into the phone and not uninstallable (updates don't count). */
    val isSystem: Boolean,
    /** Came with the phone, even if it has been updated since. */
    val isPreinstalled: Boolean = isSystem,
) {
    val packageName: String get() = component.packageName

    /** Stable id used to remember favourites. */
    val key: String get() = "${component.flattenToString()}#$userSerial"
}

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconCache = LruCache<String, ImageBitmap>(400)

    fun loadApps(): List<AppEntry> =
        userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { info -> toEntry(info, user, serial) }
        }
            .filter { it.component.className != HOME_ACTIVITY }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    /** Looks up a few apps by their saved key. Much faster than [loadApps], used at startup for the dock. */
    fun loadEntries(keys: List<String>): List<AppEntry> = keys.mapNotNull { key ->
        runCatching {
            val (flat, serialText) = key.split('#', limit = 2)
            val component = ComponentName.unflattenFromString(flat) ?: return@runCatching null
            val serial = serialText.toLong()
            val user = userManager.getUserForSerialNumber(serial) ?: return@runCatching null
            val info = launcherApps.resolveActivity(Intent(Intent.ACTION_MAIN).setComponent(component), user)
                ?: return@runCatching null
            toEntry(info, user, serial)
        }.getOrNull()
    }

    private fun toEntry(info: LauncherActivityInfo, user: UserHandle, serial: Long): AppEntry {
        val flags = info.applicationInfo.flags
        return AppEntry(
            label = info.label.toString().trim(),
            component = info.componentName,
            user = user,
            userSerial = serial,
            isWork = user != Process.myUserHandle(),
            isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
            isPreinstalled = flags and ApplicationInfo.FLAG_SYSTEM != 0,
        )
    }

    private val ownMono = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Does the app ship its own one-colour icon (Android 13+)? Call off the main thread. */
    fun hasOwnMonochrome(app: AppEntry): Boolean = ownMono.getOrPut(app.key) {
        if (Build.VERSION.SDK_INT < 33) return@getOrPut false
        val icon = resolve(app)?.let { runCatching { it.getIcon(0) }.getOrNull() }
        (icon as? AdaptiveIconDrawable)?.monochrome != null
    }

    /** Emits whenever an app is installed, removed or updated. */
    fun changes(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesAvailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    fun clearIconCache() {
        iconCache.evictAll()
        ownMono.clear()
    }

    fun cachedIcon(app: AppEntry, sizePx: Int, mono: Boolean): ImageBitmap? =
        iconCache.get(iconKey(app, sizePx, mono))

    /** Loads an app icon. Call off the main thread. */
    fun loadIcon(app: AppEntry, sizePx: Int, mono: Boolean): ImageBitmap? {
        val key = iconKey(app, sizePx, mono)
        iconCache.get(key)?.let { return it }
        val info = resolve(app) ?: return null
        val drawable = runCatching { info.getBadgedIcon(0) }.getOrNull() ?: return null
        val bitmap = if (mono) drawable.toMonoBitmap(sizePx) else drawable.toBitmap(sizePx)
        return bitmap.asImageBitmap().also { iconCache.put(key, it) }
    }

    /**
     * Opens an app. [sourceBounds] is where its icon is on screen and [options] carries the
     * opening animation, so the app grows out of the icon like with a built-in launcher.
     */
    fun launch(app: AppEntry, sourceBounds: Rect? = null, options: Bundle? = null): Boolean = runCatching {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, options)
    }.isSuccess

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstallIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))

    /** Apps that make a good starting home screen: phone, messages, camera, browser. */
    fun defaultFavoritePackages(): List<String> = listOfNotNull(
        dialerPackage(),
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
        cameraPackage(),
        resolvePackage(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
    ).distinct()

    /** Every app that can open a phone keypad. */
    fun dialHandlerPackages(): Set<String> =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0)
            .map { it.activityInfo.packageName }
            .toSet()

    fun dialerPackage(): String? =
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

    fun cameraPackage(): String? {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        resolvePackage(intent)?.let { return it }
        // Several camera apps and no default chosen: prefer the phone's built-in one.
        val candidates = context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo }
        return (candidates.firstOrNull { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            ?: candidates.firstOrNull())?.packageName
    }

    fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }

    /** The app the user picked as default for [intent], or null if Android would ask every time. */
    private fun resolvePackage(intent: Intent): String? {
        val pm = context.packageManager
        val chosen = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        // When there's no default, Android returns its "choose an app" screen instead,
        // which isn't one of the real handlers.
        val handlers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        return chosen?.takeIf { it in handlers }
    }

    private fun resolve(app: AppEntry): LauncherActivityInfo? = runCatching {
        launcherApps.resolveActivity(Intent(Intent.ACTION_MAIN).setComponent(app.component), app.user)
    }.getOrNull()

    private fun iconKey(app: AppEntry, sizePx: Int, mono: Boolean) = "${app.key}|$sizePx|$mono"

    private companion object {
        /** The launcher's own home screen. Hidden from the app list; its Phone app is not. */
        const val HOME_ACTIVITY = "com.applauncher.MainActivity"
    }
}

private fun Drawable.toBitmap(size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val d = mutate()
    d.setBounds(0, 0, size, size)
    d.draw(Canvas(bitmap))
    return bitmap
}

/**
 * White, single-colour version of an icon, in this order of preference:
 * 1. the app's own monochrome icon (Android 13+),
 * 2. the symbol on the icon cut out from its solid background, in white,
 * 3. a greyscale copy of the icon (busy icons like photos or logos on gradients).
 */
private fun Drawable.toMonoBitmap(size: Int): Bitmap {
    val adaptive = this as? AdaptiveIconDrawable
    val mono = if (Build.VERSION.SDK_INT >= 33) adaptive?.monochrome else null
    if (mono != null) {
        // Adaptive icon layers are 108dp with the visible part in the middle 72dp,
        // so draw 25% larger on every side to fill the bitmap with the visible part.
        val pad = size / 4
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val m = mono.mutate()
        m.setBounds(-pad, -pad, size + pad, size + pad)
        m.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        m.draw(Canvas(bitmap))
        return bitmap
    }

    val full = toBitmap(size)
    extractGlyph(full)?.let { return it }

    val grey = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    Canvas(grey).drawBitmap(full, 0f, 0f, paint)
    return grey
}

/**
 * For flat icons (a symbol on a solid-colour shape), returns the symbol alone in white.
 * Returns null when the icon has no clear solid background or the result looks wrong.
 */
private fun extractGlyph(src: Bitmap): Bitmap? {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)

    // Sample the background just inside the top, left, right and bottom edges of the shape.
    val samples = listOf(w / 2 to h / 8, w / 8 to h / 2, w * 7 / 8 to h / 2, w / 2 to h * 7 / 8)
        .map { (x, y) -> px[y * w + x] }
    if (samples.any { Color.alpha(it) < 230 }) return null
    val bg = samples[0]
    if (samples.any { colorDistance(it, bg) > 40 }) return null

    var shapePixels = 0
    var glyphPixels = 0
    val out = IntArray(w * h)
    for (i in px.indices) {
        val c = px[i]
        val a = Color.alpha(c)
        if (a < 128) continue
        shapePixels++
        val d = colorDistance(c, bg)
        if (d > 50) {
            glyphPixels++
            // Gentle ramp so darker and lighter parts of the symbol keep some detail.
            val alpha = ((d - 30) * 2).coerceAtMost(255) * a / 255
            out[i] = Color.argb(alpha, 255, 255, 255)
        }
    }
    if (shapePixels == 0) return null
    val coverage = glyphPixels.toFloat() / shapePixels
    if (coverage < 0.03f || coverage > 0.7f) return null

    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

private fun colorDistance(a: Int, b: Int): Int {
    val dr = Color.red(a) - Color.red(b)
    val dg = Color.green(a) - Color.green(b)
    val db = Color.blue(a) - Color.blue(b)
    return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toInt()
}
