package com.applauncher.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class WeatherNow(
    val temp: Int,
    val code: Int,
    val high: Int,
    val low: Int,
    val fetchedAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put("temp", temp).put("code", code).put("high", high).put("low", low).put("at", fetchedAt)
        .toString()

    companion object {
        fun fromJson(text: String?): WeatherNow? = runCatching {
            val j = JSONObject(text!!)
            WeatherNow(j.getInt("temp"), j.getInt("code"), j.getInt("high"), j.getInt("low"), j.getLong("at"))
        }.getOrNull()
    }
}

/** Current weather from Open-Meteo (free, no account). Location is rounded to about 1 km first. */
object Weather {

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Short description for a WMO weather code. */
    fun describe(code: Int): String = when (code) {
        0 -> "CLEAR"
        1, 2 -> "PARTLY CLOUDY"
        3 -> "CLOUDY"
        45, 48 -> "FOG"
        in 51..57 -> "DRIZZLE"
        in 61..67 -> "RAIN"
        in 71..77 -> "SNOW"
        in 80..82 -> "SHOWERS"
        85, 86 -> "SNOW SHOWERS"
        in 95..99 -> "STORM"
        else -> "WEATHER"
    }

    /** Fetches fresh weather. Call off the main thread. Returns null on any failure. */
    suspend fun fetch(context: Context): WeatherNow? {
        val location = location(context) ?: return null
        return runCatching {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                        "&current=temperature_2m,weather_code" +
                        "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                    location.latitude, location.longitude,
                )
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            val text = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val json = JSONObject(text)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            WeatherNow(
                temp = current.getDouble("temperature_2m").roundToInt(),
                code = current.getInt("weather_code"),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                fetchedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    @SuppressLint("MissingPermission") // checked by hasPermission
    private suspend fun location(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val last = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val fresh = last != null && System.currentTimeMillis() - last.time < 6 * 60 * 60 * 1000L
        if (fresh || Build.VERSION.SDK_INT < 30) return last

        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return last
        }
        val current = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationSignal()
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { cont.resume(it) }
                cont.invokeOnCancellation { cancel.cancel() }
            }
        }
        return current ?: last
    }
}
