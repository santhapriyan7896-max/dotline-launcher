package com.applauncher.system

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.WindowManager
import com.applauncher.ui.mono.DotFont
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Nothing-style lock screen wallpapers, drawn as dots on black. */
object LockWallpaper {

    enum class Style(val label: String) { DOTS("DOTS"), RING("RING"), TEXT("TEXT") }

    private const val GRID_COLUMNS = 36
    private val UNLIT = Color.rgb(0x2C, 0x2C, 0x2C)
    private val LIT = Color.rgb(0xEC, 0xEC, 0xEC)
    private val RED = Color.rgb(0xE5, 0x26, 0x2B)

    /** Draws the wallpaper at [width] x [height] pixels. Use a small size for previews. */
    fun render(width: Int, height: Int, style: Style, text: String, withRedDot: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Text may need a finer grid so long words still fit across the screen.
        val words = text.trim().uppercase().take(12)
        val textColumns = words.sumOf { DotFont.glyph(it)[0].length + 1 } - 1
        val columns = if (style == Style.TEXT) maxOf(GRID_COLUMNS, textColumns + 6) else GRID_COLUMNS
        val pitch = width.toFloat() / columns
        val rows = (height / pitch).toInt() + 1
        val offsetX = pitch / 2f

        // Which grid dots are lit, and which one is red.
        val lit = HashSet<Pair<Int, Int>>()
        var red: Pair<Int, Int>? = null

        when (style) {
            Style.DOTS -> {
                red = columns / 2 to (rows * 0.80f).toInt()
            }
            Style.RING -> {
                val cx = columns / 2f
                val cy = rows * 0.60f
                val radius = columns * 0.32f
                for (y in 0 until rows) for (x in 0 until columns) {
                    val d = hypot(x - cx + 0.5f, y - cy)
                    if (d in (radius - 0.6f)..(radius + 0.6f)) lit += x to y
                }
                val angle = Math.toRadians(-45.0)
                red = (cx + radius * cos(angle)).toInt() to (cy + radius * sin(angle)).toInt()
            }
            Style.TEXT -> {
                val startX = (columns - textColumns) / 2
                val startY = (rows * 0.66f).toInt()
                var col = startX
                words.forEach { c ->
                    val glyph = DotFont.glyph(c)
                    for (y in 0 until DotFont.ROWS) for (x in glyph[0].indices) {
                        if (glyph[y][x] == '1') lit += (col + x) to (startY + y)
                    }
                    col += glyph[0].length + 1
                }
                red = (columns / 2) to (startY + DotFont.ROWS + 3)
            }
        }

        if (!withRedDot) red = null
        for (y in 0 until rows) for (x in 0 until columns) {
            val cell = x to y
            val (color, size) = when {
                cell == red -> RED to 0.48f
                cell in lit -> LIT to 0.30f
                else -> UNLIT to 0.14f
            }
            paint.color = color
            canvas.drawCircle(offsetX + x * pitch, pitch / 2f + y * pitch, pitch * size, paint)
        }
        return bitmap
    }

    /** The full screen size in pixels. */
    fun screenSize(context: Context): Pair<Int, Int> {
        val windows = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windows.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") windows.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    enum class Result { SET, BLOCKED, FAILED }

    /**
     * Sets the lock screen wallpaper only (the home screen is unchanged). Call off the main thread.
     * Returns BLOCKED when Android accepted it but the phone (e.g. a theme app) kept its own lock wallpaper.
     */
    fun apply(context: Context, style: Style, text: String): Result = runCatching {
        val (w, h) = screenSize(context)
        val bitmap = render(w, h, style, text)
        val manager = WallpaperManager.getInstance(context)
        val newId = manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        if (newId > 0 && manager.getWallpaperId(WallpaperManager.FLAG_LOCK) == newId) Result.SET else Result.BLOCKED
    }.getOrDefault(Result.FAILED)

    private var cached: Triple<String, Int, Bitmap>? = null

    /**
     * Full-screen background for the Dot lock screen, drawn once and reused.
     * Leaves out the red dot: the lock screen has its own, and this one would sit behind its text.
     */
    @Synchronized
    fun background(context: Context, style: Style, text: String): Bitmap {
        val (w, h) = screenSize(context)
        val key = "${style.name}|$text"
        cached?.let { (k, width, bmp) -> if (k == key && width == w) return bmp }
        return render(w, h, style, text, withRedDot = false).also { cached = Triple(key, w, it) }
    }
}
