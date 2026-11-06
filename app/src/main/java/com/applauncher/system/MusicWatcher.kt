package com.applauncher.system

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val appLabel: String,
    val playing: Boolean,
)

/** Follows whatever music or video app is playing, and controls it. */
class MusicWatcher(private val context: Context) {

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var started = false

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> choose(list) }

    /** Call when the home screen becomes visible. */
    fun start() {
        _hasAccess.value = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        if (!_hasAccess.value) {
            _nowPlaying.value = null
            return
        }
        if (!started) {
            runCatching { sessions.addOnActiveSessionsChangedListener(sessionsListener, listener, handler) }
            started = true
        }
        refresh()
    }

    /** Call when the home screen is hidden. */
    fun stop() {
        if (started) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsListener) }
        started = false
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    /** Opens the app that is playing. */
    fun openApp() {
        val c = controller ?: return
        runCatching { c.sessionActivity?.send() }.onFailure {
            context.packageManager.getLaunchIntentForPackage(c.packageName)
                ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { runCatching { context.startActivity(it) } }
        }
    }

    private fun refresh() {
        val list = runCatching { sessions.getActiveSessions(listener) }.getOrNull()
        choose(list)
    }

    private fun choose(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || title.isNullOrBlank()) {
            _nowPlaying.value = null
            return
        }
        _nowPlaying.value = NowPlaying(
            title = title,
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: "",
            appLabel = appLabel(c.packageName),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    @Suppress("DEPRECATION") // the newer overload needs Android 13
    private fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
